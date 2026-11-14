#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Generate app/src/main/assets/words.json from ECDICT (MIT, https://github.com/skywind3000/ECDICT).

Preset books come from the ECDICT tag field (cet4/cet6/ky). The pos column is empty
for those words, so POS prefixes sit at the start of each translation line
("vt. 放弃...").

Output JSON schema, matching the Room global word pool:
  {
    "source": "ECDICT (MIT)",
    "books": [
      { "name": "CET-4", "words": [ { "text": "apple", "phonetic": "/ˈæpl/", "senses": [ {"pos":"n.","meaning":"苹果"} ] } ] },
      ...
    ]
  }

Usage: python tools/gen_words_json.py <path/to/ecdict.csv>
Reads ./ecdict.csv by default.
"""

import csv
import json
import os
import re
import sys

POS_PREFIXES = (
    "prep.", "pron.", "conj.", "adv.", "num.", "art.", "aux.", "int.",
    "abbr.", "vt.", "vi.", "n.", "v.", "a.",
)
POS_RE = re.compile(r"^(" + "|".join(re.escape(p) for p in POS_PREFIXES) + r")")

BOOK_TAGS = [
    ("cet4", "CET-4"),
    ("cet6", "CET-6"),
    ("ky", "考研英语"),
]

# ECDICT phonetics use non-standard characters; see the per-entry notes below.
PHONETIC_CHARMAP = str.maketrans({
    "'": "\u02C8",   # ASCII apostrophe maps to IPA primary stress ˈ (U+02C8)
    "^": "\u02C8",   # caret maps to IPA primary stress ˈ, a non-standard ECDICT accent mark
    "\u04D9": "\u0259",  # Cyrillic schwa ә maps to IPA schwa ə
    "\u0454": "\u0259",  # Cyrillic ie є maps to IPA schwa ə
})


def normalize_phonetic(raw):
    """Normalize the ECDICT phonetic into standard IPA wrapped in slashes; empty input returns None."""
    if not raw:
        return None
    cleaned = raw.strip().translate(PHONETIC_CHARMAP)
    cleaned = re.sub(r"\s+", "", cleaned)
    if not cleaned:
        return None
    return "/" + cleaned + "/"


def parse_translation(translation):
    """
    Parse the ECDICT translation field into [(pos, meaning), ...].
    Each POS appears at most once, with same-POS lines merged (the Room sense
    table's (wordId, pos) unique constraint). A translation with no POS prefix
    anywhere degrades to a single n. sense so the word is not dropped.
    """
    if not translation:
        return []
    senses = []
    pos_index = {}
    # ECDICT separates sense lines with a literal backslash-plus-n, not a real newline.
    translation = translation.replace("\\n", "\n")
    for raw_line in translation.split("\n"):
        line = raw_line.strip()
        if not line:
            continue
        m = POS_RE.match(line)
        if m:
            pos = m.group(1)
            meaning = line[m.end():].strip().lstrip("，,;；").strip()
            if not meaning:
                continue
            if pos in pos_index:
                idx = pos_index[pos]
                if meaning not in senses[idx][1]:
                    senses[idx][1] = (senses[idx][1] + "； " + meaning) if senses[idx][1] else meaning
            else:
                pos_index[pos] = len(senses)
                senses.append([pos, meaning])
        else:
            # A POS-less ECDICT line (domain label [经]/[医]/[计] or a
            # continuation) joins the previous sense.
            extra = line
            extra = re.sub(r"^\[[^\]]*\]\s*", "", extra).strip()
            if not extra:
                continue
            if senses:
                if extra not in senses[-1][1]:
                    senses[-1][1] = (senses[-1][1] + "； " + extra) if senses[-1][1] else extra
            else:
                senses.append(["n.", extra])
                pos_index["n."] = 0
    return [(p, m) for p, m in senses]


def main():
    csv_path = sys.argv[1] if len(sys.argv) > 1 else "ecdict.csv"
    if not os.path.exists(csv_path):
        sys.exit(f"ERROR: ecdict.csv not found at {csv_path}")

    word_cache = {}
    books_payload = []

    with open(csv_path, encoding="utf-8") as f:
        reader = csv.DictReader(f)
        by_tag = {tag: [] for tag, _ in BOOK_TAGS}
        skipped = 0
        for row in reader:
            tags = (row.get("tag") or "").split()
            if not any(t in tags for t, _ in BOOK_TAGS):
                continue
            text = (row.get("word") or "").strip()
            if not text or " " in text:
                skipped += 1
                continue
            trans = row.get("translation") or ""
            if not trans.strip():
                skipped += 1
                continue
            if text not in word_cache:
                senses = parse_translation(trans)
                if not senses:
                    skipped += 1
                    continue
                phonetic = normalize_phonetic(row.get("phonetic") or "")
                word_cache[text] = (senses, phonetic)
            for t, _ in BOOK_TAGS:
                if t in tags:
                    by_tag[t].append(text)

    for tag, book_name in BOOK_TAGS:
        words = sorted(set(by_tag[tag]))
        book_words = []
        for text in words:
            senses, phonetic = word_cache[text]
            entry = {
                "text": text,
                "phonetic": phonetic,
                "senses": [{"pos": p, "meaning": m} for p, m in senses],
            }
            book_words.append(entry)
        books_payload.append({"name": book_name, "words": book_words})

    out = {
        "source": "ECDICT (MIT License, https://github.com/skywind3000/ECDICT)",
        "books": books_payload,
    }

    out_path = os.path.join(
        os.path.dirname(os.path.abspath(__file__)),
        "..", "app", "src", "main", "assets", "words.json",
    )
    out_path = os.path.normpath(out_path)
    with open(out_path, "w", encoding="utf-8") as f:
        json.dump(out, f, ensure_ascii=False, separators=(",", ":"))

    for tag, book_name in BOOK_TAGS:
        n = len(next(b for b in books_payload if b["name"] == book_name)["words"])
        print(f"{book_name}: {n} words")
    unique = len(word_cache)
    with_phonetic = sum(1 for _, ph in word_cache.values() if ph)
    print(f"global unique words: {unique} (with phonetic: {with_phonetic})")
    print(f"skipped (no trans / phrase / empty): {skipped}")
    print(f"wrote {out_path} ({os.path.getsize(out_path)//1024} KB)")


if __name__ == "__main__":
    main()
