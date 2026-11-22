#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Validate the bundled preset books (app/src/main/assets/words.json) against
the official syllabus reference lists in tools/data/:

- zero missing / zero extra words per book (set comparison against the
  reference),
- word count within SIZE_TOLERANCE of the official printed syllabus size
  (CET-4 ~4,155 band-4 词目, CET-6 5,418, 考研 ~5,500),
- no duplicate entries within a book.

Exit code 0 iff every check passes. See docs/preset-books-provenance.md for
why the reference counts differ slightly from the printed syllabus sizes.

Usage: python tools/validate_preset_books.py
"""

import json
import os
import sys

TOOLS_DIR = os.path.dirname(os.path.abspath(__file__))
REPO_ROOT = os.path.normpath(os.path.join(TOOLS_DIR, ".."))
WORDS_JSON = os.path.join(REPO_ROOT, "app", "src", "main", "assets", "words.json")

# book name -> (reference file, official printed syllabus size, size note)
BOOKS = {
    "CET-4": ("cet4_2016.txt", 4155, "2016 syllabus band-4 headwords incl. variant seconds, printed claim 5,418 - 1,263 starred"),
    "CET-6": ("cet6_2016.txt", 5418, "2016 syllabus headwords incl. variant seconds, printed total 5,418"),
    "考研英语": ("kaoyan.txt", 5500, "考研大纲 printed vocabulary size (约5,500)"),
}

SIZE_TOLERANCE = 0.05


def compare_book(reference_words, book_words):
    """Compare book membership against the reference; set-based."""
    ref_set = set(reference_words)
    book_set = set(book_words)
    missing = sorted(ref_set - book_set)
    extra = sorted(book_set - ref_set)
    return {
        "missing": missing,
        "extra": extra,
        "ref_count": len(ref_set),
        "book_count": len(book_words),
        "ok": not missing and not extra,
    }


def size_ok(book_count, expected):
    return abs(book_count - expected) / expected <= SIZE_TOLERANCE


def main():
    with open(WORDS_JSON, encoding="utf-8") as f:
        books = {b["name"]: b for b in json.load(f)["books"]}

    failures = 0
    for name, (ref_file, expected, note) in BOOKS.items():
        if name not in books:
            print(f"FAIL {name}: book missing from words.json")
            failures += 1
            continue
        ref_path = os.path.join(TOOLS_DIR, "data", ref_file)
        with open(ref_path, encoding="utf-8") as f:
            reference = [w for w in f.read().splitlines() if w]
        words = [w["text"] for w in books[name]["words"]]

        report = compare_book(reference, words)
        dups = sorted({w for w in words if words.count(w) > 1})
        size_pass = size_ok(report["book_count"], expected)

        status = "PASS" if report["ok"] and not dups and size_pass else "FAIL"
        if status == "FAIL":
            failures += 1
        print(f"{status} {name}: book {report['book_count']} / reference "
              f"{report['ref_count']} / official size {expected} ({note})")
        if report["missing"]:
            print(f"  missing ({len(report['missing'])}): {report['missing']}")
        if report["extra"]:
            print(f"  extra ({len(report['extra'])}): {report['extra']}")
        if dups:
            print(f"  duplicates: {dups}")
        if not size_pass:
            delta = report["book_count"] - expected
            print(f"  size out of ±{int(SIZE_TOLERANCE * 100)}% band: {delta:+d} vs official {expected}")

    if failures:
        sys.exit(f"{failures} book(s) failed validation")
    print("all books validated")


if __name__ == "__main__":
    main()
