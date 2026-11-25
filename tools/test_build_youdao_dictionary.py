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
    PRESET_LOOKUP_ALIASES,
    apply_phonetic_fill,
    apply_preset_aliases,
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

    # POS spellings found by reading every preset-word line (audit of
    # 2026-11-23); each fixture is a real Youdao line.
    def test_combining_form_and_prefix_forms_recognised(self):
        assert split_pos("comb. 有……腿的(-legged)") == ("comb.", "有……腿的(-legged)")
        assert split_pos("pref. 千（kilo-）") == ("pref.", "千（kilo-）")

    def test_spelled_out_variants_map_to_the_short_form(self):
        assert split_pos("inter. 天哪，哎呀") == ("int.", "天哪，哎呀")
        assert split_pos("pronoun. 自己的，属于自己的") == ("pron.", "自己的，属于自己的")

    def test_uppercase_letter_and_full_width_dot_recognised(self):
        assert split_pos("V. 打手势，用动作示意") == ("v.", "打手势，用动作示意")
        assert split_pos("n．前部，前面") == ("n.", "前部，前面")

    def test_youdao_pos_typo_mapped(self):
        # friendly: the only "cn." in the crawl; the sense is a noun.
        assert split_pos("cn. 友谊赛") == ("n.", "友谊赛")

    def test_abbreviation_that_merely_looks_like_a_pos_kept(self):
        assert split_pos("e.g. 例如") == ("", "e.g. 例如")


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
        assert normalize_meaning("  苹果   pear  ") == "苹果 pear"

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

    def test_half_open_full_close_pair_widens(self):
        # Youdao typo on live data (2026-11-23): "allege (未经证实地）宣称".
        assert normalize_meaning("(未经证实地）宣称，指控") == "（未经证实地）宣称，指控"

    def test_full_open_half_close_pair_widens(self):
        # Youdao typos on live data (2026-11-23): "（Bike)比克", "布什（人名)".
        assert normalize_meaning("（Bike)（土、土库、塞）比克") == \
            "（Bike）（土、土库、塞）比克"
        assert normalize_meaning("布什（人名)") == "布什（人名）"

    def test_balanced_paren_pairs_untouched(self):
        assert normalize_meaning("call (sb) up") == "call (sb) up"
        assert normalize_meaning("（因危险）离开") == "（因危险）离开"

    def test_unmatched_closer_untouched(self):
        # No opener to pair with: the stray closer is left exactly as sourced.
        assert normalize_meaning("a) b") == "a) b"

    def test_paren_pair_widening_idempotent(self):
        once = normalize_meaning("(未经证实地）宣称")
        assert normalize_meaning(once) == once

    def test_nested_mismatched_pairs_widen(self):
        assert normalize_meaning("（甲(乙）丙)") == "（甲（乙）丙）"

    # Subject labels, from the 2026-11-23 audit of all 6,366 preset words.
    def test_parenthetical_mid_phrase_is_never_a_label(self):
        # Youdao writes ordinary qualifiers in the same full-width parens as
        # subject labels; a real label only ever opens a sense segment.
        for text in ("紧抓，紧握；理解（力），领会",
                     "使（计算机）联网；（尤指工作中）建立人脉",
                     "非法将（汽车）改头换面",
                     "封蜡，火漆；封蜡模（印），封铅模（印）"):
            assert normalize_meaning(text) == text

    def test_parenthetical_label_opening_a_segment_converts(self):
        assert normalize_meaning("戒指，指环；（化）环；（数）环") == \
            "戒指，指环；【化】环；【数】环"
        assert normalize_meaning("（法律）原告，起诉人") == "【法律】原告，起诉人"
        assert normalize_meaning("辅修课程；（乐）小调，小音程；（逻）小项，小前提") == \
            "辅修课程；【乐】小调，小音程；【逻】小项，小前提"

    def test_labels_joined_by_a_comma_convert_together(self):
        assert normalize_meaning("感觉官能；（数，物理）指向，方向") == \
            "感觉官能；【数，物理】指向，方向"
        assert normalize_meaning("温和的；（外科，医疗）根治的；（变化）不大的") == \
            "温和的；【外科，医疗】根治的；（变化）不大的"

    def test_mixed_marker_stays_parenthetical(self):
        # One non-label part keeps the whole marker parenthetical.
        text = "突出的，显著的；（角）凸出的；（纹章，动物）后腿站立前爪举起的"
        assert normalize_meaning(text) == text
        assert normalize_meaning("收费表；<英，法律>量刑标准") == "收费表；（英，法律）量刑标准"

    def test_angle_bracket_subject_labels_become_bracket_labels(self):
        assert normalize_meaning("使遇难；<史>毁船打劫；拆") == "使遇难；【史】毁船打劫；拆"
        assert normalize_meaning("三角铁（打击乐器）；<史>（系缚士兵以鞭打的）三戟刑具") == \
            "三角铁（打击乐器）；【史】（系缚士兵以鞭打的）三戟刑具"
        assert normalize_meaning("小雕像；〈史〉（步兵的）方阵") == \
            "小雕像；【史】（步兵的）方阵"

    def test_angle_bracket_register_markers_stay_parenthetical(self):
        assert normalize_meaning("同意；〈正式〉签署（遗嘱，合同或其他文件）") == \
            "同意；（正式）签署（遗嘱，合同或其他文件）"
        assert normalize_meaning("使人苦恼的事；（喻）棘手的东西") == \
            "使人苦恼的事；（喻）棘手的东西"

    def test_mixed_and_slashed_angle_markers(self):
        assert normalize_meaning("签字；〈英，史〉文书，抄写员") == "签字；（英，史）文书，抄写员"
        assert normalize_meaning("（用布）擦干净；<澳/新，非正式>拒绝（某人）") == \
            "（用布）擦干净；（澳/新，非正式）拒绝（某人）"

    def test_label_after_a_register_marker_converts(self):
        assert normalize_meaning("困境，窘境；<非正式>（医）刮宫") == \
            "困境，窘境；（非正式）【医】刮宫"
        assert normalize_meaning("窃听，窃听器；<英>（电） 分接头") == \
            "窃听，窃听器；（英）【电】分接头"
        assert normalize_meaning("较小的；<旧>（地质）中生代的") == "较小的；（旧）【地质】中生代的"

    def test_wu_is_not_a_label(self):
        # "（物）" opens segments meaning "of a thing" as often as "physics".
        for text in ("苗条的，纤细的；单薄的；（物）狭长的",
                     "未被雇用的，失业的；（物）闲置的，未用的"):
            assert normalize_meaning(text) == text
        assert normalize_meaning("强健的；（物理）强，强相互作用的") == \
            "强健的；【物理】强，强相互作用的"

    def test_subject_label_inventory(self):
        # Every subject label met in the audit converts when it opens a segment.
        for label in ("史", "乐", "哲", "逻", "法律", "军事", "解剖", "统计",
                      "心理", "天文", "建筑", "经济", "财政", "数学", "物理", "化学",
                      "医学", "哲学", "逻辑", "语言学", "生物学", "地质", "语音",
                      "植物学", "动物学", "遗传", "会计", "教育", "音乐"):
            assert normalize_meaning(f"甲；（{label}）乙") == f"甲；【{label}】乙"

    # Ellipses.
    def test_ellipsis_spellings_unified(self):
        assert normalize_meaning("在......附近，靠近") == "在……附近，靠近"
        assert normalize_meaning("一.....就，一旦") == "一……就，一旦"
        assert normalize_meaning("把…...塞入，把…...藏入") == "把……塞入，把……藏入"
        assert normalize_meaning("把..……打包") == "把……打包"
        assert normalize_meaning("用………揉擦（皮肤、头发等）") == "用……揉擦（皮肤、头发等）"
        assert normalize_meaning("给······姓氏，给······起别名") == "给……姓氏，给……起别名"
        assert normalize_meaning("与……相似；和…一样，像") == "与……相似；和……一样，像"

    def test_abbreviation_dots_untouched(self):
        assert normalize_meaning("常缩写为 Sep. 或 Sept.") == "常缩写为 Sep. 或 Sept."
        assert normalize_meaning("e.g. apples") == "e.g. apples"

    # Half-width pairs and stray spaces in Chinese text.
    def test_half_width_paren_pair_in_chinese_context_widens(self):
        assert normalize_meaning("格雷丝 (女名)") == "格雷丝（女名）"
        assert normalize_meaning("向内，向中心；向自己，向内心 (=inwards)") == \
            "向内，向中心；向自己，向内心（=inwards）"
        assert normalize_meaning("有......腿的(-legged)") == "有……腿的（-legged）"
        assert normalize_meaning("不及物动词 (intransitive verb)") == \
            "不及物动词（intransitive verb）"

    def test_half_width_paren_pair_in_english_phrase_kept(self):
        assert normalize_meaning("call (sb) up") == "call (sb) up"
        assert normalize_meaning("jog (one's memory)") == "jog (one's memory)"

    def test_square_bracket_qualifier_becomes_parenthetical(self):
        # Real Youdao data: yours "你（们）的东西；你的责任[义务]".
        assert normalize_meaning("你的责任[义务]") == "你的责任（义务）"

    def test_space_between_cjk_characters_removed(self):
        # Every CJK-space-CJK gap in the crawl (41 outside "） （"/"】 （") is a
        # stray: "航空站 （港）", "（数字、 数量、价值）", "第九个小时 下午3点".
        assert normalize_meaning("航空站 （港）") == "航空站（港）"
        assert normalize_meaning("每日的祈祷 （=Divine Office）") == "每日的祈祷（=Divine Office）"
        assert normalize_meaning("（数字、 数量、价值）增加") == "（数字、数量、价值）增加"
        assert normalize_meaning("悠闲 （或漫不经心）地移动") == "悠闲（或漫不经心）地移动"
        assert normalize_meaning("（法）格里夫 (人名)") == "（法）格里夫（人名）"

    def test_space_between_cjk_and_latin_kept(self):
        assert normalize_meaning("常缩略为 p") == "常缩略为 p"
        assert normalize_meaning("九十（数字 90）") == "九十（数字 90）"

    def test_doubled_name_tag_collapsed(self):
        # Real Youdao data: holiday's name line is tagged "【名】 【名】".
        assert normalize_meaning("【名】 【名】（Fit）（捷、罗）菲特（人名）") == \
            "【名】（Fit）（捷、罗）菲特（人名）"


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

    def test_identical_sense_line_repeated_is_dropped(self):
        # Real Youdao data (2026-11-23): bike's verb sense repeats its
        # "deliver by bike" line verbatim; merged output would show it twice.
        trimmed = {
            "input": "bike",
            "ec": {"word": [{"trs": [{"tr": [{"l": {"i": [
                "v. 骑自行车",
            ]}}]}, {"tr": [{"l": {"i": [
                "v. 用自行车投递（信件，包裹）",
            ]}}]}, {"tr": [{"l": {"i": [
                "v. 用自行车投递（信件，包裹）",
            ]}}]}]}]},
        }
        entries = extract_entries("bike", trimmed)
        assert [e["meaning"] for e in entries] == \
            ["骑自行车；用自行车投递（信件，包裹）"]

    def test_repeated_segment_inside_one_sense_line_is_dropped(self):
        # Real Youdao data (2026-11-23): the two copies of bike's "deliver by
        # bike" sense sit inside ONE line and differ only by a space, so they
        # are identical once normalized. One of them is noise.
        trimmed = {
            "input": "bike",
            "ec": {"word": [{"trs": [{"tr": [{"l": {"i": [
                "v. <非正式>骑自行车（单车）；<英> 用自行车（或摩托车）投递（信件，包裹）；"
                "<英>用自行车（或摩托车）投递（信件，包裹）",
            ]}}]}]}]},
        }
        entries = extract_entries("bike", trimmed)
        assert [e["meaning"] for e in entries] == [
            "（非正式）骑自行车（单车）；（英）用自行车（或摩托车）投递（信件，包裹）"]

    # Name lines, placeholders and generated lines, from the 2026-11-23 audit
    # of all 6,366 preset words. Every fixture is a real Youdao line.
    def test_name_line_stays_apart_from_unrecognised_pos_lines(self):
        # hand: the "comb." line and the 【名】 line both had no recognised POS,
        # so they collapsed into one entry.
        entries = extract_entries("hand", lines(
            "n. 手；（钟表的）指针", "comb. 用……手的；手工", "v. 交，递；支持，搀扶",
            "【名】 （Hand）（英、瑞典）汉德（人名）"))
        assert [(e["pos"], e["meaning"]) for e in entries] == [
            ("n.", "手；（钟表的）指针"), ("comb.", "用……手的；手工"),
            ("v.", "交，递；支持，搀扶"), ("", "【名】（Hand）（英、瑞典）汉德（人名）")]

    def test_name_line_with_its_own_pos_prefix_stays_apart(self):
        # stark: Youdao's own "n." sits on the name line.
        entries = extract_entries("stark", lines(
            "adj. 荒凉的；严酷的", "adv. 完全地；彻底地",
            "n. 【名】（Stark）（俄、罗）斯塔尔克（人名）"))
        assert [(e["pos"], e["meaning"]) for e in entries] == [
            ("adj.", "荒凉的；严酷的"), ("adv.", "完全地；彻底地"),
            ("", "【名】（Stark）（俄、罗）斯塔尔克（人名）")]

    def test_region_tagged_name_line(self):
        # dash: the name line opens with 【英】 instead of 【名】.
        entries = extract_entries("dash", lines(
            "vi. 猛冲，飞奔", "【英】（Dash）（英、美、印）达什（人名）"))
        assert [(e["pos"], e["meaning"]) for e in entries] == [
            ("vi.", "猛冲，飞奔"), ("", "【英】（Dash）（英、美、印）达什（人名）")]

    def test_null_placeholder_dropped(self):
        # Youdao emits the literal string "null" as the POS of some name lines
        # and, once, as a trailing sense.
        entries = extract_entries("ass", lines(
            "n. 傻瓜，笨蛋；屁股，臀部", "null 【名】（Ass）（俄）阿斯（人名）"))
        assert [(e["pos"], e["meaning"]) for e in entries] == [
            ("n.", "傻瓜，笨蛋；屁股，臀部"), ("", "【名】（Ass）（俄）阿斯（人名）")]
        entries = extract_entries("stray", lines("null （Stray）（挪、美、英、俄）斯特雷（人名）"))
        assert [(e["pos"], e["meaning"]) for e in entries] == [
            ("", "（Stray）（挪、美、英、俄）斯特雷（人名）")]
        entries = extract_entries("pull", lines("n. 拉，拽；草样，校样；null"))
        assert [e["meaning"] for e in entries] == ["拉，拽；草样，校样"]

    def test_doubled_name_tag_collapsed(self):
        entries = extract_entries("holiday", lines(
            "n. 假日；度假期", "【名】 【名】（Fit）（捷、罗）菲特（人名）"))
        assert [(e["pos"], e["meaning"]) for e in entries] == [
            ("n.", "假日；度假期"), ("", "【名】（Fit）（捷、罗）菲特（人名）")]

    def test_name_line_repeated_inside_one_line_kept_once(self):
        # serve prints its name twice, the second copy without the 【名】 tag;
        # app and chap repeat theirs with the tag.
        entries = extract_entries("serve", lines(
            "n. （网球、排球等的）发球",
            "【名】  （Serve）（法）塞尔夫（人名）；（Serve）（法）塞尔夫（人名）"))
        assert [(e["pos"], e["meaning"]) for e in entries] == [
            ("n.", "（网球、排球等的）发球"), ("", "【名】（Serve）（法）塞尔夫（人名）")]
        entries = extract_entries("app", lines(
            "【名】 （App）（美、德、巴）阿普（人名）；【名】 （App）（美、德、巴）阿普（人名）"))
        assert [e["meaning"] for e in entries] == ["【名】（App）（美、德、巴）阿普（人名）"]

    def test_name_line_joins_the_posless_entry_when_both_exist(self):
        # Real Youdao data: crawled proper nouns such as Ortega carry a name
        # line and a place line, both POS-less; (word, pos) must stay unique.
        entries = extract_entries("Ortega", lines(
            "奥尔特加（人名）", "奥尔特加（地名，阿根廷、哥伦比亚）"))
        assert [(e["pos"], e["meaning"]) for e in entries] == [(
            "", "奥尔特加（人名）；奥尔特加（地名，阿根廷、哥伦比亚）")]

    def test_generated_lines_joined_without_a_stray_period(self):
        # according to: generated prose, each line ending with "。"; joining
        # the lines with "；" used to leave "。；" in the entry.
        entries = extract_entries("according to", lines(
            "根据：表示根据某人或某事的陈述、说法或信念。",
            "按照：表示按照某个标准、规则或方法。"))
        assert [(e["pos"], e["meaning"]) for e in entries] == [(
            "", "根据：表示根据某人或某事的陈述、说法或信念；按照：表示按照某个标准、规则或方法")]

    # Name entries dropped for the words of the preset books (owner decision
    # 2026-11-25). Every fixture is a real Youdao line, some shortened.
    def test_name_line_dropped_only_on_request(self):
        trimmed = lines("n. 手；（钟表的）指针", "【名】 （Hand）（英、瑞典）汉德（人名）")
        assert [e["pos"] for e in extract_entries("hand", trimmed)] == ["n.", ""]
        assert [e["pos"] for e in extract_entries("hand", trimmed, True)] == ["n."]

    def test_name_line_with_a_pos_prefix_dropped(self):
        trimmed = lines("abbr. 上午（同 a.m.）", "n. （AM）（中、英）安（人名）")
        assert [(e["pos"], e["meaning"]) for e in extract_entries("am", trimmed, True)] == [
            ("abbr.", "上午（同 a.m.）")]

    def test_name_run_appended_to_a_sense_dropped(self):
        trimmed = lines(
            "n. 灰色；暗淡的光线；戈瑞（吸收电离辐射剂量单位，符号 Gy）；（Gray）人名；（意）格拉伊；（英、法、葡、瑞典）格雷")
        assert extract_entries("gray", trimmed, True)[0]["meaning"] == \
            "灰色；暗淡的光线；戈瑞（吸收电离辐射剂量单位，符号 Gy）"
        trimmed = lines("n. （非正式）爸爸，爹爹；（Dad）人名：（孟、巴基、阿富）达德；（泰）达")
        assert [(e["pos"], e["meaning"]) for e in extract_entries("dad", trimmed, True)] == [
            ("n.", "（非正式）爸爸，爹爹")]

    def test_name_run_opening_with_the_headword_walked_back(self):
        # be: the run opens with the headword and untagged fragments; only its
        # last segment carries the mark.
        trimmed = lines("v. 是；有，存在；做，成为；发生",
                        "n. （Be）（缅）拜；（日）部（姓）；（朝）培；（中非）贝（人名）")
        assert [e["pos"] for e in extract_entries("be", trimmed, True)] == ["v."]

    def test_word_with_nothing_but_name_lines_keeps_them(self):
        trimmed = lines("n. （Afford）（英、美）阿福德（人名）")
        assert extract_entries("afford", trimmed, True) == extract_entries("afford", trimmed)

    def test_senses_that_only_talk_about_names_kept(self):
        trimmed = lines("n. （人名前表示地位、职业或婚姻状况的）头衔，称谓；职位名称")
        assert extract_entries("title", trimmed, True) == extract_entries("title", trimmed)
        trimmed = lines("adv. 永远；万岁（用在物或人名后，表示支持的口号）")
        assert extract_entries("forever", trimmed, True) == extract_entries("forever", trimmed)

    def test_capitalised_sense_in_front_of_a_name_run_kept(self):
        trimmed = lines(
            "n. 劳动，工作；劳方，工会组织；（Labor）工党，劳动党；（Labor）人名：（德、塞、法）拉博尔，（英）拉博")
        assert extract_entries("labor", trimmed, True)[0]["meaning"] == \
            "劳动，工作；劳方，工会组织；（Labor）工党，劳动党"

    def test_semicolon_inside_parentheses_does_not_end_a_segment(self):
        # vigor: the mark sits in "（人名；英、法）", and the first sense holds
        # a "；" of its own.
        trimmed = lines(
            "n. 活力，精力（美式拼法；英式常作 vigour）；（文风、表达等的）气势，力度；（人名；英、法） Vigor，维戈尔")
        assert extract_entries("vigor", trimmed, True)[0]["meaning"] == \
            "活力，精力（美式拼法；英式常作 vigour）；（文风、表达等的）气势，力度"

    def test_untagged_name_and_place_entries_dropped(self):
        trimmed = lines("n. 自行车，脚踏车，单车；摩托车", "n. （Bike)（土、土库、塞）比克")
        assert [(e["pos"], e["meaning"]) for e in extract_entries("bike", trimmed, True)] == [
            ("n.", "自行车，脚踏车，单车；摩托车")]
        trimmed = lines("n. 轮胎；轮箍；（Tyre）提尔")
        assert [(e["pos"], e["meaning"]) for e in extract_entries("tyre", trimmed, True)] == [
            ("n.", "轮胎；轮箍")]
        trimmed = lines("adj. 美好的", "n. 尼斯（法国城市，Nice）")
        assert [e["pos"] for e in extract_entries("nice", trimmed, True)] == ["adj."]

    def test_untagged_place_entry_inside_a_sense_dropped(self):
        trimmed = lines("n. 水井；韦尔斯（有矿泉的地方）（Wells）；（大量）来源，源泉")
        assert extract_entries("well", trimmed, True)[0]["meaning"] == "水井；（大量）来源，源泉"

    def test_capitalised_senses_that_are_no_names_kept(self):
        trimmed = lines("n. （Pole）波兰人", "【名】 （Pole）波列（人名）")
        assert [(e["pos"], e["meaning"]) for e in extract_entries("pole", trimmed, True)] == [
            ("n.", "（Pole）波兰人")]
        trimmed = lines("n. （Polish）波兰人；波兰语")
        assert [(e["pos"], e["meaning"]) for e in extract_entries("polish", trimmed, True)] == [
            ("n.", "（Polish）波兰人；波兰语")]


