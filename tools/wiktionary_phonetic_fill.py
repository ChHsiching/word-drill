#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Fill missing phonetic variants from English Wiktionary IPA (ADR 0003, owner
decision 2026-11-20).

Definitions stay 100% Youdao; this tool only supplies IPA for variants
Youdao's entry does not carry. It reads the crawler's raw cache, computes the
gap set — entries with senses but missing exactly one variant (uk-only needs
us, us-only needs uk) or both — fetches each gap word's wikitext from
en.wiktionary.org, and parses variant-qualified IPA (US/GenAm -> us,
UK/RP/British -> uk; a single unqualified IPA fills the missing column).

Results go to <state-dir>/wiktionary_fill.jsonl ({"word","uk","us"}, one per
processed word, nulls when Wiktionary has nothing usable) and processed words
are checkpointed in <state-dir>/wiktionary_fill_state.jsonl, so an interrupted
fill resumes without re-fetching. The Youdao raw cache is never mutated;
tools/build_youdao_dictionary.py merges the fill file at build time.

Disambiguation pages are skipped. Scope (owner rule 2026-11-20): fetch only
gap words a book uses — preset-list words or words Youdao tags with
exam_type; untagged deep-tail gaps are skipped entirely. Pace is <=1 req/s;
HTTP 429 backs off 60s with doubling, and sustained refusal stops the run
cleanly (state kept, resumable).

Usage:
  python tools/wiktionary_phonetic_fill.py [--state-dir DIR]
      [--limit N] [--max-seconds S]
