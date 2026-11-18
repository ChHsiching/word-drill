#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Validate the Youdao crawl output and emit the app-ready dictionary JSON.

Reads the crawler's raw cache (raw.jsonl in the state directory), turns each
trimmed response into dictionary entries, validates them, and writes the same
shape the app consumes today — one entry per sense line — plus the new US
phonetic alongside the existing (UK) phonetic:

  {
    "source": "Youdao (dict.youdao.com web API, crawled YYYY-MM-DD)",
    "words": [
      { "word": "handover", "phonetic": "/ˈhændəʊvə(r)/",
        "phonetic_us": "/ˈhændoʊvər/", "pos": "n.",
        "meaning": "移交，交接；【电信】切换，越区切换" },
      ...
    ]
  }

Validation rules (spec, issue #49): every entry has a word and at least one
sense; phonetic present for >=95% of words (UK or US); no duplicate words
(each (word, pos) appears once — the Room import dedupes on that key); meaning
punctuation normalized (normalize_meaning is idempotent on its own output).

Youdao renders domain labels as full-width parens, e.g. （电信）切换; the app
data uses bracket labels per ADR 0001, e.g. 【电信】切换. Only parentheticals
whose whole content (parts split by ／) is in DOMAIN_LABELS convert —
qualifiers like （因危险） or （美） stay parenthetical.

Usage:
  python tools/build_youdao_dictionary.py [--state-dir DIR] [--out FILE]
      [--source-run-date YYYY-MM-DD]

Exit 0 with the file written when validation passes; exit 1 with the error
list otherwise. The default output stays OUTSIDE git (.scratch); pass
--out app/src/main/assets/dictionary.json when replacing the bundled asset
after the full crawl.
"""

import argparse
import datetime
import json
import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from youdao_crawler import DEFAULT_STATE_DIR, load_raw  # noqa: E402

# Subject-domain labels Youdao marks with full-width parens. Extend as the
# full crawl surfaces more; a parenthetical converts to a 【】 label only when
# every ／-separated part is listed here.
DOMAIN_LABELS = {
    "电信", "无线电", "计算机", "电子技术", "电子学", "网络", "通信",
    "计", "电", "机", "数", "医", "律", "经", "化", "物", "生", "军",
    "航", "海", "气", "天", "地质", "地理", "矿", "农", "药", "印",
    "纺", "冶", "光", "力", "测", "体育", "音乐", "棋", "语法", "宗教",
    "圣经", "铁路", "汽车", "航空", "航海", "气象", "印刷", "古生",
}

POS_PREFIXES = (
    "prep.", "pron.", "conj.", "interj.", "adv.", "num.", "art.", "aux.",
    "int.", "abbr.", "adj.", "det.", "vt.", "vi.", "n.", "v.", "a.",
)
POS_RE = re.compile(r"^(" + "|".join(re.escape(p) for p in POS_PREFIXES) + r")")

# CJK ideographs + CJK punctuation/full-width forms, for adjacency tests.
CJK_RE = r"[\u3400-\u4dbf\u4e00-\u9fff\uf900-\ufaff\u3000-\u303f\uff00-\uffef]"

HALF_TO_FULL = {",": "，", ";": "；", ":": "：", "?": "？", "!": "！"}

# <美>, <美，非正式> style region/register markers -> full-width parens.
ANGLE_MARKER_RE = re.compile(r"<([\u4e00-\u9fff][\u4e00-\u9fff，、]{0,11})>")

PHONETIC_COVERAGE_MIN = 0.95


def split_pos(line):
    """Split a Youdao sense line into (pos, meaning); pos is "" when absent."""
    m = POS_RE.match(line)
    if not m:
        return "", line
    return m.group(1), line[m.end():].strip()


def _is_domain_label(content):
    parts = [p.strip() for p in content.split("／")]
    return bool(parts) and all(p in DOMAIN_LABELS for p in parts)


def normalize_meaning(text):
    """Normalize punctuation in a Chinese sense text:
    1. collapse whitespace runs to one space and strip;
    2. <美> style markers -> （美）;
    3. half-width , ; : ? ! -> full-width when adjacent to CJK;
    4. （电信） and [古生] domain labels -> 【】 form;
    5. strip spaces hugging full-width separators and labels.
    Idempotent on its own output.
    """
    text = re.sub(r"\s+", " ", text).strip()
    text = ANGLE_MARKER_RE.sub(r"（\1）", text)
    for half, full in HALF_TO_FULL.items():
        text = re.sub(f"(?<={CJK_RE}){re.escape(half)}", full, text)
        text = re.sub(f"{re.escape(half)}(?={CJK_RE})", full, text)

    def repl(m):
        content = m.group(1)
        return f"【{content}】" if _is_domain_label(content) else m.group(0)

    text = re.sub(r"（([^（）]+)）", repl, text)
    # Youdao also marks domains with square brackets: "[古生] 赤道部".
    text = re.sub(r"\[([^[\]]+)\]", repl, text)
    # No space hugs full-width separators or labels: "苹果， 梨" -> "苹果，梨".
    text = re.sub(r" +([，；：？！】])", r"\1", text)
    return re.sub(r"([，；：？！】]) +", r"\1", text)


def wrap_phonetic(raw):
    """IPA wrapped in slashes with inner whitespace removed; None for empty.
    Youdao separates pronunciation variants with a bare ";" — give it a space."""
    if not raw:
        return None
    cleaned = re.sub(r";(?=\S)", "; ", re.sub(r"\s+", "", raw))
    return "/" + cleaned + "/" if cleaned else None


def parse_ec(ec):
    """(uk_phone, us_phone, sense_lines) from a trimmed ec subtree."""
    if not ec:
        return None, None, []
    words = ec.get("word") or []
    if isinstance(words, dict):
        words = [words]
    uk = us = None
    lines = []
    for w in words:
        if not isinstance(w, dict):
            continue
        if uk is None and w.get("ukphone"):
            uk = w["ukphone"]
        if us is None and w.get("usphone"):
            us = w["usphone"]
        for tr_group in w.get("trs") or []:
            for tr in tr_group.get("tr") or []:
                items = (tr.get("l") or {}).get("i") or []
                if isinstance(items, str):
                    items = [items]
                for item in items:
                    if isinstance(item, str) and item.strip():
                        lines.append(item.strip())
    return uk, us, lines


def extract_entries(word, trimmed):
    """Output entries for one word. Youdao may repeat a POS across sense lines
    ("v. 疏通", "v. 使通畅"); the Room import dedupes on (word, pos), so
    same-POS senses merge into one entry joined by "；". Sense lines that
    normalize to nothing are dropped, so every entry has a non-empty meaning."""
    uk, us, lines = parse_ec(trimmed.get("ec"))
    by_pos = {}
    for line in lines:
        pos, meaning = split_pos(line)
        meaning = normalize_meaning(meaning)
        if not meaning:
            continue
        by_pos.setdefault(pos, []).append(meaning)
    return [
        {
            "word": word,
            "phonetic": wrap_phonetic(uk),
            "phonetic_us": wrap_phonetic(us),
            "pos": pos,
            "meaning": "；".join(meanings),
        }
        for pos, meanings in by_pos.items()
    ]


def validate_entries(entries):
    """Return (errors, report). errors is a list of strings; report carries
    the counts printed by the CLI."""
    errors = []
    seen_pairs = set()
    words = set()
    words_with_uk = set()
    words_with_us = set()
    for e in entries:
        if not e["word"] or not e["meaning"]:
            errors.append(f"entry without sense: {e!r}")
            continue
        pair = (e["word"], e["pos"])
        if pair in seen_pairs:
            errors.append(f"duplicate (word, pos): {pair}")
        seen_pairs.add(pair)
        if normalize_meaning(e["meaning"]) != e["meaning"]:
            errors.append(f"punctuation not normalized: {e['word']} {e['meaning']!r}")
        words.add(e["word"])
        if e["phonetic"]:
            words_with_uk.add(e["word"])
        if e["phonetic_us"]:
            words_with_us.add(e["word"])

    coverage = len(words_with_uk | words_with_us) / len(words) if words else 1.0
    if coverage < PHONETIC_COVERAGE_MIN:
        errors.append(
            f"phonetic coverage {coverage:.1%} below {PHONETIC_COVERAGE_MIN:.0%}"
        )
    report = {
        "entries": len(entries),
        "words": len(words),
        "phonetic_coverage": coverage,
        "uk_coverage": len(words_with_uk) / len(words) if words else 1.0,
        "us_coverage": len(words_with_us) / len(words) if words else 1.0,
    }
    return errors, report


def build_output(entries, source):
    return {"source": source, "words": entries}


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[1])
    parser.add_argument("--state-dir", default=DEFAULT_STATE_DIR,
                        help="crawler state dir containing raw.jsonl")
    parser.add_argument("--out", default=None,
                        help="output JSON path (default: <state-dir>/dictionary.youdao.json)")
    parser.add_argument("--source-run-date", default=datetime.date.today().isoformat(),
                        help="crawl date recorded in the source string")
    parser.add_argument("--allow-validation-errors", action="store_true",
                        help="write the output and exit 0 even when validation fails "
                             "(mid-crawl CI chunks; the final full build runs strict)")
    args = parser.parse_args(argv)

    raw = load_raw(os.path.join(args.state_dir, "raw.jsonl"))
    if not raw:
        sys.exit(f"ERROR: no crawl cache at {args.state_dir}/raw.jsonl")

    entries = []
    misses = 0
    for word in sorted(raw):
        word_entries = extract_entries(word, raw[word])
        if not word_entries:
            misses += 1
        entries.extend(word_entries)

    errors, report = validate_entries(entries)
    print(f"raw words: {len(raw)} (no entries: {misses})")
    print(f"entries: {report['entries']} over {report['words']} words")
    print(f"phonetic coverage: {report['phonetic_coverage']:.1%} "
          f"(uk {report['uk_coverage']:.1%}, us {report['us_coverage']:.1%})")
    if errors:
        for e in errors[:50]:
            print(f"VALIDATION ERROR: {e}", file=sys.stderr)
        if len(errors) > 50:
            print(f"... and {len(errors) - 50} more", file=sys.stderr)
        if not args.allow_validation_errors:
            return 1

    out_path = args.out or os.path.join(args.state_dir, "dictionary.youdao.json")
    out = build_output(
        entries, f"Youdao (dict.youdao.com web API, crawled {args.source_run_date})")
    with open(out_path, "w", encoding="utf-8") as f:
        json.dump(out, f, ensure_ascii=False, separators=(",", ":"))
    print(f"wrote {out_path} ({os.path.getsize(out_path) // 1024} KB)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