def lines(*sense_lines, phonetic="x"):
    """A trimmed response holding the given sense lines of one word."""
    return {"input": "w", "ec": {"word": [{
        "ukphone": phonetic,
        "trs": [{"tr": [{"l": {"i": list(sense_lines)}}]}],
    }]}}


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

    def test_preset_word_without_entry_fails_gate(self):
        # Owner ruling 2026-11-23: no preset word may ship without a
        # definition, so a syllabus word missing from the dictionary is an
        # error, not a report line.
        entries = [make_entry("a")]
        errors, report = validate_entries(
            entries, preset_books=[("cet4_2016", {"a", "not crawled"})])
        assert any("cet4_2016" in e and "'not crawled'" in e
                   and "no dictionary entry" in e for e in errors), errors
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


class TestApplyPresetAliases:
    def test_emits_lookup_entries_under_the_syllabus_spelling(self):
        entries = [
            make_entry("baby boom", meaning="婴儿潮",
                       phonetic="/ˈbeɪbibuːm/", phonetic_us="/ˈbeɪbibuːm/"),
            make_entry("baby boom", pos="adj.", meaning="婴儿潮的"),
        ]
        aliased, errors = apply_preset_aliases(entries)
        assert errors == []
        assert {(e["word"], e["pos"], e["meaning"]) for e in aliased} == {
            ("baby boom", "n.", "婴儿潮"),
            ("baby boom", "adj.", "婴儿潮的"),
            ("babyboom", "n.", "婴儿潮"),
            ("babyboom", "adj.", "婴儿潮的"),
        }
        copied = [e for e in aliased if e["word"] == "babyboom" and e["pos"] == "n."]
        assert copied[0]["phonetic"] == "/ˈbeɪbibuːm/"
        assert copied[0]["phonetic_us"] == "/ˈbeɪbibuːm/"

    def test_keeps_the_spelling_own_entry_when_it_has_one(self):
        entries = [make_entry("babyboom", meaning="自有释义"), make_entry("baby boom")]
        aliased, errors = apply_preset_aliases(entries)
        assert errors == []
        assert [e["meaning"] for e in aliased if e["word"] == "babyboom"] == ["自有释义"]

    def test_lookup_word_missing_from_the_crawl_is_an_error(self):
        _, errors = apply_preset_aliases([make_entry("a")])
        assert any("babyboom" in e and "baby boom" in e for e in errors), errors

    def test_alias_table_locked(self):
        # Each pair must be verified against live Youdao (the syllabus spelling
        # absent, the lookup present); growing the table is an owner decision.
        assert PRESET_LOOKUP_ALIASES == {"babyboom": "baby boom"}


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
