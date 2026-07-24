package com.github.chsiching.worddrill.data.wordimport

/**
 * Ticket #21：所有文件格式解析后的统一中间模型（一行一词）。
 *
 * 字段对应 issue #21 的列结构（去掉序号列）：
 * - [word]：单词（col 1，必须；但解析器允许为空，由 [FileWordImporter] 判定跳过）
 * - [phonetic]：音标（col 2，可空）
 * - [posMeaning]：词性 + 释义原文（col 3，可空；由 [PosMeaningParser] 在导入时按需解析）
 *
 * 设计：[posMeaning] 保留原文而非解析后的 senses 列表，便于解析器（XLSX/TXT/PDF）保持
 * 单一职责（"取文本"），把 POS 切分留给 [FileWordImporter] 在词典查不到时再走。这样词典
 * 命中时根本不需要解析 col 3，省一步。
 */
data class ImportRow(
    val word: String,
    val phonetic: String? = null,
    val posMeaning: String? = null,
)
