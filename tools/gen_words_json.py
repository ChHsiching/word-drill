#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Generate app/src/main/assets/words.json: the preset books.

Word membership comes from the official syllabus reference lists in
tools/data/ (extracted/verified by tools/extract_cet_syllabus.py and
tools/build_kaoyan_reference.py; provenance in
docs/preset-books-provenance.md). Dictionary content (phonetic, senses) is
attached from the bundled app/src/main/assets/dictionary.json by exact word
match; a word the bundled dictionary does not know keeps its slot with
phonetic null and no senses — backfilling content is the dictionary
rebuild's job, not this membership alignment.

Output JSON schema, matching the Room global word pool (unchanged):
  {
    "source": "...",
    "books": [
      { "name": "CET-4", "words": [ { "text": "apple", "phonetic": "/ˈæpl/", "senses": [ {"pos":"n.","meaning":"苹果"} ] } ] },
      ...
    ]
  }

Usage: python tools/gen_words_json.py
"""

import json
import os
import sys

TOOLS_DIR = os.path.dirname(os.path.abspath(__file__))
REPO_ROOT = os.path.normpath(os.path.join(TOOLS_DIR, ".."))

BOOKS = [
    ("CET-4", "cet4_2016.txt"),
    ("CET-6", "cet6_2016.txt"),
    ("考研英语", "kaoyan.txt"),
]

SOURCE = ("membership: official CET 2016 syllabus + cross-verified 考研 "
          "transcription (docs/preset-books-provenance.md); definitions: "
          "Youdao (dict.youdao.com web API; tools/build_youdao_dictionary.py)")


def load_dictionary(path):
    """Group dictionary.json rows into {word: {phonetic, senses}}."""
    with open(path, encoding="utf-8") as f:
        rows = json.load(f)["words"]
    dictionary = {}
    for row in rows:
        entry = dictionary.setdefault(
            row["word"], {"phonetic": None, "senses": []})
        if entry["phonetic"] is None and row.get("phonetic"):
            entry["phonetic"] = row["phonetic"]
        sense = (row["pos"], row["meaning"])
        if sense not in entry["senses"]:
            entry["senses"].append(sense)
    return dictionary


def build_book(name, words, dictionary):
    """Build one book payload: sorted unique membership, content by exact match."""
    book_words = []
    for text in sorted(set(words)):
        entry = dictionary.get(text)
        if entry is None:
            book_words.append({"text": text, "phonetic": None, "senses": []})
        else:
            book_words.append({
                "text": text,
                "phonetic": entry["phonetic"],
                "senses": [{"pos": p, "meaning": m} for p, m in entry["senses"]],
            })
    return {"name": name, "words": book_words}


def main():
    dict_path = os.path.join(REPO_ROOT, "app", "src", "main", "assets", "dictionary.json")
    if not os.path.exists(dict_path):
        sys.exit(f"ERROR: dictionary.json not found at {dict_path}")
    dictionary = load_dictionary(dict_path)

    books_payload = []
    for book_name, ref_file in BOOKS:
        ref_path = os.path.join(TOOLS_DIR, "data", ref_file)
        if not os.path.exists(ref_path):
            sys.exit(f"ERROR: reference list not found at {ref_path}")
        with open(ref_path, encoding="utf-8") as f:
            words = [w for w in f.read().splitlines() if w]
        book = build_book(book_name, words, dictionary)
        books_payload.append(book)

        total = len(book["words"])
        no_content = sum(1 for w in book["words"] if not w["senses"])
        print(f"{book_name}: {total} words ({no_content} without dictionary content)")

    out = {"source": SOURCE, "books": books_payload}
    out_path = os.path.join(REPO_ROOT, "app", "src", "main", "assets", "words.json")
    with open(out_path, "w", encoding="utf-8") as f:
        json.dump(out, f, ensure_ascii=False, separators=(",", ":"))
    print(f"wrote {out_path} ({os.path.getsize(out_path) // 1024} KB)")


if __name__ == "__main__":
    main()
