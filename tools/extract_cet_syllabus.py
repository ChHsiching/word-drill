#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Extract the CET-4/6 syllabus word lists from the official 2016 exam syllabus
PDF (《全国大学英语四、六级考试大纲（2016年修订版）》, cet.neea.edu.cn).

Layout notes (verified against the PDF): the 词表 spans PDF pages 21-149 as
family rows set in up to four columns — column 1 holds the 词目 (the counted
syllabus headword), later columns hold derivatives aligned on the same
baseline. Band-6 words carry a ★ set in a separate span slightly above the
word. The embedded font renders hyphens as fullwidth G (U+FF27) and
apostrophes as a private-use glyph; a few phrases ("according to") lose their
space in the text layer; variant spellings print as "center/-tre".

The PDF prints its own totals (5,418 headwords / 2,551 derivatives) at the end
of the list; the typeset rows actually present are fewer. This script takes
the typeset content as ground truth and reports both counts.

Output: tools/data/cet4_2016.txt (band-4 headwords) and
tools/data/cet6_2016.txt (all headwords, the cumulative band-6 load — the
CET-6 book has always been the full 4+6 list). Duplicated headword texts
(poly-POS words listed in separate rows) are merged once; a word with any
starred row counts as band-6.

Usage: python tools/extract_cet_syllabus.py <path/to/cet-syllabus-2016.pdf>
Requires PyMuPDF (pip install pymupdf).
"""

import os
import re
import sys

import pymupdf

# Spans of the word list: these three fonts at this size, nothing else.
WORD_FONTS = ("E-B4", "E-B6", "E-BZ")
WORD_SIZE = 9.7
STAR_FONT = "E-BZ"
# Rows are ~14pt apart; the star span sits ~0.7pt above its word.
ROW_TOL = 5.0
# Spans closer than this belong to the same cell (variant pieces overlap or
# nearly touch); columns are ~10pt+ apart.
CELL_GAP = 8.0

FIRST_PAGE = 21   # 1-based, inclusive
LAST_PAGE = 149   # 1-based, inclusive

CHARMAP = str.maketrans({
    "\uff27": "-",        # fullwidth G renders the hyphen glyph
    "\U001001B3": "'",    # private-use apostrophe glyph
    "\uff0e": ".",        # fullwidth full stop
})

# Phrases whose space is lost in the text layer.
PHRASES = {
    "accordingto": "according to",
    "oughtto": "ought to",
    "owingto": "owing to",
    "coup(d'état)": "coup d'état",
}

ALLOWED = re.compile(r"^[A-Za-z\u00C0-\u024F' -]+$")
FULLWIDTH_DIGITS = re.compile(r"^[\uff10-\uff19]+$")


def cluster_rows(spans):
    """Group one page's spans into rows by baseline (y within ROW_TOL)."""
    rows = []
    for span in sorted(spans, key=lambda s: s[2]):
        if not rows or abs(span[2] - rows[-1][0]) > ROW_TOL:
            rows.append((span[2], []))
        rows[-1][1].append(span)
    return [spans for _, spans in rows]


def split_cells(spans):
    """Split one row's spans into cells: columns far apart, pieces together."""
    cells = []
    for span in sorted(spans, key=lambda s: s[0]):
        if not cells or span[0] >= cells[-1][-1][1] + CELL_GAP:
            cells.append([span])
        else:
            cells[-1].append(span)
    return cells


def canonicalize(text):
    """Reduce a printed headword to the drilled word form."""
    cleaned = text.translate(CHARMAP).strip()
    cleaned = PHRASES.get(cleaned, cleaned)
    if "/" in cleaned:
        cleaned = cleaned.split("/", 1)[0]
    cleaned = re.sub(r"\([^)]*\)", "", cleaned)
    cleaned = re.sub(r"\s+", " ", cleaned).strip()
    return cleaned


def collect_spans(page):
    raw = page.get_text("rawdict")
    spans = []
    for block in raw["blocks"]:
        for line in block.get("lines", []):
            for span in line["spans"]:
                if round(span["size"], 1) == WORD_SIZE and span["font"] in WORD_FONTS:
                    text = "".join(c["c"] for c in span["chars"])
                    if text.strip():
                        bbox = span["bbox"]
                        spans.append((bbox[0], bbox[2], bbox[1], span["font"], text))
    return spans


def extract(pdf_path):
    """Return the headwords in print order as a list of (word, starred)."""
    doc = pymupdf.open(pdf_path)
    entries = []
    for pno in range(FIRST_PAGE - 1, min(LAST_PAGE, doc.page_count)):
        for spans in cluster_rows(collect_spans(doc[pno])):
            cells = split_cells(spans)
            head = "".join(s[4] for s in cells[0] if s[3] != STAR_FONT)
            if FULLWIDTH_DIGITS.match(head):
                continue  # the totals printed after the last entry
            starred = any(s[3] == STAR_FONT for s in cells[0])
            word = canonicalize(head)
            if not ALLOWED.match(word):
                raise ValueError(f"unparsed headword form: {head!r} -> {word!r}")
            entries.append((word, starred))
    return entries


def main():
    if len(sys.argv) != 2:
        sys.exit("usage: python tools/extract_cet_syllabus.py <cet-syllabus-2016.pdf>")
    pdf_path = sys.argv[1]
    if not os.path.exists(pdf_path):
        sys.exit(f"ERROR: PDF not found at {pdf_path}")

    entries = extract(pdf_path)

    merged = {}  # word -> starred (any-starred wins), insertion ordered
    for word, starred in entries:
        merged[word] = merged.get(word, False) or starred

    cet4 = [w for w, s in merged.items() if not s]
    cet6 = list(merged)

    data_dir = os.path.normpath(os.path.join(
        os.path.dirname(os.path.abspath(__file__)), "data"))
    os.makedirs(data_dir, exist_ok=True)
    for name, words in (("cet4_2016.txt", cet4), ("cet6_2016.txt", cet6)):
        path = os.path.join(data_dir, name)
        with open(path, "w", encoding="utf-8", newline="\n") as f:
            f.write("\n".join(words) + "\n")
        print(f"wrote {path} ({len(words)} words)")

    print(f"headword rows: {len(entries)} (PDF claims 5,418)")
    print(f"distinct headwords: {len(merged)}")
    print(f"band-6 (starred) additions: {sum(1 for s in merged.values() if s)}")


if __name__ == "__main__":
    main()
