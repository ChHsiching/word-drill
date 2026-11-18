#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Crawl Youdao's web JSON API (dict.youdao.com/jsonapi) for the word inventory of
the bundled dictionary asset, as the data source of the self-built dictionary
(ADR 0001).

State directory (gitignored, OUTSIDE git): <repo>/.scratch/youdao-crawl/
  - raw.jsonl        one {"word", "input", "ec"} JSON object per line; responses
                     are trimmed to the `ec` subtree at crawl time (~0.5 KB vs
                     ~100 KB full), keeping a 100k-word cache around 50 MB and
                     small enough to carry across CI runs as an artifact.
  - checkpoint.jsonl one crawled word per line; the resume marker.

Resume contract: a word is done iff it appears in checkpoint.jsonl. A hard kill
between the raw.jsonl append and the checkpoint append leaves a duplicate raw
line; load_raw() and the build step treat the last line as authoritative.

Rate limit is 1 request/second (spec-locked). Crawl seeds: the bundled
dictionary asset's word inventory, plus optional --extra-words (one word per
line; official-syllabus additions once available).

Usage:
  python tools/youdao_crawler.py [--limit N] [--max-seconds S]
      [--seed path/to/dictionary.json] [--extra-words path/to/words.txt]
      [--state-dir path]

--max-seconds stops the crawler cleanly (for CI chunks that must finish before
the 6-hour job cap); --limit caps the number of fetches this run.
"""

import argparse
import json
import os
import sys
import time
import urllib.parse
import urllib.request

REPO_ROOT = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
DEFAULT_SEED = os.path.join(REPO_ROOT, "app", "src", "main", "assets", "dictionary.json")
DEFAULT_STATE_DIR = os.path.join(REPO_ROOT, ".scratch", "youdao-crawl")

API_URL = "https://dict.youdao.com/jsonapi"
REQUEST_INTERVAL = 1.0  # seconds between request starts; spec-locked at 1 req/s
USER_AGENT = (
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/126.0 Safari/537.36"
)


def load_seed_words(dictionary_json_path, extra_words_path=None):
    """Unique seed words, sorted. The asset repeats each word per POS."""
    with open(dictionary_json_path, encoding="utf-8") as f:
        data = json.load(f)
    words = {e["word"] for e in data["words"] if e.get("word")}
    if extra_words_path:
        with open(extra_words_path, encoding="utf-8") as f:
            words.update(line.strip() for line in f if line.strip())
    return sorted(words)


def append_checkpoint(path, word):
    with open(path, "a", encoding="utf-8") as f:
        f.write(word + "\n")


def load_checkpoint(path):
    """Words whose checkpoint line is newline-terminated; a partial trailing
    line (crash between write and flush) is dropped."""
    if not os.path.exists(path):
        return set()
    with open(path, encoding="utf-8") as f:
        content = f.read()
    lines = content.split("\n")
    complete = lines[:-1] if lines and lines[-1] != "" else lines
    return {w for w in complete if w}


def append_raw(path, word, trimmed):
    with open(path, "a", encoding="utf-8") as f:
        f.write(json.dumps({"word": word, "input": trimmed["input"], "ec": trimmed["ec"]},
                           ensure_ascii=False, separators=(",", ":")) + "\n")


def load_raw(path):
    """word -> trimmed response; later lines win; unparsable trailing line
    (hard kill mid-write) is skipped."""
    loaded = {}
    if not os.path.exists(path):
        return loaded
    with open(path, encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if not line:
                continue
            try:
                rec = json.loads(line)
            except json.JSONDecodeError:
                continue
            loaded[rec["word"]] = {"input": rec.get("input"), "ec": rec.get("ec")}
    return loaded


def pending_words(seed, completed):
    return [w for w in seed if w not in completed]


def trim_response(raw):
    """Keep only the `ec` subtree (and the input echo); drop sentences,
    collins, webster, etc. — 200x smaller, still re-parseable without a
    re-fetch."""
    return {"input": raw.get("input"), "ec": raw.get("ec")}


def fetch_word(word):
    """Fetch one word from Youdao. Raises on HTTP/transport errors."""
    url = API_URL + "?le=eng&q=" + urllib.parse.quote(word)
    req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(req, timeout=30) as r:
        return json.load(r)


def run(args):
    seed = load_seed_words(args.seed, args.extra_words)
    os.makedirs(args.state_dir, exist_ok=True)
    checkpoint_path = os.path.join(args.state_dir, "checkpoint.jsonl")
    raw_path = os.path.join(args.state_dir, "raw.jsonl")

    done = load_checkpoint(checkpoint_path)
    queue = pending_words(seed, done)
    print(f"seed: {len(seed)} words, already crawled: {len(done)}, to fetch: {len(queue)}")
    if args.limit is not None:
        queue = queue[: args.limit]
        print(f"limited to {len(queue)} fetches this run")

    fetched = 0
    errors = 0
    deadline = time.monotonic() + args.max_seconds if args.max_seconds else None
    next_start = time.monotonic()
    for word in queue:
        if deadline is not None and time.monotonic() >= deadline:
            print(f"max-seconds budget reached; stopping cleanly")
            break
        delay = next_start - time.monotonic()
        if delay > 0:
            time.sleep(delay)
        next_start = max(next_start, time.monotonic()) + REQUEST_INTERVAL
        try:
            raw = fetch_word(word)
        except Exception as exc:  # noqa: BLE001 — one bad word must not kill the run
            errors += 1
            print(f"ERROR fetching {word!r}: {exc}", file=sys.stderr)
            time.sleep(REQUEST_INTERVAL)
            continue
        append_raw(raw_path, word, trim_response(raw))
        append_checkpoint(checkpoint_path, word)
        fetched += 1
        if fetched % 100 == 0:
            print(f"progress: {fetched} fetched, {len(queue) - fetched} left this run")

    total_done = len(load_checkpoint(checkpoint_path))
    print(
        f"run finished: fetched {fetched}, errors {errors}; "
        f"crawled overall {total_done}/{len(seed)}"
    )
    return 0 if errors == 0 else 1


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[1])
    parser.add_argument("--seed", default=DEFAULT_SEED, help="bundled dictionary.json to seed from")
    parser.add_argument("--extra-words", default=None,
                        help="optional text file, one extra seed word per line")
    parser.add_argument("--state-dir", default=DEFAULT_STATE_DIR,
                        help="checkpoint/raw-cache directory (gitignored)")
    parser.add_argument("--limit", type=int, default=None,
                        help="max fetches this run (sample crawls)")
    parser.add_argument("--max-seconds", type=float, default=None,
                        help="clean stop after this many seconds (CI chunking)")
    args = parser.parse_args(argv)
    return run(args)


if __name__ == "__main__":
    sys.exit(main())
