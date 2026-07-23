#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
从 ECDICT (MIT, https://github.com/skywind3000/ECDICT) 生成 app/src/main/assets/words.json。

数据来源说明：
- ECDICT 是 MIT 许可的英汉词典数据库，依据各类考试大纲与语料库标注词汇。
- 三本预置词书的成员由 ECDICT 的 tag 字段确定：cet4 / cet6 / ky（考研）。
- 词性 (POS) 与中文释义从 translation 字段逐行解析（cet4/cet6/ky 单词的 pos 列为空，
  词性嵌在 translation 每行行首，如 "vt. 放弃..."）。

输出 JSON schema（与 Room 全局词条池对应）：
  {
    "source": "ECDICT (MIT)",
    "books": [
      { "name": "CET-4", "words": [ { "text": "apple", "senses": [ {"pos":"n.","meaning":"苹果"} ] } ] },
      ...
    ]
  }

解析规则（保证 (wordId, pos) 唯一约束）：
- translation 按 "\n" 拆行。
- 行首匹配已知 POS 前缀（n./v./vt./vi./a./adv./prep./conj./num./pron./art./aux./int./abbr.）
  → 新建一条 sense：pos=前缀，meaning=剩余中文文本。
- 否则（如 "[经] 能力" 这类领域标注、或无前缀的补充行）→ 追加到上一条 sense 的 meaning
  （分号分隔），不另起 sense。
- 一个 POS 下若多行（极少见），同样合并进同一条 meaning。

用法：python tools/gen_words_json.py <path/to/ecdict.csv>
（默认读 ./ecdict.csv）
"""

import csv
import json
import os
import re
import sys

# 已知英文词性缩写（行首前缀）。顺序无影响，但放完整 token 避免误匹配。
POS_PREFIXES = (
    "prep.", "pron.", "conj.", "adv.", "num.", "art.", "aux.", "int.",
    "abbr.", "vt.", "vi.", "n.", "v.", "a.",
)
POS_RE = re.compile(r"^(" + "|".join(re.escape(p) for p in POS_PREFIXES) + r")")

# ECDICT tag → 本 App 词书名映射
BOOK_TAGS = [
    ("cet4", "CET-4"),
    ("cet6", "CET-6"),
    ("ky", "考研英语"),
]


def parse_translation(translation):
    """
    把 ECDICT 的 translation 字段解析成 [(pos, meaning), ...]。
    每个 POS 至多一条（同 POS 多行合并），满足 Room 的 (wordId, pos) 唯一约束。
    """
    if not translation:
        return []
    senses = []          # list of [pos, meaning]
    pos_index = {}       # pos -> index in senses（合并同 POS 用）
    # ECDICT 的 translation 用字面量 "\n"（反斜杠 + n，两个字符）分隔多义行，
    # 而非真实换行。先还原成真实换行再按行解析。
    translation = translation.replace("\\n", "\n")
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
                # 同 POS 已有，合并（理论上极少，保险）
                idx = pos_index[pos]
                if meaning not in senses[idx][1]:
                    senses[idx][1] = (senses[idx][1] + "； " + meaning) if senses[idx][1] else meaning
            else:
                pos_index[pos] = len(senses)
                senses.append([pos, meaning])
        else:
            # 无 POS 前缀的行（领域标注 [经]/[医]/[计] 或补充说明）→ 并入上一条 sense。
            extra = line
            # 去掉常见领域方括号前缀，保留含义文本
            extra = re.sub(r"^\[[^\]]*\]\s*", "", extra).strip()
            if not extra:
                continue
            if senses:
                if extra not in senses[-1][1]:
                    senses[-1][1] = (senses[-1][1] + "； " + extra) if senses[-1][1] else extra
            else:
                # 整条 translation 没有 POS 前缀：退化为单条 n.（避免丢词）
                senses.append(["n.", extra])
                pos_index["n."] = 0
    return [(p, m) for p, m in senses]


def main():
    csv_path = sys.argv[1] if len(sys.argv) > 1 else "ecdict.csv"
    if not os.path.exists(csv_path):
        sys.exit(f"ERROR: ecdict.csv not found at {csv_path}")

    # 每个单词只解析一次（多个词书共享），缓存 text -> senses
    word_senses_cache = {}
    books_payload = []

    with open(csv_path, encoding="utf-8") as f:
        reader = csv.DictReader(f)
        # 预读：为每个 tag 收集词条
        by_tag = {tag: [] for tag, _ in BOOK_TAGS}
        skipped = 0
        for row in reader:
            tags = (row.get("tag") or "").split()
            if not any(t in tags for t, _ in BOOK_TAGS):
                continue
            text = (row.get("word") or "").strip()
            # 过滤带空格的短语/带撇号脏数据，只保留纯单词（降低数据噪声）
            if not text or " " in text:
                skipped += 1
                continue
            trans = row.get("translation") or ""
            if not trans.strip():
                skipped += 1
                continue
            if text not in word_senses_cache:
                senses = parse_translation(trans)
                if not senses:
                    skipped += 1
                    continue
                word_senses_cache[text] = senses
            for t, _ in BOOK_TAGS:
                if t in tags:
                    by_tag[t].append(text)

    for tag, book_name in BOOK_TAGS:
        words = sorted(set(by_tag[tag]))
        book_words = []
        for text in words:
            senses = word_senses_cache[text]
            book_words.append({
                "text": text,
                "senses": [{"pos": p, "meaning": m} for p, m in senses],
            })
        books_payload.append({"name": book_name, "words": book_words})

    out = {
        "source": "ECDICT (MIT License, https://github.com/skywind3000/ECDICT)",
        "books": books_payload,
    }

    out_path = os.path.join(
        os.path.dirname(os.path.abspath(__file__)),
        "..", "app", "src", "main", "assets", "words.json",
    )
    out_path = os.path.normpath(out_path)
    with open(out_path, "w", encoding="utf-8") as f:
        json.dump(out, f, ensure_ascii=False, separators=(",", ":"))

    # 统计
    for tag, book_name in BOOK_TAGS:
        n = len(next(b for b in books_payload if b["name"] == book_name)["words"])
        print(f"{book_name}: {n} words")
    # 全局唯一单词数
    unique = len(word_senses_cache)
    print(f"global unique words: {unique}")
    print(f"skipped (no trans / phrase / empty): {skipped}")
    print(f"wrote {out_path} ({os.path.getsize(out_path)//1024} KB)")


if __name__ == "__main__":
    main()
