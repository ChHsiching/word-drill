#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Build the 考研 reference word list as the variant-normalized union of two
syllabus transcriptions:

- mahavivo/english-wordlists NPEE_Wordlist.txt (5,394 distinct words; a widely
  circulated 考研大纲 transcription, but lossy: entire runs of common u-y
  words are missing and it reflects an older 大纲 edition).
- exam-data/NETEMVocabulary netem_full_list.json (5,530 words; derived from
  the 2024 考研英语(一) 大纲词汇表, but trimmed by its frequency ranking).

Cross-verified against a third source, kajweb/dict's Youdao 考研 book
(5,047 distinct; a curated subset that omits elementary words), which agreed
with the union on the mid-frequency band and flagged the same holes.

Merge rules (deterministic):
- Words merge when their normalized keys match: lowercase, hyphen/space/
  apostrophe stripped, then UK->US suffix rules and an irregular-pair table.
- Display form: the NETEM form when present (newer edition, e.g. program over
  programme), else the NPEE form; a hyphenated form beats a spaced one.
- Bare "according"/"ought"/"owing" drop in favor of the phrase entries, which
  both the 考研 and CET syllabi print ("according to").

Usage:
  python tools/build_kaoyan_reference.py <NPEE_Wordlist.txt> <netem_full_list.json>
Writes tools/data/kaoyan.txt.
"""

import json
import os
import re
import sys

# Irregular UK/US pairs the suffix rules cannot derive.
VARIANT_PAIRS = {
    "defence": "defense",
    "mould": "mold",
    "programme": "program",
    "judgement": "judgment",
    "sceptical": "skeptical",
    "manoeuvre": "maneuver",
    "sulphur": "sulfur",
    "fibre": "fiber",
    "enrol": "enroll",
    "enrolment": "enrollment",
    "fulfil": "fulfill",
    "fulfilment": "fulfillment",
    "instalment": "installment",
    "skilful": "skillful",
    "enquiry": "inquiry",
    "distil": "distill",
    "aesthetic": "esthetic",
    "artefact": "artifact",
    "gramme": "gram",
    "goodby": "goodbye",
    "madame": "madam",
    "axe": "ax",
    "aluminium": "aluminum",
    "smoulder": "smolder",
}

# Bare adverbs that only exist as phrase entries in the syllabi.
PHRASES = {
    "according": "according to",
    "ought": "ought to",
    "owing": "owing to",
}

SUFFIX_RULES = (
    (re.compile(r"isation$"), "ization"),
    (re.compile(r"ise$"), "ize"),
    (re.compile(r"yse$"), "yze"),
    (re.compile(r"tre$"), "ter"),
    (re.compile(r"our$"), "or"),
    (re.compile(r"ogue$"), "og"),
    (re.compile(r"ence$"), "ense"),
)


def us_key(word):
    """Normalized key under which UK/US spelling variants merge."""
    key = word.lower().replace("-", "").replace(" ", "").replace("'", "")
    key = VARIANT_PAIRS.get(key, key)
    for pattern, repl in SUFFIX_RULES:
        if pattern.search(key):
            key = pattern.sub(repl, key)
            break
    return key


def choose_form(npee_form, netem_form):
    """Pick the display form for a merged key: NETEM (newer) wins, but a
    hyphenated form from either source beats a spaced one."""
    candidates = [f for f in (netem_form, npee_form) if f]
    for form in candidates:
        if "-" in form and " " not in form:
            return form
    return candidates[0]


def load_npee(path):
    words = []
    for line in open(path, encoding="utf-8"):
        line = line.strip()
        if line:
            words.append(line.split(" ", 1)[0].replace("oˈclock", "o'clock"))
    return words


def load_netem(path):
    data = json.load(open(path, encoding="utf-8"))
    key = next(iter(data))  # "5530考研词汇词频排序表"
    return [entry["单词"] for entry in data[key]]


def build(npee_words, netem_words):
    by_key = {}
    for word in npee_words:
        by_key.setdefault(us_key(word), []).append(("npee", word))
    for word in netem_words:
        by_key.setdefault(us_key(word), []).append(("netem", word))

    merged = []
    for entries in by_key.values():
        npee_form = next((w for src, w in entries if src == "npee"), None)
        netem_form = next((w for src, w in entries if src == "netem"), None)
        form = choose_form(npee_form, netem_form)
        bare = form.lower().replace(" ", "").replace("-", "")
        if bare in PHRASES and us_key(PHRASES[bare]) in by_key:
            continue  # the phrase entry covers this bare adverb
        merged.append(form)
    return merged


def main():
    if len(sys.argv) != 3:
        sys.exit("usage: python tools/build_kaoyan_reference.py "
                 "<NPEE_Wordlist.txt> <netem_full_list.json>")
    npee_path, netem_path = sys.argv[1], sys.argv[2]
    for p in (npee_path, netem_path):
        if not os.path.exists(p):
            sys.exit(f"ERROR: source not found at {p}")

    npee_words = load_npee(npee_path)
    netem_words = load_netem(netem_path)
    merged = build(npee_words, netem_words)

    out_path = os.path.normpath(os.path.join(
        os.path.dirname(os.path.abspath(__file__)), "data", "kaoyan.txt"))
    os.makedirs(os.path.dirname(out_path), exist_ok=True)
    with open(out_path, "w", encoding="utf-8", newline="\n") as f:
        f.write("\n".join(merged) + "\n")

    print(f"NPEE distinct: {len(set(npee_words))}")
    print(f"NETEM distinct: {len(set(netem_words))}")
    print(f"merged union: {len(merged)} words")
    print(f"wrote {out_path}")


if __name__ == "__main__":
    main()
