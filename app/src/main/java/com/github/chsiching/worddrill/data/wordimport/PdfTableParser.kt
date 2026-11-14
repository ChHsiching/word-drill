package com.github.chsiching.worddrill.data.wordimport

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.InputStream

// Requires PdfBox-Android's PDFBoxResourceLoader.init (called in Application), so this parser
// is only testable in androidTest, not JVM unit tests.
object PdfTableParser {

    /** Max characters per line; longer lines are treated as watermark/footer/scan background and skipped. */
    private const val MAX_LINE_LENGTH = 200

    fun parse(input: InputStream): List<ImportRow> {
        val text = input.use { stream ->
            PDDocument.load(stream).use { doc ->
                PDFTextStripper().getText(doc)
            }
        }
        if (text.isEmpty()) return emptyList()

        val rows = mutableListOf<ImportRow>()
        for (rawLine in text.lines()) {
            if (rawLine.isBlank()) continue
            if (rawLine.length > MAX_LINE_LENGTH) continue

            val cols = splitLine(rawLine)
            val row = toRow(cols) ?: continue
            rows.add(row)
        }
        return rows
    }

    private fun splitLine(line: String): List<String> = ImportRowMapper.splitLine(line)

    private fun toRow(cols: List<String>): ImportRow? = ImportRowMapper.toRow(cols)
}
