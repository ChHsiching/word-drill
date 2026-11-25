#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Validate the Youdao crawl output and emit the app-ready dictionary JSON.

Reads the crawler's raw cache (raw.jsonl in the state directory), turns each
trimmed response into dictionary entries, validates them, and writes the same
shape the app consumes today — one entry per sense line — plus the new US
phonetic alongside the existing (UK) phonetic:

  {
    "source": "Youdao (dict.youdao.com web API, crawled YYYY-MM-DD)",
    "words": [
      { "word": "handover", "phonetic": "/ˈhændəʊvə(r)/",
        "phonetic_us": "/ˈhændoʊvər/", "pos": "n.",
        "meaning": "移交，交接；【电信】切换，越区切换" },
      ...
    ]
  }

Validation rules (issue #49, ADR 0003): every entry has a word and at least
one sense; no duplicate (word, pos) pairs (the Room import dedupes on that
key); meaning punctuation normalized (normalize_meaning is idempotent on its
own output). Hard gate: EVERY word of the three preset-book word lists
(tools/data/) has a dictionary entry, and each entry carries a phonetic (UK or
US). Preset words Youdao genuinely gives no phonetic for may pass only via
PHONETIC_GATE_EXCEPTIONS. A syllabus spelling Youdao does not list under that
exact form is covered by PRESET_LOOKUP_ALIASES (the lookup word's entries are
also emitted under the syllabus spelling). Non-preset words Youdao has no
entry for are excluded from the output. Overall phonetic coverage is reported,
not gated. Missing phonetic
variants are gap-filled from English Wiktionary IPA (tools/
wiktionary_phonetic_fill.py, per the amended ADR 0003): Youdao values always
win, and the build reports the fill counts.

Youdao marks subject labels and ordinary qualifiers the same way, with
full-width parens or angle brackets. Examples are （电信）切换, <史>毁船打劫
and 理解（力）. Notation alone cannot tell them apart. A marker becomes a 【】
label (the app data's form, ADR 0001) only when it opens a sense segment and
every ／ ， 、-separated part is in DOMAIN_LABELS. Everything else stays a
parenthetical, for example （因危险）, <美> or 理解（力）. Other punctuation is
unified. Half-width pairs in Chinese text, every ellipsis spelling and
stray spaces are normalized. Youdao's person-name line becomes an entry of
its own (pos ""). For the words of the three preset books the person-name
entries are dropped instead (owner decision 2026-11-25): the line of its own
and the entry Youdao appends to a sense, plus the place names listed with them.
Other words keep theirs.

Usage:
  python tools/build_youdao_dictionary.py [--state-dir DIR] [--out FILE]
      [--source-run-date YYYY-MM-DD]

Exit 0 with the file written when validation passes; exit 1 with the error
list otherwise. The default output stays OUTSIDE git (.scratch); pass
--out app/src/main/assets/dictionary.json when replacing the bundled asset
after the full crawl.
"""

import argparse
import datetime
import json
import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from youdao_crawler import DEFAULT_STATE_DIR, load_raw  # noqa: E402

# Subject-domain labels. Youdao marks them with full-width parens or angle
# brackets, as in （电信）, <史> and 〈史〉. It marks ordinary qualifiers the
# same way, as in "理解（力）" and "使（计算机）联网", so notation alone cannot
# tell a label from a qualifier. A marker becomes a 【】 label only when it
# opens a sense segment. That is the position right after the POS or a "；",
# and a register marker such as <非正式> may sit in front. Every ／ ，
# 、-separated part must be listed here too. The inventory was closed by
# reading every preset-word line (2026-11-23). "物" is deliberately absent,
# because "（物）" opens segments meaning "of a thing" (live, slim, unemployed)
# as often as "physics". "（物理）" is the unambiguous spelling.
DOMAIN_LABELS = {
    "电信", "无线电", "计算机", "电子技术", "电子学", "网络", "互联网", "通信",
    "信息科学", "技术",
    "计", "电", "机", "数", "数学", "医", "医学", "医疗", "外科", "律", "法律",
    "经", "经济", "财政", "金融", "贸易", "会计", "化", "化学", "生", "生物",
    "生物学", "生化", "生物化学", "分子生物", "遗传", "遗传学", "农学", "军",
    "军事", "航", "航海", "航空", "海", "气", "气象", "天", "天文", "地质",
    "地理", "考古", "矿", "农", "农业", "药", "印", "印刷", "纺", "冶",
    "光", "光学", "力", "测", "测绘", "体育", "音乐", "棋", "语法",
    "宗教", "宗教学", "神学", "圣经", "铁路", "铁道", "交通", "汽车", "古生",
    "史", "乐", "哲", "哲学", "逻", "逻辑", "解剖", "剖", "统计",
    "心理", "心理学", "心理分析", "生理", "生理学", "动", "动物学", "植",
    "植物学", "昆", "占星", "建筑", "建", "电子", "机械",
    "机械学", "摄影", "摄", "教育", "艺术", "语言", "语言学", "语音", "语音学",
    "词汇", "词形", "伦理学", "修辞", "免疫", "外交", "生态", "进化", "分化",
    "技", "渔", "猎", "诗", "诗韵", "鸣钟术", "纹章", "几何", "物理",
}

POS_PREFIXES = (
    "prep.", "pron.", "conj.", "interj.", "adv.", "num.", "art.", "aux.",
    "int.", "abbr.", "adj.", "det.", "vt.", "vi.", "n.", "v.", "a.",
    "comb.", "pref.",
)
# Spellings Youdao also uses for a POS above, and its one "cn." typo
# (friendly "cn. 友谊赛"), mapped onto that POS.
POS_ALIASES = {"inter.": "int.", "pronoun.": "pron.", "cn.": "n."}
POS_RE = re.compile(r"^([A-Za-z]{1,8})[．.]")
_KNOWN_POS = frozenset(POS_PREFIXES)

# CJK ideographs + CJK punctuation/full-width forms, for adjacency tests.
CJK_RE = r"[\u3400-\u4dbf\u4e00-\u9fff\uf900-\ufaff\u3000-\u303f\uff00-\uffef]"
CJK_CHAR_RE = re.compile(r"[\u3400-\u4dbf\u4e00-\u9fff\uf900-\ufaff]")

HALF_TO_FULL = {",": "，", ";": "；", ":": "：", "?": "？", "!": "！"}

# Markers that may open a sense segment: <美>, 〈史〉, （非正式）, [古生]
# (grouped: angle-bracket body, paren body, square-bracket body).
SEGMENT_MARKER_RE = re.compile(
    r"^(?:[<〈]([^<>〈〉]{1,18})[>〉]|（([^（）]+)）|\[([^\[\]]+)\])")
# Any remaining <美>, 〈英，旧〉, <澳/新，非正式> marker inside a segment.
# The body must start with a Chinese character, so "x <y> z" is no marker.
ANGLE_MARKER_RE = re.compile(r"[<〈]([\u4e00-\u9fff][^<>〈〉]{0,17})[>〉]")
HALF_PAIR_RE = re.compile(r"\(([^()]*)\)")
SQUARE_PAIR_RE = re.compile(r"\[([^\[\]]+)\]")
# Every ellipsis spelling Youdao uses: "......", "…...", "…", "······".
ELLIPSIS_RUN_RE = re.compile(r"[.…·]{2,}")
LONE_ELLIPSIS_RE = re.compile(r"(?<![.…·])…(?![.…·])")
NAME_TAG_RE = re.compile(r"(【名】)\1+")

# ADR 0003 hard gate: the three preset-book word lists, files in tools/data/.
PRESET_BOOK_FILES = ("cet4_2016.txt", "cet6_2016.txt", "kaoyan.txt")

# Preset-book words whose Youdao entry genuinely carries no phonetic (no
# ukphone AND no usphone, verified against live Youdao on 2026-11-20:
# multi-word phrase entries). The gate lets exactly these through; anything
# else failing it is an error. Growing this list is an owner decision.
PHONETIC_GATE_EXCEPTIONS = ("according to", "owing to")

# Syllabus spellings Youdao does not list under that exact form. The official
# CET list prints "babyboom" closed up; Youdao only has "baby boom". The lookup
# word's entries are also emitted under the syllabus spelling, so every preset
# word has a definition (owner ruling 2026-11-23). The content stays 100%
# Youdao. Each pair must be verified against live Youdao (the syllabus spelling
# absent, the lookup present); growing this table is an owner decision.
PRESET_LOOKUP_ALIASES = {"babyboom": "baby boom"}


def split_pos(line):
    """Split a Youdao sense line into (pos, meaning); pos is "" when absent.
    The POS is matched case-insensitively, behind a full-width dot as well
    ("n．"), and through POS_ALIASES."""
    m = POS_RE.match(line)
    if m:
        pos = m.group(1).lower() + "."
        pos = POS_ALIASES.get(pos, pos)
        if pos in _KNOWN_POS:
            return pos, line[m.end():].strip()
    return "", line


def _is_domain_label(content):
    parts = [p.strip() for p in re.split(r"[／/，,、]", content)]
    return bool(parts) and all(p in DOMAIN_LABELS for p in parts)


def _widen_mismatched_paren_pairs(text):
    """Give a mismatched paren pair one width: Youdao typos seen on live data
    are "(未经证实地）宣称" and "（Bike)比克". Only a pair whose opener and
    closer differ is touched (both become full-width); balanced pairs and
    stray closers stay exactly as sourced."""
    chars = list(text)
    openers = []
    for i, ch in enumerate(chars):
        if ch in "（(":
            openers.append(i)
        elif ch in "）)" and openers:
            opener = openers.pop()
            if (chars[opener] == "（") != (ch == "）"):
                chars[opener] = "（"
                chars[i] = "）"
    return "".join(chars)


def _convert_segment_start(segment):
    """Convert the markers that open one sense segment. They may stack, as in
    "<非正式>（医）刮宫". A subject label becomes a 【】 label. Any other
    angle-bracket marker becomes （） with inner spaces dropped. A non-label
    [ ] holding Chinese becomes （）. An ordinary （） marker is kept as
    sourced and peeling goes on, since a label may follow one."""
    out = []
    rest = segment.lstrip()
    while True:
        m = SEGMENT_MARKER_RE.match(rest)
        if not m:
            break
        angle, paren, square = m.groups()
        if angle is not None and not CJK_CHAR_RE.search(angle):
            break  # "<y>" is no marker
        body = angle if angle is not None else (
            paren if paren is not None else square)
        if _is_domain_label(body):
            out.append(f"【{body}】")
        elif angle is not None:
            out.append("（" + body.replace(" ", "") + "）")
        elif square is not None and CJK_CHAR_RE.search(body):
            out.append(f"（{body}）")
        else:
            out.append(m.group(0))
        rest = rest[m.end():]
    return "".join(out) + rest


def _in_chinese_context(text, start, end, inner):
    """True when a pair sits in Chinese text: Chinese inside it, or the
    nearest non-space character on either side is Chinese or full-width.
    "call (sb) up" has neither and keeps its half-width parens."""
    if CJK_CHAR_RE.search(inner):
        return True
    before = text[:start].rstrip()[-1:]
    after = text[end:].lstrip()[:1]
    return bool((before and re.match(CJK_RE, before)) or
                (after and re.match(CJK_RE, after)))


def _widen_half_width_pairs(text):
    """A balanced half-width ( ) pair in Chinese context gets the full width.
    Youdao itself writes such pairs full-width, for example （=night owl）.
    _widen_mismatched_paren_pairs has already widened the mismatched ones."""
    def widen(m):
        if _in_chinese_context(text, m.start(), m.end(), m.group(1)):
            return f"（{m.group(1)}）"
        return m.group(0)
    return HALF_PAIR_RE.sub(widen, text)


def _unify_ellipsis(text):
    """Every ellipsis spelling -> "……". A run of dots is one only when it
    holds a "…" or a "·", or touches Chinese text, so "Sep." stays "Sep."."""
    def widen(m):
        run = m.group(0)
        if "…" in run or "·" in run or _in_chinese_context(
                text, m.start(), m.end(), run):
            return "……"
        return run
    text = ELLIPSIS_RUN_RE.sub(widen, text)
    return LONE_ELLIPSIS_RE.sub(
        lambda m: "……" if _in_chinese_context(text, m.start(), m.end(), "…")
        else m.group(0), text)


def normalize_meaning(text):
    """Normalize punctuation in a Chinese sense text:
    1. collapse whitespace runs to one space and strip;
    2. widen a paren pair whose opener and closer have different widths;
    3. half-width , ; : ? ! -> full-width when adjacent to CJK;
    4. the markers opening each "；"-separated segment. A subject label
       (<史>, （电信）, [古生]) becomes 【史】 or 【电信】. Any other
       angle-bracket marker becomes （美）. The step runs after the comma
       rule, since a marker may hold a half-width comma, as in "<美,非正式>");
    5. any remaining <美> / 〈英，旧〉 marker -> （美）;
    6. a half-width ( ) pair in Chinese context -> （ ）, a [ ] pair holding
       Chinese -> （ ）;
    7. every ellipsis spelling (......  …...  …  ······) -> ……;
    8. strip spaces hugging full-width punctuation and closing brackets, and
       spaces sandwiched between Chinese characters ("航空站 （港）");
    9. collapse a doubled name tag, "【名】【名】" -> "【名】".
    Idempotent on its own output.
    """
    text = re.sub(r"\s+", " ", text).strip()
    text = _widen_mismatched_paren_pairs(text)
    for half, full in HALF_TO_FULL.items():
        text = re.sub(f"(?<={CJK_RE}){re.escape(half)}", full, text)
        text = re.sub(f"{re.escape(half)}(?={CJK_RE})", full, text)
    text = "；".join(_convert_segment_start(seg) for seg in text.split("；"))
    text = ANGLE_MARKER_RE.sub(
        lambda m: "（" + m.group(1).replace(" ", "") + "）", text)
    text = _widen_half_width_pairs(text)
    text = SQUARE_PAIR_RE.sub(
        lambda m: f"（{m.group(1)}）" if CJK_CHAR_RE.search(m.group(1))
        else m.group(0), text)
    text = _unify_ellipsis(text)
    # No space hugs full-width separators, labels, or brackets:
    # "苹果， 梨" -> "苹果，梨"; "（非正式） （尤指…）" -> "（非正式）（尤指…）".
    text = re.sub(r" +([，；：？！、】）])", r"\1", text)
    text = re.sub(r"([，；：？！、】）]) +", r"\1", text)
    text = re.sub(f"(?<={CJK_RE}) +(?={CJK_RE})", "", text)
    return NAME_TAG_RE.sub(r"\1", text)


def wrap_phonetic(raw):
    """IPA wrapped in slashes with inner whitespace removed; None for empty.
    Youdao separates pronunciation variants with a bare ";" — give it a space."""
    if not raw:
        return None
    cleaned = re.sub(r";(?=\S)", "; ", re.sub(r"\s+", "", raw))
    return "/" + cleaned + "/" if cleaned else None


def parse_ec(ec):
    """(uk_phone, us_phone, sense_lines) from a trimmed ec subtree."""
    if not ec:
        return None, None, []
    words = ec.get("word") or []
    if isinstance(words, dict):
        words = [words]
    uk = us = None
    lines = []
    for w in words:
        if not isinstance(w, dict):
            continue
        if uk is None and w.get("ukphone"):
            uk = w["ukphone"]
        if us is None and w.get("usphone"):
            us = w["usphone"]
        for tr_group in w.get("trs") or []:
            for tr in tr_group.get("tr") or []:
                items = (tr.get("l") or {}).get("i") or []
                if isinstance(items, str):
                    items = [items]
                for item in items:
                    if isinstance(item, str) and item.strip():
                        lines.append(item.strip())
    return uk, us, lines


# Grouping key for Youdao's person-name line, so it never merges with the
# senses of any POS (nor with a "comb." line that once shared its empty POS).
NAME_LINE = object()
NULL_PREFIX_RE = re.compile(r"^null\s+")


def _is_name_line(pos, content):
    """Youdao's person-name line: tagged 【名】, whatever POS prefix it
    carries, or an untagged POS-less line that names somebody ("【英】
    （Dash）…（人名）")."""
    return content.startswith("【名】") or (pos == "" and "人名" in content)


# Person-name entries are dropped for the words of the three preset books (owner
# decision 2026-11-25). Youdao's names dictionary transliterates any spelling
# that exists as a surname, so an ordinary word gets "【名】（Abuse）（英）阿比斯
# （人名）". Besides the line of its own (_is_name_line), the entry can be a run of
# segments Youdao appends to a sense: "蚂蚁；Ant（人名，…）", or "灰色；…；（Gray）
# 人名；（意）格拉伊；（英、法、葡、瑞典）格雷".
NAME_MARKS = ("人名", "姓氏")
PAREN_GROUP_RE = re.compile(r"[（(]([^（）()]*)[）)]")
# A tag group opens with the mark and nothing follows it but a separator,
# as in （人名，土耳其、芬兰人名译作“安特”）. "（人名前表示地位、职业或婚姻状况的）
# 头衔" is a usage note and does not match.
_TAG_GROUP_RE = re.compile(r"^(?:人名|姓氏)(?=$|[，；：、\s])")
# The run may open with the headword itself in parentheses, "（Be）（缅）拜".
# A real sense keeps its punctuation or its length: "（Labor）工党，劳动党".
_NAME_RUN_OPEN_RE = re.compile(
    r"^[（(][^（）()]{1,40}[）)](?:[（(][^（）()]{1,12}[）)])*[\u4e00-\u9fff·]{1,6}$")
# Fragments between the opening and the first tagged segment: a short label and
# at most two characters, as in "（日）部（姓）" and "（朝）培".
_NAME_RUN_STEP_RE = re.compile(
    r"^(?:[（(][^（）()]{1,8}[）)][\u4e00-\u9fff·]{0,2})+(?:[（(][^（）()]{1,8}[）)])?$")
# Name entries without any of these marks, one per preset word: a person name
# and the place names found with it, each spelled into an ordinary sense. No
# rule tells them from real senses such as （Pole）波兰人, so they are listed as
# the text reads after normalization (from the cleaned senses of every preset
# word, 2026-11-25).
UNTAGGED_NAME_ENTRIES = {
    "bike": "（Bike）（土、土库、塞）比克",
    "highland": "海兰（苏格兰北部行政区）（Highland）",
    "nice": "尼斯（法国城市，Nice）",
    "plantation": "普兰泰申市（美国佛罗里达州城市名）",
    "sunrise": "（Sunrise）森赖斯（弗罗里达州一城市）",
    "thorn": "（Thorn）托伦（波兰城市，Toruń 的德语名）",
    "tyre": "（Tyre）提尔",
    "well": "韦尔斯（有矿泉的地方）（Wells）",
}


def _is_name_segment(segment):
    """One "；"-separated segment of a sense that is a person-name entry."""
    groups = PAREN_GROUP_RE.findall(segment)
    latin_name = any(re.search(r"[A-Z]", g) for g in groups)
    for g in groups:
        if g in NAME_MARKS or _TAG_GROUP_RE.match(g):
            return True
        if latin_name and g.endswith(NAME_MARKS):
            return True
    if re.search(r"[）)](?:人名|姓氏)", segment):
        return True
    if re.search(r"[A-Z][A-Za-z]* 用作(?:人名|姓氏|绰号)", segment):
        return True
    if re.match(r"^[A-Z][A-Za-z]+[（(][^（）()]*(?:人名|姓氏)", segment):
        return True
    return bool(latin_name and re.search(r"(?:人名|姓氏)或(?:人名|姓氏)", segment))


def _split_sense_segments(meaning):
    """The "；"-separated segments of a sense. A "；" inside a parenthesis pair
    stays where it is, as in （美式拼法；英式常作 vigour）."""
    segments = [""]
    depth = 0
    for char in meaning:
        if char == "；" and not depth:
            segments.append("")
        else:
            segments[-1] += char
        if char in "（(":
            depth += 1
        elif char in "）)" and depth:
            depth -= 1
    return segments


def _strip_name_run(meaning, word):
    """The sense without the name entries, on a normalized meaning: the word's
    untagged entry wherever it sits, and everything from the first name segment
    on. The person-name entry may open a few segments earlier, with the headword
    in parentheses and no tag of its own, so short fragments are walked back to
    such an opening."""
    segments = _split_sense_segments(meaning)
    untagged = UNTAGGED_NAME_ENTRIES.get(word)
    if untagged:
        segments = [seg for seg in segments if seg != untagged]
        if not segments:
            return ""
    start = next((i for i, seg in enumerate(segments) if _is_name_segment(seg)), None)
    if start is None:
        return "；".join(segments)
    back = start
    while back > 0 and _NAME_RUN_STEP_RE.fullmatch(segments[back - 1]):
        back -= 1
    for i in range(start - 1, back - 1, -1):
        first = PAREN_GROUP_RE.match(segments[i])
        if first and first.group(1).lower() == word.lower() and \
                _NAME_RUN_OPEN_RE.match(segments[i]):
            start = i
            break
    return "；".join(segments[:start])


def _entries_of(word, parsed, uk, us, drop_names):
    """The output entries of one word's (pos, content) sense lines."""
    # The name line gets its own entry unless the word also has a POS-less
    # sense line. (word, pos) must stay unique, so the two then share one
    # entry in source order. 51 crawled proper nouns, e.g. Ortega, have both.
    kept = [(pos, content) for pos, content in parsed
            if not (drop_names and _is_name_line(pos, content))]
    own_name_entry = not any(
        pos == "" and not _is_name_line(pos, content) for pos, content in kept)
    by_pos = {}
    seen = {}
    for pos, content in kept:
        meaning = normalize_meaning(content)
        if drop_names:
            meaning = _strip_name_run(meaning, word)
        if not meaning:
            continue
        key = NAME_LINE if (_is_name_line(pos, content) and own_name_entry) else pos
        segments = by_pos.setdefault(key, [])
        keys = seen.setdefault(key, set())
        for segment in meaning.split("；"):
            segment = segment.strip()
            dedup_key = segment.removeprefix("【名】")
            if segment and segment != "null" and dedup_key not in keys:
                segments.append(segment)
                keys.add(dedup_key)
    return [
        {
            "word": word,
            "phonetic": wrap_phonetic(uk),
            "phonetic_us": wrap_phonetic(us),
            "pos": "" if pos is NAME_LINE else pos,
            # Re-normalized as a whole: two sense lines can hold the halves of
            # one paren pair, which only meet after they are joined.
            "meaning": normalize_meaning("；".join(segments)),
        }
        for pos, segments in by_pos.items()
    ]


def extract_entries(word, trimmed, drop_names=False):
    """Output entries for one word. Youdao may repeat a POS across sense lines
    ("v. 疏通", "v. 使通畅"); the Room import dedupes on (word, pos), so
    same-POS senses merge into one entry joined by "；". A sense segment that
    repeats an earlier one (inside one line or across lines) is kept once.
    The comparison ignores the 【名】 tag, since Youdao repeats name lines
    with and without it. The person-name line becomes an entry of its
    own (pos ""). Youdao's literal "null" placeholder and a line-final "。"
    (generated prose) are dropped, so every entry has a non-empty meaning.

    drop_names removes the name entries, for the words of the preset books: the
    person-name ones and the place names listed with them. A word whose every
    line is a name entry keeps them, so no word is lost."""
    uk, us, lines = parse_ec(trimmed.get("ec"))
    parsed = [split_pos(NULL_PREFIX_RE.sub("", line).rstrip("。")) for line in lines]
    entries = _entries_of(word, parsed, uk, us, drop_names)
    if not entries and drop_names:
        entries = _entries_of(word, parsed, uk, us, False)
    return entries


def load_preset_books(data_dir=None):
    """[(file stem, {words})] for the three preset-book reference lists in
    tools/data/ — the ADR 0003 hard gate's scope."""
    data_dir = data_dir or os.path.join(
        os.path.dirname(os.path.abspath(__file__)), "data")
    books = []
    for ref_file in PRESET_BOOK_FILES:
        with open(os.path.join(data_dir, ref_file), encoding="utf-8") as f:
            stem = os.path.splitext(ref_file)[0]
            books.append((stem, {w for w in f.read().splitlines() if w}))
    return books


def validate_entries(entries, preset_books=()):
    """Return (errors, report). errors is a list of strings; report carries
    the counts printed by the CLI.

    ADR 0003 hard gate: every preset-book word has a dictionary entry, and
    each entry carries a phonetic (UK or US); words in PHONETIC_GATE_EXCEPTIONS
    pass without one. Overall phonetic coverage is reported, not gated.
    """
    errors = []
    seen_pairs = set()
    words = set()
    words_with_uk = set()
    words_with_us = set()
    for e in entries:
        if not e["word"] or not e["meaning"]:
            errors.append(f"entry without sense: {e!r}")
            continue
        pair = (e["word"], e["pos"])
        if pair in seen_pairs:
            errors.append(f"duplicate (word, pos): {pair}")
        seen_pairs.add(pair)
        if normalize_meaning(e["meaning"]) != e["meaning"]:
            errors.append(f"punctuation not normalized: {e['word']} {e['meaning']!r}")
        words.add(e["word"])
        if e["phonetic"]:
            words_with_uk.add(e["word"])
        if e["phonetic_us"]:
            words_with_us.add(e["word"])

    words_phonetic = words_with_uk | words_with_us
    coverage = len(words_phonetic) / len(words) if words else 1.0
    report = {
        "entries": len(entries),
        "words": len(words),
        "phonetic_coverage": coverage,
        "uk_coverage": len(words_with_uk) / len(words) if words else 1.0,
        "us_coverage": len(words_with_us) / len(words) if words else 1.0,
        "preset_books": {},
    }
    for name, preset in preset_books:
        with_entry = preset & words
        no_phonetic = sorted(with_entry - words_phonetic)
        excepted = [w for w in no_phonetic if w in PHONETIC_GATE_EXCEPTIONS]
        report["preset_books"][name] = {
            "with_entry": len(with_entry),
            "with_phonetic": len(with_entry) - len(no_phonetic),
            "both": len(with_entry & words_with_uk & words_with_us),
            "uk_only": len((with_entry & words_with_uk) - words_with_us),
            "us_only": len((with_entry & words_with_us) - words_with_uk),
            "no_phonetic": [w for w in no_phonetic
                            if w not in PHONETIC_GATE_EXCEPTIONS],
            "excepted": excepted,
            "without_entry": sorted(preset - words),
        }
        for w in report["preset_books"][name]["no_phonetic"]:
            errors.append(f"{name}: preset word {w!r} has an entry but no phonetic")
        for w in report["preset_books"][name]["without_entry"]:
            errors.append(f"{name}: preset word {w!r} has no dictionary entry")
    return errors, report


def apply_phonetic_fill(entries, fill):
    """Fill missing phonetic variants from the Wiktionary gap-fill (ADR 0003,
    owner decision 2026-11-20). Youdao values always win; a variant stays
    None when neither source has it. Returns (entries, stats) with unique
    word counts: gap_words (missing a variant before the fill), filled_uk,
    filled_us, still_missing (still missing a variant after the fill)."""
    before = {}
    for e in entries:
        seen = before.setdefault(e["word"], [False, False])
        seen[0] = seen[0] or e["phonetic"] is not None
        seen[1] = seen[1] or e["phonetic_us"] is not None
    gap_words = {w for w, had in before.items() if not (had[0] and had[1])}
    filled_uk = set()
    filled_us = set()
    for e in entries:
        f = fill.get(e["word"])
        if not f:
            continue
        if e["phonetic"] is None and f.get("uk"):
            e["phonetic"] = f["uk"]
            filled_uk.add(e["word"])
        if e["phonetic_us"] is None and f.get("us"):
            e["phonetic_us"] = f["us"]
            filled_us.add(e["word"])
    after = {}
    for e in entries:
        seen = after.setdefault(e["word"], [False, False])
        seen[0] = seen[0] or e["phonetic"] is not None
        seen[1] = seen[1] or e["phonetic_us"] is not None
    still_missing = {w for w in gap_words if not (after[w][0] and after[w][1])}
    return entries, {
        "gap_words": len(gap_words),
        "filled_uk": len(filled_uk),
        "filled_us": len(filled_us),
        "still_missing": len(still_missing),
    }


def apply_preset_aliases(entries):
    """Emit the lookup word's entries under a syllabus spelling Youdao does not
    use itself (PRESET_LOOKUP_ALIASES). A spelling that already has its own
    entry is left alone. Returns (entries, errors); an alias whose lookup word
    is missing from the crawl is reported, so it cannot pass silently."""
    words = {e["word"] for e in entries}
    errors = []
    added = []
    for spelling, lookup in PRESET_LOOKUP_ALIASES.items():
        if spelling in words:
            continue
        lookup_entries = [e for e in entries if e["word"] == lookup]
        if not lookup_entries:
            errors.append(
                f"alias {spelling!r} -> {lookup!r}: the lookup word has no crawl entry")
            continue
        added.extend({**e, "word": spelling} for e in lookup_entries)
    return entries + added, errors


def build_output(entries, source):
    return {"source": source, "words": entries}


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[1])
    parser.add_argument("--state-dir", default=DEFAULT_STATE_DIR,
                        help="crawler state dir containing raw.jsonl")
    parser.add_argument("--out", default=None,
                        help="output JSON path (default: <state-dir>/dictionary.youdao.json)")
    parser.add_argument("--source-run-date", default=datetime.date.today().isoformat(),
                        help="crawl date recorded in the source string")
    parser.add_argument("--fill-file", default=None,
                        help="Wiktionary phonetic gap-fill JSONL (default: "
                             "<state-dir>/wiktionary_fill.jsonl when present)")
    parser.add_argument("--allow-validation-errors", action="store_true",
                        help="write the output and exit 0 even when validation fails "
                             "(mid-crawl CI chunks; the final full build runs strict)")
    args = parser.parse_args(argv)

    raw = load_raw(os.path.join(args.state_dir, "raw.jsonl"))
    if not raw:
        sys.exit(f"ERROR: no crawl cache at {args.state_dir}/raw.jsonl")

    # The preset books' words lose their person-name entries. A lookup alias
    # contributes its entries to a preset spelling, so it counts too.
    preset_words = set(PRESET_LOOKUP_ALIASES.values())
    for _, words in load_preset_books():
        preset_words |= words

    entries = []
    misses = 0
    for word in sorted(raw):
        word_entries = extract_entries(word, raw[word], drop_names=word in preset_words)
        if not word_entries:
            misses += 1
        entries.extend(word_entries)

    fill_path = args.fill_file or os.path.join(
        args.state_dir, "wiktionary_fill.jsonl")
    if os.path.exists(fill_path):
        from wiktionary_phonetic_fill import load_fill  # deferred: avoids import cycle
        fill = load_fill(fill_path)
    else:
        fill = {}
    entries, fill_stats = apply_phonetic_fill(entries, fill)
    entries, alias_errors = apply_preset_aliases(entries)

    errors, report = validate_entries(entries, load_preset_books())
    errors = alias_errors + errors
    print(f"raw words: {len(raw)} (no entries: {misses})")
    print(f"entries: {report['entries']} over {report['words']} words")
    print(f"phonetic coverage: {report['phonetic_coverage']:.1%} "
          f"(uk {report['uk_coverage']:.1%}, us {report['us_coverage']:.1%})")
    print(f"wiktionary fill: {fill_stats['gap_words']} gap words, filled "
          f"uk {fill_stats['filled_uk']} / us {fill_stats['filled_us']}, "
          f"still missing a variant {fill_stats['still_missing']}")
    for name, stats in report["preset_books"].items():
        line = (f"{name}: {stats['with_phonetic']}/{stats['with_entry']} entry "
                f"words with phonetic (both {stats['both']}, uk-only "
                f"{stats['uk_only']}, us-only {stats['us_only']})")
        if stats["excepted"]:
            line += f"; excepted no-phonetic: {', '.join(stats['excepted'])}"
        if stats["without_entry"]:
            line += (f"; {len(stats['without_entry'])} without dictionary "
                     f"entry: {', '.join(stats['without_entry'][:30])}")
            if len(stats["without_entry"]) > 30:
                line += f" (+{len(stats['without_entry']) - 30} more)"
        print(line)
    if errors:
        for e in errors[:50]:
            print(f"VALIDATION ERROR: {e}", file=sys.stderr)
        if len(errors) > 50:
            print(f"... and {len(errors) - 50} more", file=sys.stderr)
        if not args.allow_validation_errors:
            return 1

    out_path = args.out or os.path.join(args.state_dir, "dictionary.youdao.json")
    out = build_output(
        entries, f"Youdao (dict.youdao.com web API, crawled {args.source_run_date})")
    with open(out_path, "w", encoding="utf-8") as f:
        json.dump(out, f, ensure_ascii=False, separators=(",", ":"))
    print(f"wrote {out_path} ({os.path.getsize(out_path) // 1024} KB)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
