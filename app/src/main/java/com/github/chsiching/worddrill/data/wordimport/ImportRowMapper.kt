package com.github.chsiching.worddrill.data.wordimport

/**
 * Ticket #21：列 → [ImportRow] 通用映射。
 *
 * [TextTableParser]（TXT 模式）和 [PdfTableParser] 都按同一规则切列 + 映射 [ImportRow]，
 * 提取到这里避免两处实现漂移（issue #21 列结构对所有格式统一）。
 *
 * 列结构（issue #21）：col0=序号忽略 / col1=word / col2=phonetic / col3=posMeaning。
 */
internal object ImportRowMapper {

    private val COLUMN_SEPARATOR = Regex("\\t+| {2,}")

    /** 按行首尾 trim 后，按 tab 或 2+ 连续空格切列。 */
    fun splitLine(line: String): List<String> {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return emptyList()
        return trimmed.split(COLUMN_SEPARATOR)
    }

    /**
     * 列数组 → [ImportRow]。col1=word / col2=phonetic / col3=posMeaning。
     * - 列数 < 2 或 col1（word）空 → 返回 null
     * - 音标 / 释义列 trim 后空 → null
     */
    fun toRow(cols: List<String>): ImportRow? {
        if (cols.size < 2) return null
        val word = cols[1].trim()
        if (word.isEmpty()) return null
        val phonetic = cols.getOrNull(2)?.trim()?.takeIf { it.isNotEmpty() }
        val posMeaning = cols.getOrNull(3)?.trim()?.takeIf { it.isNotEmpty() }
        return ImportRow(word = word, phonetic = phonetic, posMeaning = posMeaning)
    }
}
