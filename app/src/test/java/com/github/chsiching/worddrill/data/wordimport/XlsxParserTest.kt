package com.github.chsiching.worddrill.data.wordimport

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

@RunWith(JUnit4::class)
class XlsxParserTest {

    @Test
    fun parsesBasicFourColumns_withSharedStrings() {
        val xlsx = buildXlsx(
            sharedStrings = listOf("1", "apple", "/ˈæpl/", "n.苹果"),
            rows = listOf(
                row(cells = listOf(cell("A1", "s", "0"), cell("B1", "s", "1"), cell("C1", "s", "2"), cell("D1", "s", "3"))),
            ),
        )
        val rows = XlsxParser.parse(ByteArrayInputStream(xlsx))
        assertThat(rows).containsExactly(
            ImportRow(word = "apple", phonetic = "/ˈæpl/", posMeaning = "n.苹果"),
        )
    }

    @Test
    fun parsesMultipleRows() {
        val xlsx = buildXlsx(
            sharedStrings = listOf("1", "apple", "/p1/", "n.苹果", "2", "run", "vi.跑"),
            rows = listOf(
                row(cells = listOf(
                    cell("A1", "s", "0"), cell("B1", "s", "1"), cell("C1", "s", "2"), cell("D1", "s", "3"),
                )),
                row(cells = listOf(
                    cell("A2", "s", "4"), cell("B2", "s", "5"), cell("C2"), cell("D2", "s", "6"),
                )),
            ),
        )
        val rows = XlsxParser.parse(ByteArrayInputStream(xlsx))
        assertThat(rows).hasSize(2)
        assertThat(rows[0]).isEqualTo(ImportRow("apple", "/p1/", "n.苹果"))
        assertThat(rows[1]).isEqualTo(ImportRow("run", null, "vi.跑"))
    }

    @Test
    fun emptyCells_yieldsNullFields() {
        val xlsx = buildXlsx(
            sharedStrings = listOf("1", "ghost"),
            rows = listOf(
                row(cells = listOf(cell("A1", "s", "0"), cell("B1", "s", "1"))),
            ),
        )
        val rows = XlsxParser.parse(ByteArrayInputStream(xlsx))
        // Only the index and word cells exist: a word-only row.
        assertThat(rows.single()).isEqualTo(ImportRow("ghost", null, null, wordOnly = true))
    }

    @Test
    fun inlineStringCell_supported() {
        val xlsx = buildXlsx(
            sharedStrings = emptyList(),
            rows = listOf(
                row(cells = listOf(
                    cellInline("A1", "1"),
                    cellInline("B1", "apple"),
                    cellInline("C1", "/p/"),
                    cellInline("D1", "n.苹果"),
                )),
            ),
        )
        val rows = XlsxParser.parse(ByteArrayInputStream(xlsx))
        assertThat(rows.single()).isEqualTo(ImportRow("apple", "/p/", "n.苹果"))
    }

    @Test
    fun blankWordRow_skipped() {
        val xlsx = buildXlsx(
            sharedStrings = listOf("1", "", "n.空"),
            rows = listOf(
                row(cells = listOf(
                    cell("A1", "s", "0"), cell("B1", "s", "1"), cell("D1", "s", "2"),
                )),
            ),
        )
        val rows = XlsxParser.parse(ByteArrayInputStream(xlsx))
        assertThat(rows).isEmpty()
    }

    @Test
    fun emptySheet_returnsEmptyList() {
        val xlsx = buildXlsx(sharedStrings = emptyList(), rows = emptyList())
        val rows = XlsxParser.parse(ByteArrayInputStream(xlsx))
        assertThat(rows).isEmpty()
    }

    @Test
    fun outOfOrderColumns_sortedByColumnRef() {
        val xlsx = buildXlsx(
            sharedStrings = listOf("1", "apple", "/p/", "n.苹果"),
            rows = listOf(
                row(cells = listOf(
                    cell("D1", "s", "3"), cell("A1", "s", "0"), cell("C1", "s", "2"), cell("B1", "s", "1"),
                )),
            ),
        )
        val rows = XlsxParser.parse(ByteArrayInputStream(xlsx))
        assertThat(rows.single()).isEqualTo(ImportRow("apple", "/p/", "n.苹果"))
    }

    @Test
    fun skipsRowsAfterFirstSheet() {
        val xlsx = buildXlsx(
            sharedStrings = listOf("1", "sheet1word", "2", "sheet2word"),
            extraEntries = mapOf(
                "xl/worksheets/sheet2.xml" to worksheetXml(listOf(
                    row(cells = listOf(cell("A1", "s", "2"), cell("B1", "s", "3"))),
                )),
            ),
            rows = listOf(
                row(cells = listOf(cell("A1", "s", "0"), cell("B1", "s", "1"))),
            ),
        )
        val rows = XlsxParser.parse(ByteArrayInputStream(xlsx))
        assertThat(rows.map { it.word }).containsExactly("sheet1word")
    }

    private fun cell(ref: String, type: String, value: String): String = when (type) {
        "inlineStr" -> error("use cellInline for inlineStr")
        else -> """<c r="$ref" t="$type"><v>$value</v></c>"""
    }

    private fun cellInline(ref: String, value: String): String =
        """<c r="$ref" t="inlineStr"><is><t>${escapeXml(value)}</t></is></c>"""

    private fun cell(ref: String): String = """<c r="$ref"/>"""

    private fun row(cells: List<String>): String =
        """<row r="1">${cells.joinToString("")}</row>"""

    private fun escapeXml(s: String): String =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    private fun sharedStringsXml(items: List<String>): String {
        if (items.isEmpty()) return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"/>"""
        val body = items.joinToString("") { "<si><t>${escapeXml(it)}</t></si>" }
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">$body</sst>"""
    }

    private fun worksheetXml(rows: List<String>): String {
        val body = if (rows.isEmpty()) "" else "<sheetData>${rows.joinToString("")}</sheetData>"
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">$body</worksheet>"""
    }

    private fun buildXlsx(
        sharedStrings: List<String>,
        rows: List<String>,
        extraEntries: Map<String, String> = emptyMap(),
    ): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry("xl/sharedStrings.xml"))
            zip.write(sharedStringsXml(sharedStrings).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("xl/worksheets/sheet1.xml"))
            zip.write(worksheetXml(rows).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            for ((path, content) in extraEntries) {
                zip.putNextEntry(ZipEntry(path))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }
}
