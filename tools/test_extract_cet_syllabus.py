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
from extract_cet_syllabus import (  # noqa: E402
    cluster_rows,
    split_cells,
    split_pieces,
    canonicalize,
    variant_second,
    extract_entries,
)


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


class FakePage:
    """Minimal page stand-in for collect_spans (rawdict shape)."""

    def __init__(self, spans):
        self._spans = spans

    def get_text(self, _mode):
        spans = []
        for x0, x1, y, font, text in self._spans:
            spans.append({
                "size": 9.7,
                "font": font,
                "chars": [{"c": c} for c in text],
                "bbox": (x0, y, x1, y),
            })
        return {"blocks": [{"lines": [{"spans": spans}]}]}


class TestSplitPieces:
    def test_variant_pieces_stay_one_piece(self):
        spans = [
            (81, 91, 72.9, WORD, "a"),
            (85, 90, 72.9, PUNCT, "/"),
            (91, 101, 72.9, WORD, "an"),
        ]
        assert split_pieces(spans) == ["a/an"]

    def test_glued_column_mate_starts_new_piece(self):
        # Real geometry (p63): the "esthetic/aesthetic" headword with the
        # derivative pair "esthetics/aesthetics" typeset 3.7pt after it — too
        # close for split_cells' column gap, separate typeset pieces.
        spans = [
            (81.27, 120.67, 172.11, WORD, "esthetic"),
            (115.62, 125.33, 171.09, PUNCT, "/"),
            (120.86, 165.01, 172.11, WORD, "aesthetic"),
            (168.70, 212.56, 172.11, WORD, "esthetics"),
            (207.32, 217.02, 171.09, PUNCT, "/"),
            (212.56, 261.17, 172.11, WORD, "aesthetics"),
        ]
        assert split_pieces(spans) == ["esthetic/aesthetic", "esthetics/aesthetics"]

    def test_plain_word(self):
        assert split_pieces([(81, 95, 72.9, WORD, "abandon")]) == ["abandon"]


class TestVariantSecond:
    def test_full_word_second_returned(self):
        assert variant_second("anybody/anyone") == "anyone"
        assert variant_second("bicycle/bike") == "bike"
        assert variant_second("a/an") == "an"
        assert variant_second("math(ematics)/maths") == "maths"
        assert variant_second("kilogram(me)/kilo") == "kilo"

    def test_trailing_fullwidth_period_stripped(self):
        assert variant_second("apartment/apt．") == "apt"

    def test_case_kept_as_printed(self):
        assert variant_second("barbecue/BBQ") == "BBQ"
        assert variant_second("television/TV") == "TV"

    def test_suffix_abbreviation_excluded(self):
        # The hyphen glyph (fullwidth G) prefixes abbreviated suffixes:
        # advise/Ｇer is advis-er, not a separate word.
        assert variant_second("analyze/Ｇyse") is None
        assert variant_second("center/Ｇtre") is None
        assert variant_second("advise/Ｇer") is None

    def test_case_only_pair_excluded(self):
        # Same word, different capitalization — not a separate headword.
        assert variant_second("Internet/internet") is None
        assert variant_second("Biblical/biblical") is None

    def test_no_variant(self):
        assert variant_second("abandon") is None


class TestExtractEntries:
    def test_full_word_second_emitted_with_row_band(self):
        page = FakePage([
            (81, 130, 100.0, WORD, "anybody"),
            (132, 137, 100.0, PUNCT, "/"),
            (138, 175, 100.0, WORD, "anyone"),
        ])
        assert extract_entries([page]) == [("anybody", False), ("anyone", False)]

    def test_second_inherits_star(self):
        page = FakePage([
            (69.63, 79.33, 101.41, STAR, "★"),
            (81.27, 140.07, 102.11, WORD, "skeptical"),
            (141.0, 146.0, 101.09, PUNCT, "/"),
            (147.0, 200.0, 102.11, WORD, "sceptical"),
        ])
        assert extract_entries([page]) == [("skeptical", True), ("sceptical", True)]

    def test_derivative_cell_pair_emitted(self):
        # Variant pairs also print in derivative columns (inquiry/enquiry).
        page = FakePage([
            (81, 120, 200.0, WORD, "inquire"),
            (169, 210, 200.0, WORD, "inquiry"),
            (211, 216, 200.0, PUNCT, "/"),
            (217, 260, 200.0, WORD, "enquiry"),
        ])
        assert extract_entries([page]) == [("inquire", False), ("enquiry", False)]

    def test_case_only_and_suffix_not_emitted(self):
        page = FakePage([
            (81, 130, 300.0, WORD, "Internet"),
            (131, 136, 300.0, PUNCT, "/"),
            (137, 170, 300.0, WORD, "internet"),
        ])
        assert extract_entries([page]) == [("Internet", False)]

    def test_glued_derivative_pair_emitted(self):
        # Real geometry (p63): starred esthetic row with the derivative pair
        # glued into the headword cell — both seconds inherit the star.
        page = FakePage([
            (69.63, 79.33, 171.41, STAR, "★"),
            (81.27, 120.67, 172.11, WORD, "esthetic"),
            (115.62, 125.33, 171.09, PUNCT, "/"),
            (120.86, 165.01, 172.11, WORD, "aesthetic"),
            (168.70, 212.56, 172.11, WORD, "esthetics"),
            (207.32, 217.02, 171.09, PUNCT, "/"),
            (212.56, 261.17, 172.11, WORD, "aesthetics"),
        ])
        assert extract_entries([page]) == [
            ("esthetic", True), ("aesthetic", True), ("aesthetics", True),
        ]


if __name__ == "__main__":
    sys.exit(pytest.main([__file__, "-v"]))
