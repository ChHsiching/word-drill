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
 * Ticket #21：xlsx 解析（手写 OOXML，零依赖）。
 *
 * 测试通过内存构造最小合法 .xlsx（ZIP + OOXML）验证解析器。
 * 不放 sample.xlsx 二进制到 resources —— 手工构造便于审查结构、避免二进制 blob。
 *
 * OOXML 结构要点（仅解析需要的部分）：
 * - `xl/sharedStrings.xml`：共享字符串表，`<si><t>字符串</t></si>` 列表
 * - `xl/worksheets/sheet1.xml`：`<sheetData>` 内多个 `<row>`，每行多个 `<c r="A1" t="s"><v>0</v></c>`
 *   - `t="s"`：cell 值是 sharedStrings 的索引（`<v>` 里是数字）
 *   - `t` 缺省（或 `t="n"`）：cell 值是 inline 数字（`<v>` 里直接是值）
 *   - `t="inlineStr"`：cell 含 `<is><t>字符串</t></is>`，不走 sharedStrings
 * - `r="A1"`：A=col 1，1=row 1。解析器据此算 cell 的列位置
 *
 * 列结构（issue #21，所有格式统一）：
 * - col A（col 0）：序号（忽略）
 * - col B（col 1）：单词
 * - col C（col 2）：音标
 * - col D（col 3）：词性+释义
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
        // 空 cell（C2 缺失）→ phonetic 为 null
        assertThat(rows[1]).isEqualTo(ImportRow("run", null, "vi.跑"))
    }

    @Test
    fun emptyCells_yieldsNullFields() {
        // 行内只有 word，音标 / 释义 cell 整个不存在
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
        // t="inlineStr" 模式：cell 内含 <is><t>...</t></is>
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
        // 列以任意顺序出现，靠 r="A1/B1/C1/D1" 排序而非出现顺序
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
        // 多 sheet 的 xlsx：本解析器只读 sheet1.xml，其他 sheet 数据应忽略
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

    // ---- helpers：内存构造最小 .xlsx ----

    /** 单个 cell 的 XML 片段。type "s" / "n" 走 <v>，"inlineStr" 走 <is><t>。 */
    private fun cell(ref: String, type: String, value: String): String = when (type) {
        "inlineStr" -> error("use cellInline for inlineStr")
        else -> """<c r="$ref" t="$type"><v>$value</v></c>"""
    }

    /** inlineStr cell。 */
    private fun cellInline(ref: String, value: String): String =
        """<c r="$ref" t="inlineStr"><is><t>${escapeXml(value)}</t></is></c>"""

    /** 数值 cell（无 type，默认 number）。 */
    private fun cell(ref: String): String = """<c r="$ref"/>"""

    /** 一行。 */
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
     * 构造最小 .xlsx：必需 entries + 可选额外 entries（如 sheet2）。
     * 注：合规 .xlsx 还需 [Content_Types].xml + xl/workbook.xml + rels，
     * 但本解析器只读 sharedStrings + sheet1，构造时省略合规元数据不影响解析测试。
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
