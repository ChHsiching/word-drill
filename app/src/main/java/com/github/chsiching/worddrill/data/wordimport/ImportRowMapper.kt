package com.github.chsiching.worddrill.data.wordimport

/** Shared column layout for all import formats: col0=index (ignored) / col1=word / col2=phonetic / col3=posMeaning. */
internal object ImportRowMapper {

    private val COLUMN_SEPARATOR = Regex("\\t+| {2,}")

    fun splitLine(line: String): List<String> {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return emptyList()
        return trimmed.split(COLUMN_SEPARATOR)
    }

    fun toRow(cols: List<String>): ImportRow? {
        if (cols.size < 2) return null
        val word = cols[1].trim()
        if (word.isEmpty()) return null
        val phonetic = cols.getOrNull(2)?.trim()?.takeIf { it.isNotEmpty() }
        val posMeaning = cols.getOrNull(3)?.trim()?.takeIf { it.isNotEmpty() }
        return ImportRow(
            word = word,
            phonetic = phonetic,
            posMeaning = posMeaning,
            // Word-only row: nothing followed the word column. Empty trailing
            // columns still count as present (incomplete data, not a bare word).
            wordOnly = cols.size == 2,
        )
    }
}
