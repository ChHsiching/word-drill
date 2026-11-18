# -*- coding: utf-8 -*-
"""
Tests for extract_cet_syllabus parsing, against synthetic spans that mirror the
real geometry of the official 2016 CET syllabus PDF: word-list entries are set
in family rows (headword in column 1, derivatives in later columns on the same
baseline), band-6 words carry a star in a separate span slightly above the
word, and the embedded font renders hyphens as fullwidth G (U+FF27).

These tests cover the pure parsing functions; the end-to-end run against the
real PDF is validated by the printed counts (row/star totals) in main().
"""

import sys
import os
import pytest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from extract_cet_syllabus import cluster_rows, split_cells, canonicalize  # noqa: E402


# A span is (x0, x1, y, font, text); fonts match the real PDF: E-B4 words,
# E-B6 punctuation, E-BZ stars.
STAR = "E-BZ"
WORD = "E-B4"
PUNCT = "E-B6"
PUA_APOS = "\U001001B3"  # the PDF's private-use apostrophe glyph


class TestClusterRows:
    def test_star_and_word_on_same_baseline_form_one_row(self):
        spans = [(70, 77, 101.0, STAR, "★"), (81, 150, 101.7, WORD, "abbreviation")]
        rows = cluster_rows(spans)
        assert len(rows) == 1
        assert [s[4] for s in rows[0]] == ["★", "abbreviation"]

    def test_different_baselines_split(self):
        spans = [
            (81, 95, 72.9, WORD, "able"),
            (81, 95, 87.1, WORD, "abandon"),
        ]
        rows = cluster_rows(spans)
        assert len(rows) == 2

    def test_columns_on_same_baseline_stay_one_row(self):
        spans = [
            (81, 100, 130.0, WORD, "able"),
            (169, 200, 130.0, WORD, "ability"),
        ]
        rows = cluster_rows(spans)
        assert len(rows) == 1
        assert len(rows[0]) == 2


class TestSplitCells:
    def test_variant_pieces_join_one_cell(self):
        # "demonstration/demo" is split into word, slash, word spans.
        spans = [
            (169, 228, 370.0, WORD, "demonstration"),
            (231, 236, 370.0, PUNCT, "/"),
            (237, 259, 370.0, WORD, "demo"),
        ]
        cells = split_cells(spans)
        assert len(cells) == 1

    def test_far_column_is_separate_cell(self):
        spans = [
            (169, 228, 370.0, WORD, "demonstration"),
            (231, 236, 370.0, PUNCT, "/"),
            (237, 259, 370.0, WORD, "demo"),
            (333, 390, 370.0, WORD, "demonstrative"),
        ]
        cells = split_cells(spans)
        assert len(cells) == 2

    def test_overlapping_spans_join(self):
        # "a/an": the slash overlaps the letters.
        spans = [
            (81, 91, 72.9, WORD, "a"),
            (85, 90, 72.9, PUNCT, "/"),
            (91, 101, 72.9, WORD, "an"),
        ]
        cells = split_cells(spans)
        assert len(cells) == 1


class TestCanonicalize:
    def test_plain_word_unchanged(self):
        assert canonicalize("abandon") == "abandon"

    def test_slash_variant_stripped_to_primary(self):
        assert canonicalize("a/an") == "a"
        assert canonicalize("center/Ｇtre") == "center"
        assert canonicalize("practise/Ｇice") == "practise"
        assert canonicalize("Celsius/Ｇcius") == "Celsius"
        assert canonicalize("sober/Ｇre") == "sober"
        assert canonicalize("barbecue/BBQ") == "barbecue"
        assert canonicalize("math(ematics)/maths") == "math"

    def test_hyphen_glyph_mapped(self):
        assert canonicalize("xＧray") == "x-ray"
        assert canonicalize("iceＧcream") == "ice-cream"
        assert canonicalize("upＧtoＧdate") == "up-to-date"

    def test_parenthetical_part_removed(self):
        assert canonicalize("auto(mobile)") == "auto"
        assert canonicalize("afterward(s)") == "afterward"
        assert canonicalize("labo(u)r") == "labor"
        assert canonicalize("mo(u)ld") == "mold"
        assert canonicalize("stor(e)y") == "story"

    def test_phrases_restored(self):
        assert canonicalize("accordingto") == "according to"
        assert canonicalize("oughtto") == "ought to"
        assert canonicalize("owingto") == "owing to"
        assert canonicalize("coup(d" + PUA_APOS + "état)") == "coup d'état"

    def test_apostrophe_pua_mapped(self):
        assert canonicalize("o" + PUA_APOS + "clock") == "o'clock"

    def test_fullwidth_period_mapped(self):
        assert canonicalize("apartment/apt．") == "apartment"

    def test_accents_kept(self):
        assert canonicalize("résumé") == "résumé"


if __name__ == "__main__":
    sys.exit(pytest.main([__file__, "-v"]))
