# -*- coding: utf-8 -*-
"""
Tests for build_youdao_dictionary's pure functions: POS split, punctuation and
domain-label normalization, ec parsing, entry extraction, and the validation
rules. Fixtures are hand-trimmed from real API responses (2026-11).
"""

import os
import sys

import pytest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from build_youdao_dictionary import (  # noqa: E402
    PHONETIC_GATE_EXCEPTIONS,
    apply_phonetic_fill,
    extract_entries,
    load_preset_books,
    normalize_meaning,
    parse_ec,
    split_pos,
    validate_entries,
    wrap_phonetic,
)

ABANDON_TRIMMED = {
    "input": "abandon",
    "ec": {
        "word": [{
            "usphone": "əˈbændən",
            "ukphone": "əˈbændən",
            "trs": [
                {"tr": [{"l": {"i": [
                    "v. 抛弃，遗弃；（因危险）离开，舍弃；中止，不再有；"
                    "放弃（信念、信仰或看法）；陷入，沉湎于（某种情感）",
                ]}}]},
                {"tr": [{"l": {"i": ["n. 放任，放纵"]}}]},
            ],
        }],
    },
}

HANDOVER_TRIMMED = {
    "input": "handover",
    "ec": {
        "word": [{
            "usphone": "ˈhændoʊvər",
            "ukphone": "ˈhændəʊvə(r)",
            "trs": [{"tr": [{"l": {"i": ["n. 移交，交接；（电信）切换，越区切换"]}}]}],
        }],
    },
}

BUS_TRIMMED = {
    "input": "bus",
    "ec": {
        "word": [{
            "usphone": "bʌs",
            "ukphone": "bʌs",
            "trs": [{"tr": [{"l": {"i": [
                "n. 公共汽车；（计算机／电子技术）总线；母线",
                "v. 乘公共汽车；用公共汽车运送；<美>收拾（餐桌）",
            ]}}]}],
        }],
    },
}

MISS_TRIMMED = {"input": "zzzqqqxy", "ec": None}


class TestSplitPos:
    def test_splits_pos_prefix(self):
        assert split_pos("v. 抛弃，遗弃") == ("v.", "抛弃，遗弃")

    def test_longer_prefixes_win(self):
        assert split_pos("adj. 右翼的")[0] == "adj."
        assert split_pos("vt. 瞄准")[0] == "vt."
        assert split_pos("num. 第三")[0] == "num."

    def test_no_pos_returns_empty(self):
        assert split_pos("苹果，果树") == ("", "苹果，果树")

    def test_pos_without_meaning(self):
        assert split_pos("n.") == ("n.", "")


