# -*- coding: utf-8 -*-
"""
Tests for the Wiktionary phonetic gap-fill (ADR 0003, owner decision
2026-11-20): gap computation over the Youdao cache, wikitext IPA parsing
rules, and fill selection. Fixtures are hand-trimmed from real Wiktionary
wikitext shapes (2026-11); no test touches the network.
"""

import os
import sys

import pytest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from wiktionary_phonetic_fill import (  # noqa: E402
    choose_fill,
    compute_gap_words,
    load_fill,
    parse_wiktionary_ipa,
    scoped_gap_words,
)


def trimmed(ukphone=None, usphone=None, lines=("n. 苹果",)):
    trs = [{"tr": [{"l": {"i": list(lines)}}]}] if lines else []
    words = ([{"ukphone": ukphone, "usphone": usphone, "trs": trs}]
             if lines else [])
    return {"input": "x", "ec": {"word": words}}


class TestComputeGapWords:
    def test_uk_only_needs_us(self):
        gaps = compute_gap_words({"apple": trimmed(ukphone="ˈæp.əl")})
        assert gaps == {"apple": {"need_uk": False, "need_us": True}}

    def test_us_only_needs_uk(self):
        gaps = compute_gap_words({"apple": trimmed(usphone="ˈæp.əl")})
        assert gaps == {"apple": {"need_uk": True, "need_us": False}}

    def test_no_phonetic_with_trs_needs_both(self):
        gaps = compute_gap_words({"apple": trimmed()})
        assert gaps == {"apple": {"need_uk": True, "need_us": True}}

    def test_both_variants_no_gap(self):
        assert compute_gap_words(
            {"apple": trimmed(ukphone="æ", usphone="æ")}) == {}

    def test_hollow_word_not_a_gap(self):
        assert compute_gap_words({"zzz": {"input": "zzz", "ec": None}}) == {}


class TestParseWiktionaryIpa:
    def test_single_unqualified_ipa(self):
        text = "==English==\n===Pronunciation===\n* {{IPA|en|/ˈæp.əl/}}\n"
        assert parse_wiktionary_ipa(text) == {
            "uk": [], "us": [], "unqualified": ["/ˈæp.əl/"]}

    def test_inline_qualifier_a2_us(self):
        # {{IPA|en|/ɒp/|/ɑp/|a2=US}}: item 2 is US, item 1 unqualified.
        text = "==English==\n* {{IPA|en|/ɒp/|/ɑp/|a2=US}}\n"
        assert parse_wiktionary_ipa(text) == {
            "uk": [], "us": ["/ɑp/"], "unqualified": ["/ɒp/"]}

    def test_line_qualifier_a_uk_and_us(self):
        text = ("==English==\n"
                "* {{a|UK}} {{IPA|en|/ɒp/}}\n"
                "* {{a|US}} {{IPA|en|/ɑp/}}\n")
        assert parse_wiktionary_ipa(text) == {
            "uk": ["/ɒp/"], "us": ["/ɑp/"], "unqualified": []}

    def test_qualifier_template_alias(self):
        text = "==English==\n* {{qualifier|GenAm}} {{IPA|en|/ɑp/}}\n"
        assert parse_wiktionary_ipa(text)["us"] == ["/ɑp/"]

    def test_rp_maps_to_uk(self):
        text = "==English==\n* {{a|RP}} {{IPA|en|/ɒp/}}\n"
        assert parse_wiktionary_ipa(text)["uk"] == ["/ɒp/"]

    def test_british_maps_to_uk(self):
        text = "==English==\n* {{a|British}} {{IPA|en|/ɒp/}}\n"
        assert parse_wiktionary_ipa(text)["uk"] == ["/ɒp/"]

    def test_unknown_qualifier_is_not_variant(self):
        text = "==English==\n* {{a|Canada}} {{IPA|en|/ɑp/}}\n"
        assert parse_wiktionary_ipa(text) == {
            "uk": [], "us": [], "unqualified": ["/ɑp/"]}

    def test_multiple_unqualified_ipas_pool(self):
        text = "==English==\n* {{IPA|en|/ɹəʊz/|/ɹoʊz/}}\n"
        assert parse_wiktionary_ipa(text) == {
            "uk": [], "us": [], "unqualified": ["/ɹəʊz/", "/ɹoʊz/"]}

    def test_non_english_section_ignored(self):
        text = "==German==\n* {{IPA|de|/ˈapfəl/}}\n"
        assert parse_wiktionary_ipa(text) == {"uk": [], "us": [], "unqualified": []}

    def test_english_heading_must_exist(self):
        text = "#REDIRECT [[apple]]\n"
        assert parse_wiktionary_ipa(text) == {"uk": [], "us": [], "unqualified": []}

    def test_disambiguation_page_skipped(self):
        text = ("==English==\n{{disambiguation}}\n"
                "* {{a|US}} {{IPA|en|/eɪ/}}\n")
        assert parse_wiktionary_ipa(text) == {"uk": [], "us": [], "unqualified": []}

    def test_lowercase_ipa_template(self):
        text = "==English==\n* {{ipa|en|/x/}}\n"
        assert parse_wiktionary_ipa(text)["unqualified"] == ["/x/"]

    def test_square_bracket_ipa_collected(self):
        text = "==English==\n* {{IPA|en|[ɹəʊz]}}\n"
        assert parse_wiktionary_ipa(text)["unqualified"] == ["[ɹəʊz]"]

    def test_non_ipa_arguments_ignored(self):
        text = "==English==\n* {{IPA|en|/x/|audio=foo.ogg}}\n"
        assert parse_wiktionary_ipa(text)["unqualified"] == ["/x/"]


