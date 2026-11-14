#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Generate app/src/main/assets/dictionary.json from ECDICT (MIT, https://github.com/skywind3000/ECDICT).

Bundles the top 100k frequent single words (no phrases) as read-only reference
data for the Room dictionary table, which backs POS/meaning/phonetic lookup when
adding words or importing files.

Output JSON schema, matching the fields of the Room dictionary table (each POS of
the same word becomes its own entry, so DAO findByWord returns a list that maps
straight into the sense table):
  {
    "source": "ECDICT (MIT License, ...)",
    "words": [
      { "word": "apple", "phonetic": "/ˈæpl/", "pos": "n.", "meaning": "苹果" },
      ...
    ]
  }

Usage: python tools/gen_dictionary_json.py <path/to/ecdict.csv>
Reads C:/Users/Administrator/AppData/Local/Temp/ecdict.csv by default, the dev
machine's cache path.
"""

import csv
import json
import os
import re
import sys

POS_PREFIXES = (
    "prep.", "pron.", "conj.", "adv.", "num.", "art.", "aux.", "int.",
    "interj.", "abbr.", "adj.", "vt.", "vi.", "n.", "v.", "a.",
)
POS_RE = re.compile(r"^(" + "|".join(re.escape(p) for p in POS_PREFIXES) + r")")

PHONETIC_CHARMAP = str.maketrans({
    "'": "\u02C8",
    "^": "\u02C8",
    "\u04D9": "\u0259",
    "\u0454": "\u0259",
})

TOP_N = 100000


def normalize_phonetic(raw):
    """Returns standard IPA wrapped in slashes, or None for empty input."""
    if not raw:
        return None
    cleaned = raw.strip().translate(PHONETIC_CHARMAP)
    cleaned = re.sub(r"\s+", "", cleaned)
    if not cleaned:
        return None
    return "/" + cleaned + "/"


def parse_translation(translation):
    """
    Parse the ECDICT translation into [(pos, meaning), ...], at most one entry
    per POS. A POS-less line (domain label or abbreviation gloss) does not
    continue the previous line: the first one degrades to n., later ones are dropped.
    """
    if not translation:
        return []
    senses = []
    pos_index = {}
    # ECDICT separates sense lines with a literal backslash-plus-n; stray \r
    # occasionally survives csv parsing.
    translation = translation.replace("\\r", "").replace("\\n", "\n")
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
            # In ECDICT these lines are usually another sense missing its POS tag,
            # often with a domain prefix; continuing the previous line would smear
            # senses across each other. The first one degrades to n., later ones drop.
            if senses:
                continue
            extra = re.sub(r"^\[[^\]]*\]\s*", "", line).strip()
            if not extra:
                continue
            senses.append(["n.", extra])
            pos_index["n."] = 0
    return [(p, m) for p, m in senses]


def main():
    default_csv = "C:/Users/Administrator/AppData/Local/Temp/ecdict.csv"
    csv_path = sys.argv[1] if len(sys.argv) > 1 else default_csv
    if not os.path.exists(csv_path):
        sys.exit(f"ERROR: ecdict.csv not found at {csv_path}")

    rows = []
    skipped_phrase = 0
    skipped_no_trans = 0
    skipped_no_sense = 0

    with open(csv_path, encoding="utf-8") as f:
        reader = csv.DictReader(f)
        for row in reader:
            trans = (row.get("translation") or "").strip()
            if not trans:
                skipped_no_trans += 1
                continue
            word = (row.get("word") or "").strip()
            if not word or " " in word:
                skipped_phrase += 1
                continue
            senses = parse_translation(trans)
            if not senses:
                skipped_no_sense += 1
                continue
            try:
                collins = int(row.get("collins") or "0")
            except ValueError:
                collins = 0
            try:
                bnc = int(row.get("bnc") or "0")
            except ValueError:
                bnc = 0
            try:
                frq = int(row.get("frq") or "0")
            except ValueError:
                frq = 0
            rows.append((word, normalize_phonetic(row.get("phonetic") or ""), senses, collins, bnc, frq))

    # Sort by frequency: collins stars desc, then bnc/frq ascending ranks
    # (smaller rank = more common), then alphabetical.
    big = 10 ** 12
    rows.sort(key=lambda x: (
        -x[3],
        x[4] if x[4] > 0 else big,
        x[5] if x[5] > 0 else big,
        x[0],
    ))

    top = rows[:TOP_N]
    if len(top) < TOP_N:
        sys.stderr.write(f"WARN: only {len(top)} words available (target {TOP_N})\n")

    entries = []
    for word, phonetic, senses, _, _, _ in top:
        for pos, meaning in senses:
            entries.append({
                "word": word,
                "phonetic": phonetic,
                "pos": pos,
                "meaning": meaning,
            })

    out = {
        "source": "ECDICT (MIT License, https://github.com/skywind3000/ECDICT)",
        "words": entries,
    }

    out_path = os.path.join(
        os.path.dirname(os.path.abspath(__file__)),
        "..", "app", "src", "main", "assets", "dictionary.json",
    )
    out_path = os.path.normpath(out_path)
    with open(out_path, "w", encoding="utf-8") as f:
        json.dump(out, f, ensure_ascii=False, separators=(",", ":"))

    unique_words = len(set(e["word"] for e in entries))
    with_phonetic = sum(1 for e in entries if e["phonetic"])
    print(f"unique words: {unique_words}")
    print(f"total entries (word x pos): {len(entries)}")
    print(f"entries with phonetic: {with_phonetic}")
    print(f"skipped (phrase / no trans / no sense): {skipped_phrase} / {skipped_no_trans} / {skipped_no_sense}")
    print(f"wrote {out_path} ({os.path.getsize(out_path) // 1024} KB)")


if __name__ == "__main__":
    main()
