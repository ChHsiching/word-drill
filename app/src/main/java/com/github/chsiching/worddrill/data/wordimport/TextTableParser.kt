package com.github.chsiching.worddrill.data.wordimport

import java.io.InputStream

/**
 * Ticket #21: txt / csv table parser (pure function).
 *
 * Column layout (issue #21, identical for all formats):
 * - col 0: index (ignored)
 * - col 1: word
 * - col 2: phonetic (nullable)
 * - col 3: pos+meaning (nullable)
 *
 * Two modes:
 * - [delimiter] = `,`: CSV mode, supports double-quoted fields (meaning containing `,`)
 * - [delimiter] = null: TXT mode, splits columns on tabs or 2+ consecutive spaces
 *   (compatible with aligned tables copied from PDF/Word)
 *
 * What it does not do:
 * - No POS parsing ([PosMeaningParser] owns that; [FileWordImporter] calls it on a dictionary miss)
 * - No header detection (not required by the issue; if headers must be excluded, the caller
 *   filters them manually after import)
 *
 * Parsed fields are trimmed, and empty fields become null (matching [ImportRow] field semantics).
 * Rows with an empty word column (col 1) are skipped outright (they cannot form a word entry).
 */
object TextTableParser {

    /**
     * @param input Text input stream (decoded as UTF-8)
     * @param delimiter `,` means CSV mode; `null` means TXT auto-detection (tab / 2+ spaces)
     */
    fun parse(input: InputStream, delimiter: Char?): List<ImportRow> {
        val text = input.bufferedReader(Charsets.UTF_8).readText()
        if (text.isEmpty()) return emptyList()

        val rows = mutableListOf<ImportRow>()
        for (rawLine in text.lines()) {
            // Skip blank lines (including CRLF leftovers that are only \r)
            if (rawLine.isBlank()) continue

            val cols = if (delimiter != null) {
                splitCsvLine(rawLine, delimiter)
            } else {
                // TXT mode: split on tab / 2+ spaces, sharing [ImportRowMapper.splitLine] with PdfTableParser
                ImportRowMapper.splitLine(rawLine)
            }
            val row = ImportRowMapper.toRow(cols) ?: continue
            rows.add(row)
        }
        return rows
    }

    /**
     * CSV line splitting: supports double-quoted fields.
     *
     * Simple state machine: the quote flag toggles; a delimiter (outside a quote) ends a field.
     * Delimiters inside quotes do not split; the quotes themselves are stripped.
     */
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
                        // CSV escaping: two consecutive quotes become one literal quote
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