"""

import argparse
import json
import os
import re
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from build_youdao_dictionary import (  # noqa: E402
    load_preset_books, parse_ec, wrap_phonetic)
from youdao_crawler import DEFAULT_STATE_DIR, load_checkpoint, load_raw  # noqa: E402

API_URL = "https://en.wiktionary.org/w/api.php"
REQUEST_INTERVAL = 1.0  # seconds between request starts
USER_AGENT = (
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/126.0 Safari/537.36"
)

HEADING_RE = re.compile(r"^==\s*([^=]+?)\s*==\s*$")
IPA_TEMPLATE_RE = re.compile(r"\{\{[Ii][Pp][Aa]\s*\|([^{}]*)\}\}")
LINE_QUALIFIER_RE = re.compile(r"\{\{(?:a|qualifier)\s*\|([^{}]*)\}\}", re.I)
IPA_ITEM_RE = re.compile(r"^[/\[].+[/\]]$")
INLINE_QUALIFIER_RE = re.compile(r"^(?:a|q)(\d*)$", re.I)

US_KEYWORDS = ("US", "GenAm", "General American")
UK_KEYWORDS = ("UK", "RP", "Received Pronunciation", "British", "BrE")


def compute_gap_words(raw):
    """{word: {"need_uk", "need_us"}} for cache entries that have sense
    lines but lack one or both phonetic variants."""
    gaps = {}
    for word, trimmed in raw.items():
        uk, us, lines = parse_ec(trimmed.get("ec"))
        if not lines:
            continue
        need_uk = wrap_phonetic(uk) is None
        need_us = wrap_phonetic(us) is None
        if need_uk or need_us:
            gaps[word] = {"need_uk": need_uk, "need_us": need_us}
    return gaps


def _classify(qualifier):
    """'us', 'uk', or None (unknown/ambiguous)."""
    is_us = any(k in qualifier for k in US_KEYWORDS)
    is_uk = any(k in qualifier for k in UK_KEYWORDS)
    if is_us != is_uk:
        return "us" if is_us else "uk"
    return None


def _split_template_args(argstr):
    args = [a.strip() for a in argstr.split("|")]
    if not args or args[0].lower() != "en":
        return None, [], {}
    items, quals = [], {}
    for a in args[1:]:
        if "=" in a:
            key, _, value = a.partition("=")
            m = INLINE_QUALIFIER_RE.match(key.strip())
            if m:
                index = int(m.group(1)) if m.group(1) else 1
                quals[index] = value.strip()
        else:
            items.append(a)
    return args[0], items, quals


def parse_wiktionary_ipa(content):
    """IPA items grouped by variant from a page's wikitext. Only the
    ==English== section counts; disambiguation pages yield nothing."""
    parsed = {"uk": [], "us": [], "unqualified": []}
    in_english = False
    for line in content.splitlines():
        heading = HEADING_RE.match(line)
        if heading:
            in_english = heading.group(1).strip() == "English"
            continue
        if not in_english:
            continue
        if "{{disambig" in line.lower():
            return {"uk": [], "us": [], "unqualified": []}
        line_variant = None
        for q in LINE_QUALIFIER_RE.findall(line):
            v = _classify(q)
            if v:
                line_variant = v
                break
        for match in IPA_TEMPLATE_RE.finditer(line):
            _, items, quals = _split_template_args(match.group(1))
            ipa_items = [i for i in items if IPA_ITEM_RE.match(i)]
            for pos, item in enumerate(ipa_items, start=1):
                variant = _classify(quals.get(pos, "")) if pos in quals \
                    else line_variant
                if variant:
                    parsed[variant].append(item)
                else:
                    parsed["unqualified"].append(item)
    return parsed


def choose_fill(need, parsed):
    """Pick the fill values for one word's needs from parsed IPA: a
    qualified match per variant first; then a single unqualified IPA fills
    the missing column (uk first when both are missing). Multiple
    unqualified IPAs are too ambiguous to place and are not used."""
    fill = {"uk": None, "us": None}
    if need["need_uk"] and parsed["uk"]:
        fill["uk"] = parsed["uk"][0]
    if need["need_us"] and parsed["us"]:
        fill["us"] = parsed["us"][0]
    if len(parsed["unqualified"]) == 1:
        single = parsed["unqualified"][0]
        if fill["uk"] is None and need["need_uk"]:
            fill["uk"] = single
        elif fill["us"] is None and need["need_us"]:
            fill["us"] = single
    return fill


def load_fill(path):
    """{word: {"uk", "us}} from wiktionary_fill.jsonl; later lines win;
    unparsable trailing lines are skipped."""
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
            loaded[rec["word"]] = {"uk": rec.get("uk"), "us": rec.get("us")}
    return loaded


def fetch_wikitext(word):
    """Page wikitext, or None when the page does not exist. Raises on
    HTTP/transport errors (the run loop handles pacing/backoff)."""
    url = API_URL + "?" + urllib.parse.urlencode({
        "action": "query", "format": "json", "prop": "revisions",
        "rvslots": "main", "rvprop": "content", "redirects": "1",
        "titles": word})
    req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(req, timeout=30) as r:
        data = json.load(r)
    for page in data.get("query", {}).get("pages", {}).values():
        if page.get("missing") or "revisions" not in page:
            return None
        slot = page["revisions"][0].get("slots", {}).get("main", {})
        return slot.get("*") or slot.get("content")
    return None


def scoped_gap_words(raw, gaps, preset_books):
    """Owner rule (2026-11-20): spend Wiktionary fetches only on words a
    book uses — preset-list words or words Youdao tags with exam_type.
    Untagged deep-tail gaps are skipped entirely. Returns the sorted scope."""
    preset = set()
    for _, words in preset_books:
        preset |= words
    scoped = []
    for word in gaps:
        if word in preset:
            scoped.append(word)
            continue
        ec = (raw.get(word) or {}).get("ec") or {}
        if ec.get("exam_type"):
            scoped.append(word)
    return sorted(scoped)


def run(args):
    raw = load_raw(os.path.join(args.state_dir, "raw.jsonl"))
    if not raw:
        sys.exit(f"ERROR: no crawl cache at {args.state_dir}/raw.jsonl")
    gaps = compute_gap_words(raw)
    fill_path = os.path.join(args.state_dir, "wiktionary_fill.jsonl")
    state_path = os.path.join(args.state_dir, "wiktionary_fill_state.jsonl")

    processed = load_checkpoint(state_path)
    scope = scoped_gap_words(raw, gaps, load_preset_books())
    queue = [w for w in scope if w not in processed]
    print(f"gaps: {len(gaps)} words (scoped: {len(scope)}), "
          f"processed: {len(processed)}, to fetch: {len(queue)}")
    if args.limit is not None:
        queue = queue[: args.limit]
        print(f"limited to {len(queue)} fetches this run")

    fetched = filled_uk = filled_us = empty = 0
    errors = 0
    backoffs = 0
    deadline = time.monotonic() + args.max_seconds if args.max_seconds else None
    next_start = time.monotonic()
    for word in queue:
        if deadline is not None and time.monotonic() >= deadline:
            print("max-seconds budget reached; stopping cleanly")
            break
        delay = next_start - time.monotonic()
        if delay > 0:
            time.sleep(delay)
        next_start = max(next_start, time.monotonic()) + REQUEST_INTERVAL
        content = None
        failed = False
        while True:
            try:
                content = fetch_wikitext(word)
                backoffs = 0
                break
            except urllib.error.HTTPError as exc:
                if exc.code == 429 and backoffs < 4:
                    wait = 60 * 2 ** backoffs
                    backoffs += 1
                    print(f"429 on {word!r}; backing off {wait}s "
                          f"(attempt {backoffs}/4)", file=sys.stderr)
                    time.sleep(wait)
                    continue
                if exc.code == 429:
                    print("Wiktionary still refusing after backoffs; "
                          "stopping the fill (resumable)", file=sys.stderr)
                    errors += 1
                    failed = True
                    break
                errors += 1
                failed = True
                print(f"ERROR fetching {word!r}: {exc}", file=sys.stderr)
                break
            except Exception as exc:  # noqa: BLE001 — one bad word must not kill the run
                errors += 1
                failed = True
                print(f"ERROR fetching {word!r}: {exc}", file=sys.stderr)
                time.sleep(REQUEST_INTERVAL)
                break
        if failed:
            continue  # leave unprocessed; the next run retries it
        fill = choose_fill(gaps[word], parse_wiktionary_ipa(content or ""))
        with open(fill_path, "a", encoding="utf-8") as f:
            f.write(json.dumps({"word": word, "uk": fill["uk"],
                                "us": fill["us"]},
                               ensure_ascii=False, separators=(",", ":")) + "\n")
        with open(state_path, "a", encoding="utf-8") as f:
            f.write(word + "\n")
        fetched += 1
        if fill["uk"]:
            filled_uk += 1
        if fill["us"]:
            filled_us += 1
        if not fill["uk"] and not fill["us"]:
            empty += 1
        if fetched % 100 == 0:
            print(f"progress: {fetched} fetched, {len(queue) - fetched} "
                  f"left this run")

    print(f"run finished: fetched {fetched} (filled uk {filled_uk}, "
          f"us {filled_us}, nothing usable {empty}), errors {errors}; "
          f"processed overall {len(load_checkpoint(state_path))}/{len(scope)} "
          f"of the scoped {len(scope)} (full gap set: {len(gaps)})")
    return 0 if errors == 0 else 1


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[1])
    parser.add_argument("--state-dir", default=DEFAULT_STATE_DIR,
                        help="crawler state dir (raw.jsonl, fill files)")
    parser.add_argument("--limit", type=int, default=None,
                        help="max fetches this run")
    parser.add_argument("--max-seconds", type=float, default=None,
                        help="clean stop after this many seconds")
    args = parser.parse_args(argv)
    return run(args)


if __name__ == "__main__":
    sys.exit(main())
