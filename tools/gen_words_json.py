#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Generate app/src/main/assets/words.json from ECDICT (MIT, https://github.com/skywind3000/ECDICT).

About the data source:
- ECDICT is an MIT-licensed English-Chinese dictionary database. It tags vocabulary
  against exam syllabi and corpora.
- The three preset books take their members from the ECDICT tag field: cet4, cet6, ky
  (the postgraduate entrance exam).
- POS and Chinese meanings are parsed line by line from the translation field. The pos
  column is empty for cet4/cet6/ky words, so the POS sits at the start of each
  translation line, as in "vt. 放弃...".
- Ticket #14: the phonetic (IPA) comes from the ECDICT phonetic field, normalized to
  standard IPA and wrapped in slashes, giving "phonetic": "/əˈbændən/".

Output JSON schema, matching the Room global word pool:
  {
    "source": "ECDICT (MIT)",
    "books": [
      { "name": "CET-4", "words": [ { "text": "apple", "phonetic": "/ˈæpl/", "senses": [ {"pos":"n.","meaning":"苹果"} ] } ] },
      ...
    ]
  }

Parsing rules. They keep the (wordId, pos) unique constraint intact:
- Split translation on "\n".
- If a line starts with a known POS prefix
  (n./v./vt./vi./a./adv./prep./conj./num./pron./art./aux./int./abbr.),
  start a new sense whose pos is the prefix and whose meaning is the remaining
  Chinese text.
- Otherwise, for a domain label line such as "[经] 能力" or a continuation line
  without a prefix, append to the previous sense's meaning, separated by a
  semicolon. Do not start a new sense.
- If one POS spans several lines, rare in practice, merge them into the same
  meaning.

Phonetic normalization (normalize_phonetic):
- The ECDICT phonetic field uses a few non-standard characters. Cyrillic ә and є
  stand in for the IPA schwa, and the accent mark is "'" instead of "ˈ". Map them
  to standard IPA: ә to ə, є to ə, ' to ˈ, then strip whitespace.
- Wrap in slashes: "buk" gives "/buk/", matching the "/əˈbændən/" format in the
  #14 acceptance criteria.
- An empty string returns None. The UI renders null as having no phonetic.

Usage: python tools/gen_words_json.py <path/to/ecdict.csv>
Reads ./ecdict.csv by default.
"""

import csv
import json
import os
import re
import sys

# Known English POS abbreviations, matched as line-start prefixes. Order does not
# matter, but each entry is a full token to avoid partial matches.
POS_PREFIXES = (
    "prep.", "pron.", "conj.", "adv.", "num.", "art.", "aux.", "int.",
    "abbr.", "vt.", "vi.", "n.", "v.", "a.",
)
POS_RE = re.compile(r"^(" + "|".join(re.escape(p) for p in POS_PREFIXES) + r")")

# Maps ECDICT tags to the app's book names.
BOOK_TAGS = [
    ("cet4", "CET-4"),
    ("cet6", "CET-6"),
    ("ky", "考研英语"),
]

# Non-standard characters in the ECDICT phonetic field, mapped to standard IPA.
# The character survey behind this lives in the module docstring.
# Note that ә (U+04D9, Cyrillic schwa) and є (U+0454, Cyrillic ie) both stand for
# IPA ə (U+0259) in ECDICT.
# "^" occasionally appears as an accent mark in ECDICT, on 7 cet4/cet6/ky words,
# such as "God" recorded as "^ɔd". It means the same as "'" (primary stress);
# both map to the IPA primary-stress mark ˈ.
PHONETIC_CHARMAP = str.maketrans({
    "'": "\u02C8",   # ASCII apostrophe maps to IPA primary stress ˈ (U+02C8)
    "^": "\u02C8",   # caret maps to IPA primary stress ˈ, a non-standard ECDICT accent mark
    "\u04D9": "\u0259",  # Cyrillic schwa ә maps to IPA schwa ə
    "\u0454": "\u0259",  # Cyrillic ie є maps to IPA schwa ə
})


def normalize_phonetic(raw):
    """
    Normalize the ECDICT phonetic field into a standard IPA string wrapped in
    slashes. Empty input returns None. For example "'bændәn" gives "/ˈbændən/",
    "buk" gives "/buk/", and "" gives None.
    """
    if not raw:
        return None
    cleaned = raw.strip().translate(PHONETIC_CHARMAP)
    # Strip whitespace between characters. ECDICT rarely has any, as in "kəm'pju:tə",
    # but strip unconditionally to be safe.
    cleaned = re.sub(r"\s+", "", cleaned)
    if not cleaned:
        return None
    return "/" + cleaned + "/"


def parse_translation(translation):
    """
    Parse the ECDICT translation field into [(pos, meaning), ...].
    Each POS appears at most once, with same-POS lines merged, which satisfies
    the Room (wordId, pos) unique constraint.
    """
    if not translation:
        return []
    senses = []          # list of [pos, meaning]
    pos_index = {}       # pos -> index in senses, used to merge same-POS entries
    # ECDICT separates sense lines with a literal "\n", backslash plus n as two
    # characters, not a real newline. Restore real newlines before parsing by line.
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
                # This POS already exists, so merge. Rare in theory; kept as a safety net.
                idx = pos_index[pos]
                if meaning not in senses[idx][1]:
                    senses[idx][1] = (senses[idx][1] + "； " + meaning) if senses[idx][1] else meaning
            else:
                pos_index[pos] = len(senses)
                senses.append([pos, meaning])
        else:
            # A line without a POS prefix, such as a domain label [经]/[医]/[计] or a
            # continuation note, joins the previous sense.
            extra = line
            # Drop the bracketed domain prefix and keep the meaning text.
            extra = re.sub(r"^\[[^\]]*\]\s*", "", extra).strip()
            if not extra:
                continue
            if senses:
                if extra not in senses[-1][1]:
                    senses[-1][1] = (senses[-1][1] + "； " + extra) if senses[-1][1] else extra
            else:
                # No POS prefix anywhere in the translation, so fall back to a single
                # n. sense. This avoids dropping the word.
                senses.append(["n.", extra])
                pos_index["n."] = 0
    return [(p, m) for p, m in senses]


def main():
    csv_path = sys.argv[1] if len(sys.argv) > 1 else "ecdict.csv"
    if not os.path.exists(csv_path):
        sys.exit(f"ERROR: ecdict.csv not found at {csv_path}")

    # Parse each word once and share it across books. Cache text -> (senses, phonetic).
    word_cache = {}
    books_payload = []

    with open(csv_path, encoding="utf-8") as f:
        reader = csv.DictReader(f)
        # First pass: collect words for each tag.
        by_tag = {tag: [] for tag, _ in BOOK_TAGS}
        skipped = 0
        for row in reader:
            tags = (row.get("tag") or "").split()
            if not any(t in tags for t, _ in BOOK_TAGS):
                continue
            text = (row.get("word") or "").strip()
            # Skip dirty rows such as phrases containing spaces, keeping only single
            # words to reduce data noise.
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

    # Stats.
    for tag, book_name in BOOK_TAGS:
        n = len(next(b for b in books_payload if b["name"] == book_name)["words"])
        print(f"{book_name}: {n} words")
    # Global unique word count.
    unique = len(word_cache)
    with_phonetic = sum(1 for _, ph in word_cache.values() if ph)
    print(f"global unique words: {unique} (with phonetic: {with_phonetic})")
    print(f"skipped (no trans / phrase / empty): {skipped}")
    print(f"wrote {out_path} ({os.path.getsize(out_path)//1024} KB)")


if __name__ == "__main__":
    main()
