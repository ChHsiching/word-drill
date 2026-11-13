package com.github.chsiching.worddrill.data.wordimport

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.InputStream

/**
 * Ticket #21: PDF table parsing (PdfBox-Android).
 *
 * issue #21 originally specified tabula-java (technology.tabula:tabula:1.0.5), but it depends
 * on PDFBox 2.0.24 (which uses `java.awt.*` / `javax.imageio.*`, absent on Android) and the
 * project has had no updates since 2021.
 * Switched to `com.tom-roush:pdfbox-android:2.0.27.0` (an Android port of PDFBox 2.0.27 with
 * AWT removed).
 *
 * Simplified strategy (no full table-recognition algorithm, for the same reasoning as in
 * [XlsxParser]: the issue does not require advanced features):
 * 1. `PDFTextStripper` extracts the text, split by line (default output is already sorted by y).
 * 2. Each line is split into columns on tabs or 2+ consecutive spaces (same as [TextTableParser]'s TXT mode).
 * 3. **Watermark filter**: a line longer than [MAX_LINE_LENGTH] chars is treated as a
 *    watermark/footer (e.g. full-page scanned background text, or the full-page ad watermark of
 *    pirated PDFs) and skipped. This is a heuristic: normal table rows in book PDFs are far
 *    below this threshold.
 * 4. Column layout matches the other formats: col0=index / col1=word / col2=phonetic / col3=posMeaning.
 *
 * Not supported (not required by the issue): scanned PDFs (image-based, needs OCR) / complex
 * cross-page table merging / multi-column layouts.
 *
 * Warning: this parser depends on the Android runtime (PdfBox-Android's native library needs
 * `PDFBoxResourceLoader.init(context)` in Application), so it is **not usable in JVM unit
 * tests**. Coverage path: [FileWordImporterTest] (androidTest) with a small test PDF for
 * end-to-end verification.
 */
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
            // Watermark filter: overlong lines are treated as watermarks and skipped
            if (rawLine.length > MAX_LINE_LENGTH) continue

            val cols = splitLine(rawLine)
            val row = toRow(cols) ?: continue
            rows.add(row)
        }
        return rows
    }

    /** Same as [TextTableParser]'s TXT mode: split on tab or 2+ consecutive spaces + the same
     *  columns-to-ImportRow mapping. Shares [ImportRowMapper] so the two implementations cannot
     *  drift apart (issue #21's shared column layout). */
    private fun splitLine(line: String): List<String> = ImportRowMapper.splitLine(line)

    private fun toRow(cols: List<String>): ImportRow? = ImportRowMapper.toRow(cols)
}
