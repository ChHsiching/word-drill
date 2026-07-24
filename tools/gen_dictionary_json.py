#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
从 ECDICT (MIT, https://github.com/skywind3000/ECDICT) 生成 app/src/main/assets/dictionary.json。

Ticket #19：内置词典。打包约 10 万高频单词（仅单词，不含短语）为只读参考数据，
首启导入到 Room 的 dictionary 表，供加词 / 文件导入时查词性 + 释义 + 音标用。

数据规模：
- ECDICT 完整 csv 约 77 万行，但里面含大量短语、生僻词。
- 这里按词频筛 top 10 万单词（collins desc → bnc asc → frq asc → alpha），覆盖
  日常 / 考试 / 一般阅读词汇。短语（含空格的 word）跳过。
- 已验证 10 万词 compact JSON ≈ 11MB，APK 增大可接受。

输出 JSON schema（与 Room dictionary 表的字段对应）：
  {
    "source": "ECDICT (MIT License, ...)",
    "words": [
      { "word": "apple", "phonetic": "/ˈæpl/", "pos": "n.", "meaning": "苹果" },
      ...
    ]
  }
注意：同一 word 的多个词性 = 多条 entries（POS 切分沿用 gen_words_json.py 的解析逻辑），
便于 DAO findByWord 返回 List<DictionaryEntry> 后直接映射到 sense 表。

音标归一化、POS 解析与 gen_words_json.py 完全一致（复用同一套规则与字符映射），
详见 gen_words_json.py 的模块 docstring。

用法：python tools/gen_dictionary_json.py <path/to/ecdict.csv>
（默认读 C:/Users/Administrator/AppData/Local/Temp/ecdict.csv，开发机缓存路径）
"""

import csv
import json
import os
import re
import sys

# 与 gen_words_json.py 完全一致的 POS 前缀表（行首词性缩写）
POS_PREFIXES = (
    "prep.", "pron.", "conj.", "adv.", "num.", "art.", "aux.", "int.",
    "abbr.", "vt.", "vi.", "n.", "v.", "a.",
)
POS_RE = re.compile(r"^(" + "|".join(re.escape(p) for p in POS_PREFIXES) + r")")

# 与 gen_words_json.py 完全一致的 phonetic 字符映射
PHONETIC_CHARMAP = str.maketrans({
    "'": "\u02C8",
    "^": "\u02C8",
    "\u04D9": "\u0259",
    "\u0454": "\u0259",
})

# Top-N 单词数：10 万。覆盖高频核心词汇；assets 体积约 11MB。
TOP_N = 100000


def normalize_phonetic(raw):
    """与 gen_words_json.py 同实现：包裹斜杠的标准 IPA；空输入返回 None。"""
    if not raw:
        return None
    cleaned = raw.strip().translate(PHONETIC_CHARMAP)
    cleaned = re.sub(r"\s+", "", cleaned)
    if not cleaned:
        return None
    return "/" + cleaned + "/"


def parse_translation(translation):
    """与 gen_words_json.py 同实现：[(pos, meaning), ...]，每个 POS 至多一条。"""
    if not translation:
        return []
    senses = []
    pos_index = {}
    # ECDICT 用字面量 "\n"（反斜杠 + n）分隔义行；同时去除残留的 \r（csv 解析后偶存）
    translation = translation.replace("\\r", "").replace("\\n", "\n")
    for raw_line in translation.split("\n"):
        line = raw_line.strip()
        if not line:
            continue
        m = POS_RE.match(line)
        if m:
            pos = m.group(1)
            meaning = line[m.end():].strip().lstrip("，,;；").strip()
            if not meaning:
                continue
            if pos in pos_index:
                idx = pos_index[pos]
                if meaning not in senses[idx][1]:
                    senses[idx][1] = (senses[idx][1] + "； " + meaning) if senses[idx][1] else meaning
            else:
                pos_index[pos] = len(senses)
                senses.append([pos, meaning])
        else:
            extra = re.sub(r"^\[[^\]]*\]\s*", "", line).strip()
            if not extra:
                continue
            if senses:
                if extra not in senses[-1][1]:
                    senses[-1][1] = (senses[-1][1] + "； " + extra) if senses[-1][1] else extra
            else:
                senses.append(["n.", extra])
                pos_index["n."] = 0
    return [(p, m) for p, m in senses]


def main():
    default_csv = "C:/Users/Administrator/AppData/Local/Temp/ecdict.csv"
    csv_path = sys.argv[1] if len(sys.argv) > 1 else default_csv
    if not os.path.exists(csv_path):
        sys.exit(f"ERROR: ecdict.csv not found at {csv_path}")

    rows = []  # (word, phonetic, senses, collins, bnc, frq)
    skipped_phrase = 0
    skipped_no_trans = 0
    skipped_no_sense = 0

    with open(csv_path, encoding="utf-8") as f:
        reader = csv.DictReader(f)
        for row in reader:
            trans = (row.get("translation") or "").strip()
            if not trans:
                skipped_no_trans += 1
                continue
            word = (row.get("word") or "").strip()
            # 只保留单词（短语 / 带空格脏数据跳过）
            if not word or " " in word:
                skipped_phrase += 1
                continue
            senses = parse_translation(trans)
            if not senses:
                skipped_no_sense += 1
                continue
            try:
                collins = int(row.get("collins") or "0")
            except ValueError:
                collins = 0
            try:
                bnc = int(row.get("bnc") or "0")
            except ValueError:
                bnc = 0
            try:
                frq = int(row.get("frq") or "0")
            except ValueError:
                frq = 0
            rows.append((word, normalize_phonetic(row.get("phonetic") or ""), senses, collins, bnc, frq))

    # 按词频排序：高频在前。collins 星级 desc → bnc asc（小=常用）→ frq asc → alpha
    big = 10 ** 12
    rows.sort(key=lambda x: (
        -x[3],
        x[4] if x[4] > 0 else big,
        x[5] if x[5] > 0 else big,
        x[0],
    ))

    top = rows[:TOP_N]
    if len(top) < TOP_N:
        sys.stderr.write(f"WARN: only {len(top)} words available (target {TOP_N})\n")

    # 展开为 (word, phonetic, pos, meaning) 多行 entries
    entries = []
    for word, phonetic, senses, _, _, _ in top:
        for pos, meaning in senses:
            entries.append({
                "word": word,
                "phonetic": phonetic,
                "pos": pos,
                "meaning": meaning,
            })

    out = {
        "source": "ECDICT (MIT License, https://github.com/skywind3000/ECDICT)",
        "words": entries,
    }

    out_path = os.path.join(
        os.path.dirname(os.path.abspath(__file__)),
        "..", "app", "src", "main", "assets", "dictionary.json",
    )
    out_path = os.path.normpath(out_path)
    with open(out_path, "w", encoding="utf-8") as f:
        json.dump(out, f, ensure_ascii=False, separators=(",", ":"))

    # 统计
    unique_words = len(set(e["word"] for e in entries))
    with_phonetic = sum(1 for e in entries if e["phonetic"])
    print(f"unique words: {unique_words}")
    print(f"total entries (word x pos): {len(entries)}")
    print(f"entries with phonetic: {with_phonetic}")
    print(f"skipped (phrase / no trans / no sense): {skipped_phrase} / {skipped_no_trans} / {skipped_no_sense}")
    print(f"wrote {out_path} ({os.path.getsize(out_path) // 1024} KB)")


if __name__ == "__main__":
    main()
