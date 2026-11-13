# -*- coding: utf-8 -*-
"""
TDD tests for gen_dictionary_json.parse_translation, covering Ticket #23.

Reproduces and locks two confirmed contamination sources:
1. POS_PREFIXES lacked adj., so lines starting with `adj.` were treated as
   POS-less continuations and merged into the previous n./vi. line.
2. Lines without a POS prefix were continued onto the previous line. For
   example, the CAT abbreviation gloss for `cat` merged into `vi. 呕吐`.

Expected behavior after the fix:
- `adj. 轻的...` becomes its own sense and does not pollute n.
- Lines with no POS or with domain labels, such as `计算机辅助教育...` or
  `[计] 计算机辅助教学...`, no longer merge into the previous line. A POS-less
  first line degrades to `n.`; later lines are dropped.
"""

import sys
import os
import pytest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from gen_dictionary_json import parse_translation, normalize_phonetic  # noqa: E402


# ---- Ticket #23 case 1: POS_PREFIXES lacked adj. ----

def test_adj_prefix_recognized_as_own_sense():
    """A line starting with `adj.` becomes its own sense and does not pollute the previous n. line."""
    trans = "n. 右派人士\\nadj. 右派的；右翼的"
    senses = parse_translation(trans)
    n_sense = next((m for p, m in senses if p == "n."), "")
    assert "右派" in n_sense, f"n. sense should keep 右派人士: {senses}"
    assert "右翼" not in n_sense, f"adj. content polluted n.: {senses}"
    adj_sense = next((m for p, m in senses if p == "adj."), "")
    assert "右翼" in adj_sense, f"adj. sense missing: {senses}"


def test_adj_and_interj_recognized():
    """Both adj. and interj. become their own senses, not POS-less continuations."""
    trans = "interj. 再见\\nn. 告别"
    senses = parse_translation(trans)
    poses = [p for p, _ in senses]
    assert "interj." in poses
    assert "n." in poses


# ---- Ticket #23 case 2: POS-less lines no longer continue the previous line ----

def test_cat_no_pos_line_not_continued_to_prev():
    """The CAT abbreviation gloss for `cat` must not pollute `vi. 呕吐`."""
    trans = (
        "n. 猫, 恶妇\\n"
        "vi. 呕吐\\n"
        "计算机辅助教育, 计算机辅助测试\\n"
        "[计] 计算机辅助教学, 计算机辅助排字"
    )
    senses = parse_translation(trans)
    vi_sense = next((m for p, m in senses if p == "vi."), "")
    assert "计算机辅助" not in vi_sense, f"vi. polluted: {vi_sense}"
    n_sense = next((m for p, m in senses if p == "n."), "")
    assert "计算机辅助" not in n_sense, f"n. polluted by CAT: {n_sense}"


def test_aim_no_pos_line_not_continued():
    trans = (
        "n. 目标, 瞄准\\n"
        "vi. 对准目标\\n"
        "vt. 瞄准\\n"
        "[计] 医学文摘索引, 存取隔离机构\\n"
        "应用接口模块, 医学人工智能"
    )
    senses = parse_translation(trans)
    for p, m in senses:
        assert "医学文摘" not in m, f"{p} polluted by AIM abbr: {m}"
        assert "应用接口模块" not in m, f"{p} polluted by AIM abbr: {m}"


# ---- Fallback path: first line without POS, must not drop the word ----

def test_first_line_no_pos_degraded_to_noun():
    """When the first line has no POS, the translation degrades to n. and the word survives."""
    senses = parse_translation("苹果, 果树")
    assert senses == [("n.", "苹果, 果树")], f"got {senses}"


# ---- Existing contracts must not break ----

def test_basic_multi_pos():
    trans = "n. 苹果\\nvt. 喜欢"
    senses = parse_translation(trans)
    assert senses == [("n.", "苹果"), ("vt.", "喜欢")]


def test_empty_returns_empty():
    assert parse_translation("") == []
    assert parse_translation(None) == []


def test_normalize_phonetic_basic():
    assert normalize_phonetic("'bændәn") == "/ˈbændən/"
    assert normalize_phonetic("") is None
    assert normalize_phonetic(None) is None


if __name__ == "__main__":
    sys.exit(pytest.main([__file__, "-v"]))
