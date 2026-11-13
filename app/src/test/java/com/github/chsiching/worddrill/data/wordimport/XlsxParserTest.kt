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

/**
 * Ticket #21: xlsx parsing (hand-written OOXML, zero dependencies).
 *
 * The tests build a minimal valid .xlsx in memory (ZIP + OOXML) to verify the parser.
 * No sample.xlsx binary goes into resources. Building it by hand makes the structure reviewable and avoids a binary blob.
 *
 * OOXML structure points (only the parts the parser reads):
 * - `xl/sharedStrings.xml`: the shared string table, a list of `<si><t>string</t></si>`
 * - `xl/worksheets/sheet1.xml`: multiple `<row>` elements inside `<sheetData>`, each row with multiple `<c r="A1" t="s"><v>0</v></c>`
 *   - `t="s"`: the cell value is an index into sharedStrings (`<v>` holds a number)
 *   - `t` omitted (or `t="n"`): the cell value is an inline number (`<v>` holds the value directly)
 *   - `t="inlineStr"`: the cell contains `<is><t>string</t></is>` and bypasses sharedStrings
 * - `r="A1"`: A is column 1, 1 is row 1. The parser derives each cell's column position from this
 *
 * Column layout (issue #21, unified across all formats):
 * - col A (col 0): index (ignored)
 * - col B (col 1): word
 * - col C (col 2): phonetic
 * - col D (col 3): pos+meaning
 */
@RunWith(JUnit4::class)
class XlsxParserTest {

    @Test
    fun parsesBasicFourColumns_withSharedStrings() {
        val xlsx = buildXlsx(
            sharedStrings = listOf("1", "apple", "/ˈæpl/", "n.苹果"),
            rows = listOf(
                // A=sheet ref cell index of sharedStrings
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
        // Empty cell (C2 missing) gives a null phonetic.
        assertThat(rows[1]).isEqualTo(ImportRow("run", null, "vi.跑"))
    }

    @Test
    fun emptyCells_yieldsNullFields() {
        // The row has only the word; the phonetic and meaning cells are entirely absent.
        val xlsx = buildXlsx(
            sharedStrings = listOf("1", "ghost"),
            rows = listOf(
                row(cells = listOf(cell("A1", "s", "0"), cell("B1", "s", "1"))),
            ),
        )
        val rows = XlsxParser.parse(ByteArrayInputStream(xlsx))
        assertThat(rows.single()).isEqualTo(ImportRow("ghost", null, null))
    }

    @Test
    fun inlineStringCell_supported() {
        // t="inlineStr" mode: the cell contains <is><t>...</t></is>.
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
        // Columns may appear in any order; sorting relies on r="A1/B1/C1/D1", not the order of appearance.
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
        // A multi-sheet xlsx: this parser reads only sheet1.xml, and data from other sheets must be ignored.
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

    // ---- helpers: build a minimal .xlsx in memory ----

    /** XML fragment for one cell. Types "s" / "n" use <v>, "inlineStr" uses <is><t>. */
    private fun cell(ref: String, type: String, value: String): String = when (type) {
        "inlineStr" -> error("use cellInline for inlineStr")
        else -> """<c r="$ref" t="$type"><v>$value</v></c>"""
    }

    /** An inlineStr cell. */
    private fun cellInline(ref: String, value: String): String =
        """<c r="$ref" t="inlineStr"><is><t>${escapeXml(value)}</t></is></c>"""

    /** A numeric cell (no type, defaults to number). */
    private fun cell(ref: String): String = """<c r="$ref"/>"""

    /** One row. */
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

    /**
     * Builds a minimal .xlsx: the required entries plus optional extra entries (such as sheet2).
     * Note: a compliant .xlsx also needs [Content_Types].xml + xl/workbook.xml + rels,
     * but this parser reads only sharedStrings + sheet1, so omitting the compliance metadata in the build does not affect the parsing tests.
     */
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
