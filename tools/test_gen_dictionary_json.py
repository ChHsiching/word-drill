# -*- coding: utf-8 -*-
"""
Ticket #23：[gen_dictionary_json.parse_translation] 的 TDD 单测。

复现并锁死两类已确认污染：
1. POS_PREFIXES 缺 adj. —— `adj.` 开头的行被当作无 POS 续接，并到上一行的 n./vi. 等。
2. 无 POS 前缀的行被续接到上一行 —— 如 `cat` 的 CAT 缩写释义被并到 `vi. 呕吐`。

期望（修复后）：
- `adj. 轻的...` 作为独立 sense，不污染 n.
- `计算机辅助教育...` 与 `[计] 计算机辅助教学...` 这类无 POS / 领域标记行不再并到上一行；
  首行的无 POS 内容降级为 `n.`，非首行的丢弃（宁可少给，不可污染）。
"""

import sys
import os
import pytest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from gen_dictionary_json import parse_translation, normalize_phonetic  # noqa: E402


# ---- Ticket #23 case 1：POS_PREFIXES 缺 adj. ----

def test_adj_prefix_recognized_as_own_sense():
    """`adj.` 行应作为独立 sense，不污染上一行的 n.。"""
    trans = "n. 右派人士\\nadj. 右派的；右翼的"
    senses = parse_translation(trans)
    n_sense = next((m for p, m in senses if p == "n."), "")
    assert "右派" in n_sense, f"n. sense should keep 右派人士: {senses}"
    assert "右翼" not in n_sense, f"adj. content polluted n.: {senses}"
    adj_sense = next((m for p, m in senses if p == "adj."), "")
    assert "右翼" in adj_sense, f"adj. sense missing: {senses}"


def test_adj_and_interj_recognized():
    """adj. 与 interj. 都应作为独立 sense（不是无 POS 续接）。"""
    trans = "interj. 再见\\nn. 告别"
    senses = parse_translation(trans)
    poses = [p for p, _ in senses]
    assert "interj." in poses
    assert "n." in poses


# ---- Ticket #23 case 2：无 POS 行不再续接上一行 ----

def test_cat_no_pos_line_not_continued_to_prev():
    """`cat` 的 CAT 缩写释义不应污染 `vi. 呕吐`。"""
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


# ---- 退化路径：首行无 POS（不能丢词）----

def test_first_line_no_pos_degraded_to_noun():
    """整条 translation 首行无 POS 时降级为 n.（不丢词）。"""
    senses = parse_translation("苹果, 果树")
    assert senses == [("n.", "苹果, 果树")], f"got {senses}"


# ---- 已有契约不应破坏 ----

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
