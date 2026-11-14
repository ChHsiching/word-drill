package com.github.chsiching.worddrill.data.wordimport

import java.io.InputStream

object TextTableParser {

    /**
     * @param delimiter `,` means CSV mode (double-quoted fields supported); `null` means TXT
     *   mode (splits on tab / 2+ consecutive spaces)
     */
    fun parse(input: InputStream, delimiter: Char?): List<ImportRow> {
        val text = input.bufferedReader(Charsets.UTF_8).readText()
        if (text.isEmpty()) return emptyList()

        val rows = mutableListOf<ImportRow>()
        for (rawLine in text.lines()) {
            if (rawLine.isBlank()) continue

            val cols = if (delimiter != null) {
                splitCsvLine(rawLine, delimiter)
            } else {
                ImportRowMapper.splitLine(rawLine)
            }
            val row = ImportRowMapper.toRow(cols) ?: continue
            rows.add(row)
        }
        return rows
    }

    private fun splitCsvLine(line: String, delimiter: Char): List<String> {
        val cols = mutableListOf<String>()
        val current = StringBuilder()
        var inQuote = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' -> {
                    if (inQuote && i + 1 < line.length && line[i + 1] == '"') {
                        current.append('"')
                        i += 2
                        continue
                    }
                    inQuote = !inQuote
                    i++
                }
                c == delimiter && !inQuote -> {
                    cols.add(current.toString())
                    current.clear()
                    i++
                }
                else -> {
                    current.append(c)
                    i++
                }
            }
        }
        cols.add(current.toString())
        return cols
    }
}