class TestNormalizeMeaning:
    def test_domain_paren_becomes_bracket_label(self):
        assert normalize_meaning("移交，交接；（电信）切换，越区切换") == \
            "移交，交接；【电信】切换，越区切换"

    def test_compound_domain_label(self):
        assert normalize_meaning("公共汽车；（计算机／电子技术）总线") == \
            "公共汽车；【计算机／电子技术】总线"

    def test_square_bracket_domain_label(self):
        # Real Youdao data: ambitus -> "[古生] 赤道部"
        assert normalize_meaning("[古生] 赤道部") == "【古生】赤道部"
        assert normalize_meaning("[计] 总线") == "【计】总线"

    def test_square_brackets_untouched_when_not_domain(self):
        assert normalize_meaning("[sic] 这样") == "[sic] 这样"

    def test_nondomain_paren_kept(self):
        text = "抛弃，遗弃；（因危险）离开，舍弃"
        assert normalize_meaning(text) == text

    def test_angle_bracket_region_marker_becomes_full_width_paren(self):
        assert normalize_meaning("乘公共汽车；<美>收拾") == "乘公共汽车；（美）收拾"

    def test_angle_bracket_marker_with_comma_content(self):
        # Real Youdao data: can n. "罐，听；<美，非正式>监狱"
        assert normalize_meaning("罐，听；<美，非正式>监狱") == "罐，听；（美，非正式）监狱"

    def test_angle_bracket_marker_with_halfwidth_comma_and_space(self):
        # Real re-fetched data (2026-11 repair): roast "…斥责；<美,非正式>
        # 和善地戏弄" — the marker holds a half-width comma, widened by the
        # CJK comma rule, so the marker conversion must run after it.
        assert normalize_meaning("斥责；<美,非正式> 和善地戏弄") == \
            "斥责；（美，非正式）和善地戏弄"

    def test_angle_bracket_marker_with_inner_space(self):
        # Real re-fetched data: roast "<美， 非正式> 和善地戏弄" — full-width
        # comma with a stray space inside the marker.
        assert normalize_meaning("斥责；<美， 非正式> 和善地戏弄") == \
            "斥责；（美，非正式）和善地戏弄"

    def test_ascii_angle_brackets_untouched(self):
        assert normalize_meaning("x <y> z") == "x <y> z"

    def test_halfwidth_comma_semicolon_after_cjk_becomes_fullwidth(self):
        assert normalize_meaning("苹果, 梨; 香蕉") == "苹果，梨；香蕉"

    def test_ascii_punctuation_in_ascii_context_kept(self):
        assert normalize_meaning("e.g. apples, pears") == "e.g. apples, pears"

    def test_halfwidth_colon_question_after_cjk(self):
        assert normalize_meaning("例子: 见下") == "例子：见下"
        assert normalize_meaning("什么? ") == "什么？"

    def test_whitespace_collapsed_and_stripped(self):
        assert normalize_meaning("  苹果   梨  ") == "苹果 梨"

    def test_already_normalized_idempotent(self):
        text = "抛弃，遗弃；【电信】切换；（美）收拾"
        assert normalize_meaning(text) == text

    def test_spaces_hugging_full_width_parens_stripped(self):
        # Real re-fetched data (2026-11 repair): "（非正式） （尤指通过暴露在
        # 太阳下或炉火前）使受热发烫" — a half-width space hugs the full-width
        # close paren.
        assert normalize_meaning("（非正式） （尤指通过暴露）使受热发烫") == \
            "（非正式）（尤指通过暴露）使受热发烫"
        assert normalize_meaning("（美，非正式） 和善地戏弄") == \
            "（美，非正式）和善地戏弄"


class TestWrapPhonetic:
    def test_wraps_in_slashes(self):
        assert wrap_phonetic("əˈbændən") == "/əˈbændən/"

    def test_inner_whitespace_removed(self):
        assert wrap_phonetic("ə ˈbændən") == "/əˈbændən/"

    def test_variant_separator_gets_space(self):
        # Real Youdao data: the -> "ðə;ði" with a bare half-width semicolon.
        assert wrap_phonetic("ðə;ði") == "/ðə; ði/"
        assert wrap_phonetic("kæn;kən") == "/kæn; kən/"

    def test_empty_is_none(self):
        assert wrap_phonetic("") is None
        assert wrap_phonetic(None) is None


class TestParseEc:
    def test_phonetics_and_sense_lines(self):
        uk, us, lines = parse_ec(ABANDON_TRIMMED["ec"])
        assert uk == "əˈbændən"
        assert us == "əˈbændən"
        assert len(lines) == 2 and lines[0].startswith("v.")

    def test_none_ec(self):
        assert parse_ec(None) == (None, None, [])

    def test_empty_word_list(self):
        assert parse_ec({"word": []}) == (None, None, [])


class TestExtractEntries:
    def test_one_entry_per_sense_line(self):
        entries = extract_entries("abandon", ABANDON_TRIMMED)
        assert [e["pos"] for e in entries] == ["v.", "n."]
        assert entries[0]["phonetic"] == "/əˈbændən/"
        assert entries[0]["phonetic_us"] == "/əˈbændən/"
        assert "abandon" == entries[0]["word"]
        assert entries[1]["meaning"] == "放任，放纵"

    def test_domain_label_and_region_marker_converted(self):
        entries = extract_entries("handover", HANDOVER_TRIMMED) + \
            extract_entries("bus", BUS_TRIMMED)
        joined = " ".join(e["meaning"] for e in entries)
        assert "【电信】切换" in joined
        assert "【计算机／电子技术】总线" in joined
        assert "（美）收拾" in joined

    def test_miss_yields_no_entries(self):
        assert extract_entries("zzzqqxxy", MISS_TRIMMED) == []

    def test_same_pos_sense_lines_merge_into_one_entry(self):
        # Real Youdao data: antlered has three POS-less lines; the Room import
        # dedupes on (word, pos), so unmerged lines would be silently dropped.
        trimmed = {
            "input": "antlered",
            "ec": {"word": [{"ukphone": "ˌæntləd", "trs": [{"tr": [{"l": {"i": [
                "带鹿角的", "有鹿角的", "饰以鹿角的",
            ]}}]}]}]},
        }
        entries = extract_entries("antlered", trimmed)
        assert len(entries) == 1
        assert entries[0]["meaning"] == "带鹿角的；有鹿角的；饰以鹿角的"

    def test_repeated_pos_prefixes_merge(self):
        trimmed = {
            "input": "x",
            "ec": {"word": [{"trs": [{"tr": [{"l": {"i": [
                "v. 疏通", "v. 使通畅",
            ]}}]}]}]},
        }
        entries = extract_entries("x", trimmed)
        assert [(e["pos"], e["meaning"]) for e in entries] == [("v.", "疏通；使通畅")]

    def test_distinct_pos_do_not_merge(self):
        trimmed = {
            "input": "x",
            "ec": {"word": [{"trs": [{"tr": [{"l": {"i": [
                "n. 猫", "vi. 呕吐",
            ]}}]}]}]},
        }
        entries = extract_entries("x", trimmed)
        assert [(e["pos"], e["meaning"]) for e in entries] == [("n.", "猫"), ("vi.", "呕吐")]


