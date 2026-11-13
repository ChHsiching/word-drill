package com.github.chsiching.worddrill.data.wordimport

/**
 * Ticket #21: shared columns-to-[ImportRow] mapping.
 *
 * Both [TextTableParser] (TXT mode) and [PdfTableParser] split columns and map to [ImportRow]
 * by the same rules, extracted here so the two implementations cannot drift apart
 * (issue #21's column layout is identical for all formats).
 *
 * Column layout (issue #21): col0=index ignored / col1=word / col2=phonetic / col3=posMeaning.
 */
internal object ImportRowMapper {

    private val COLUMN_SEPARATOR = Regex("\\t+| {2,}")

    /** Trims the line, then splits columns on tabs or 2+ consecutive spaces. */
    fun splitLine(line: String): List<String> {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return emptyList()
        return trimmed.split(COLUMN_SEPARATOR)
    }

    /**
     * Column array -> [ImportRow]. col1=word / col2=phonetic / col3=posMeaning.
     * - Fewer than 2 columns, or col1 (word) empty -> returns null
     * - Phonetic / meaning columns empty after trim -> null
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