class TestChooseFill:
    NEED_BOTH = {"need_uk": True, "need_us": True}

    def test_qualified_match_fills_needed_variant(self):
        parsed = {"uk": [], "us": ["/ɑp/"], "unqualified": []}
        assert choose_fill(self.NEED_BOTH, parsed) == {"uk": None, "us": "/ɑp/"}

    def test_single_unqualified_fills_only_missing_variant(self):
        parsed = {"uk": [], "us": [], "unqualified": ["/ɒp/"]}
        assert choose_fill({"need_uk": True, "need_us": False}, parsed) == \
            {"uk": "/ɒp/", "us": None}

    def test_single_unqualified_fills_uk_when_both_needed(self):
        parsed = {"uk": [], "us": [], "unqualified": ["/ɒp/"]}
        assert choose_fill(self.NEED_BOTH, parsed) == {"uk": "/ɒp/", "us": None}

    def test_multiple_unqualified_not_used(self):
        parsed = {"uk": [], "us": [], "unqualified": ["/ɹəʊz/", "/ɹoʊz/"]}
        assert choose_fill({"need_uk": True, "need_us": False}, parsed) == \
            {"uk": None, "us": None}

    def test_qualified_variant_not_cross_assigned(self):
        # UK IPA must not fill a missing US column.
        parsed = {"uk": ["/ɒp/"], "us": [], "unqualified": []}
        assert choose_fill({"need_uk": False, "need_us": True}, parsed) == \
            {"uk": None, "us": None}

    def test_first_qualifier_match_wins(self):
        parsed = {"uk": ["/ɒp/", "/ɒp2/"], "us": [], "unqualified": []}
        assert choose_fill({"need_uk": True, "need_us": False}, parsed)["uk"] == \
            "/ɒp/"


class TestScopedGapWords:
    TAGGED = {"input": "tagged", "ec": {
        "exam_type": ["CET4"],
        "word": [{"trs": [{"tr": [{"l": {"i": ["n. 标记词"]}}]}]}]}}

    def test_preset_and_exam_type_words_in_scope(self):
        raw = {"presetword": trimmed(), "tagged": self.TAGGED}
        scoped = scoped_gap_words(raw, compute_gap_words(raw),
                                  [("cet4_2016", {"presetword"})])
        assert scoped == ["presetword", "tagged"]

    def test_untagged_gap_word_out_of_scope(self):
        # Owner rule 2026-11-20: spend nothing on words no book uses.
        raw = {"deep-tail": trimmed()}
        assert scoped_gap_words(raw, compute_gap_words(raw), []) == []

    def test_word_with_both_variants_never_in_scope(self):
        raw = {"complete": trimmed(ukphone="a", usphone="a"),
               "tagged": self.TAGGED}
        assert scoped_gap_words(raw, compute_gap_words(raw), []) == ["tagged"]


class TestLoadFill:
    def test_loads_jsonl_last_line_wins(self, tmp_path):
        path = tmp_path / "wiktionary_fill.jsonl"
        path.write_text(
            '{"word":"a","uk":"/a/","us":null}\n'
            '{"word":"a","uk":null,"us":"/eɪ/"}\n',
            encoding="utf-8")
        assert load_fill(str(path)) == {"a": {"uk": None, "us": "/eɪ/"}}

    def test_missing_file_empty(self, tmp_path):
        assert load_fill(str(tmp_path / "none.jsonl")) == {}


if __name__ == "__main__":
    sys.exit(pytest.main([__file__, "-v"]))