def make_entry(word, pos="n.", meaning="x", phonetic="/a/", phonetic_us=None):
    return {"word": word, "pos": pos, "meaning": meaning,
            "phonetic": phonetic, "phonetic_us": phonetic_us}


class TestValidateEntries:
    def test_clean_sample_passes(self):
        entries = [make_entry("a"), make_entry("b", phonetic="/b/")]
        errors, report = validate_entries(entries)
        assert errors == []
        assert report["words"] == 2
        assert report["phonetic_coverage"] == 1.0

    def test_entry_without_meaning_fails(self):
        entries = [make_entry("a", meaning="")]
        errors, _ = validate_entries(entries)
        assert any("without sense" in e for e in errors)

    def test_duplicate_word_pos_fails(self):
        entries = [make_entry("a"), make_entry("a")]
        errors, _ = validate_entries(entries)
        assert any("duplicate" in e for e in errors)

    def test_low_overall_coverage_alone_is_not_gated(self):
        # ADR 0003: overall phonetic coverage is reported, not gated.
        entries = [make_entry(w, phonetic=None) for w in "abcdefghijklmnopqr"] + \
            [make_entry(w) for w in "st"]
        errors, report = validate_entries(entries)
        assert errors == []
        assert report["phonetic_coverage"] == pytest.approx(0.10)

    def test_preset_word_without_phonetic_fails(self):
        entries = [make_entry("a", phonetic=None), make_entry("b")]
        errors, report = validate_entries(
            entries, preset_books=[("cet4_2016", {"a", "b"})])
        assert any("cet4_2016" in e and "'a'" in e for e in errors), errors

    def test_preset_word_with_us_only_phonetic_passes_gate(self):
        entries = [make_entry("a", phonetic=None, phonetic_us="/a/")]
        errors, _ = validate_entries(
            entries, preset_books=[("cet4_2016", {"a"})])
        assert not any("cet4_2016" in e for e in errors), errors

    def test_nonpreset_word_without_phonetic_not_gated(self):
        entries = [make_entry("a"), make_entry("zzz", phonetic=None)]
        errors, _ = validate_entries(
            entries, preset_books=[("cet4_2016", {"a"})])
        assert errors == []

    def test_preset_word_without_entry_reported_not_failed(self):
        # ADR 0003: words Youdao has no entry for are excluded from the
        # dictionary; that is reported per book, not a validation error.
        entries = [make_entry("a")]
        errors, report = validate_entries(
            entries, preset_books=[("cet4_2016", {"a", "not crawled"})])
        assert errors == []
        stats = report["preset_books"]["cet4_2016"]
        assert stats["with_entry"] == 1
        assert stats["without_entry"] == ["not crawled"]

    def test_preset_book_phonetic_split_reported(self):
        entries = [make_entry("a", phonetic="/æ/", phonetic_us="/ə/"),  # both
                   make_entry("b", phonetic=None, phonetic_us="/b/"),   # us only
                   make_entry("c", phonetic="/k/"),                     # uk only
                   make_entry("d", phonetic=None, phonetic_us=None)]    # none
        _, report = validate_entries(
            entries, preset_books=[("cet4_2016", {"a", "b", "c", "d"})])
        stats = report["preset_books"]["cet4_2016"]
        assert stats["with_entry"] == 4
        assert stats["both"] == 1
        assert stats["uk_only"] == 1
        assert stats["us_only"] == 1
        assert stats["no_phonetic"] == ["d"]

    def test_exception_phrase_without_phonetic_passes_reported(self):
        entries = [make_entry("according to", pos="", phonetic=None)]
        errors, report = validate_entries(
            entries, preset_books=[("cet4_2016", {"according to"})])
        assert errors == []
        stats = report["preset_books"]["cet4_2016"]
        assert stats["excepted"] == ["according to"]
        assert stats["no_phonetic"] == []

    def test_exception_list_locked_to_verified_phrases(self):
        # Each entry must be verified against live Youdao as carrying no
        # ukphone AND no usphone; growing the list is an owner decision.
        assert PHONETIC_GATE_EXCEPTIONS == ("according to", "owing to")

    def test_unnormalized_punctuation_fails(self):
        entries = [make_entry("a", meaning="苹果, 梨")]
        errors, _ = validate_entries(entries)
        assert any("punctuation" in e for e in errors)

    def test_us_phonetic_counts_toward_coverage(self):
        entries = [make_entry("a", phonetic=None, phonetic_us="/a/")]
        errors, report = validate_entries(entries)
        assert not any("phonetic" in e for e in errors)
        assert report["us_coverage"] == 1.0


class TestApplyPhoneticFill:
    def test_fills_only_missing_variants(self):
        entries = [make_entry("a", phonetic="/uk/", phonetic_us=None),
                   make_entry("b", phonetic=None, phonetic_us="/us/")]
        filled, stats = apply_phonetic_fill(
            entries, {"a": {"uk": None, "us": "/ɑ/"},
                      "b": {"uk": "/biː/", "us": None}})
        assert filled[0]["phonetic"] == "/uk/"          # Youdao wins
        assert filled[0]["phonetic_us"] == "/ɑ/"        # fill supplies us
        assert filled[1]["phonetic"] == "/biː/"
        assert filled[1]["phonetic_us"] == "/us/"
        assert stats["gap_words"] == 2
        assert stats["filled_uk"] == 1
        assert stats["filled_us"] == 1
        assert stats["still_missing"] == 0

    def test_complete_word_is_no_gap(self):
        entries = [make_entry("a", phonetic="/a/", phonetic_us="/ɑ/")]
        _, stats = apply_phonetic_fill(entries, {})
        assert stats == {"gap_words": 0, "filled_uk": 0, "filled_us": 0,
                         "still_missing": 0}

    def test_gap_without_fill_stays_missing(self):
        entries = [make_entry("a", phonetic=None)]
        _, stats = apply_phonetic_fill(entries, {})
        assert stats["gap_words"] == 1
        assert stats["still_missing"] == 1

    def test_partially_filled_still_missing(self):
        # Both variants missing, fill only has uk -> still single-variant.
        entries = [make_entry("a", phonetic=None, phonetic_us=None)]
        filled, stats = apply_phonetic_fill(
            entries, {"a": {"uk": "/a/", "us": None}})
        assert filled[0]["phonetic"] == "/a/"
        assert stats["gap_words"] == 1
        assert stats["filled_uk"] == 1
        assert stats["still_missing"] == 1

    def test_all_pos_entries_of_a_word_get_the_fill(self):
        trimmed = {
            "input": "x",
            "ec": {"word": [{"ukphone": "k", "trs": [{"tr": [{"l": {"i": [
                "n. 猫", "vi. 呕吐",
            ]}}]}]}]},
        }
        entries = extract_entries("x", trimmed)
        filled, _ = apply_phonetic_fill(
            entries, {"x": {"uk": None, "us": "/eks/"}})
        assert all(e["phonetic_us"] == "/eks/" for e in filled)

    def test_fill_none_values_ignored(self):
        entries = [make_entry("a", phonetic=None)]
        filled, stats = apply_phonetic_fill(
            entries, {"a": {"uk": None, "us": None}})
        assert filled[0]["phonetic"] is None
        assert stats["filled_uk"] == 0


class TestLoadPresetBooks:
    def test_loads_the_three_preset_book_lists(self):
        books = dict(load_preset_books())
        assert set(books) == {"cet4_2016", "cet6_2016", "kaoyan"}
        assert "abandon" in books["cet4_2016"]
        assert all(len(words) > 3000 for words in books.values())


if __name__ == "__main__":
    sys.exit(pytest.main([__file__, "-v"]))
