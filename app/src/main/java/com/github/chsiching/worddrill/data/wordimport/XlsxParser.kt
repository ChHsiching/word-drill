package com.github.chsiching.worddrill.data.wordimport

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * Ticket #21：xlsx 解析（手写 OOXML，零生产依赖）。
 *
 * issue #21 原指定 Fastexcel (cn.idev.excel:fastexcel:1.3.0)，但它本质是 Apache POI 5.4.1
 * 的封装。POI 5.x 在 Android 上有已知问题（`javax.xml.stream` 不存在，需 stax stub + ProGuard
 * 规则），且会让 APK +10MB。词书导入只需读 4 列文本，POI 的复杂特性（公式、合并单元格、图表）
 * 用不上。改用：ZIP 解包 + [XmlPullParser]（Android 框架自带，无新依赖）。
 *
 * 流程：
 * 1. [ZipInputStream] 解 .xlsx，找 `xl/sharedStrings.xml` + `xl/worksheets/sheet1.xml`。
 *    只读第一个 sheet（多 sheet 文件其余忽略；issue 没要求多 sheet）。
 * 2. sharedStrings：解 `<si><t>...</t></si>` 序列 → `List<String>`（按出现顺序索引）。
 * 3. sheet1：解 `<row>` 内的 `<c r="B1" t="s"><v>0</v></c>`，按 `r="A1"` 的字母列定位，
 *    `t="s"` 的值是 sharedStrings 索引（`<v>` 里数字），`t="inlineStr"` 的值是 `<is><t>` 文本，
 *    无 `t` / `t="n"` 的值是数字字符串。
 * 4. 每行按 col A/B/C/D 映射到 [ImportRow]：A=序号忽略 / B=word / C=phonetic / D=posMeaning。
 *    B 列空 → 整行跳过。
 *
 * 不支持（issue 没要求）：公式 `<f>` / 合并单元格 / 多 sheet 合并 / `.xls`（旧格式）。
 *
 * 设计要点：用 [XmlPullParser] 而非 DOM（流式解析，省内存；大 xlsx 不会一次性载入）。
 */
object XlsxParser {

    private const val NS_SHEET = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"

    /** 列字母（A/B/C/D...）→ 0 起列号。"A"=0, "B"=1, ... "AA"=26 */
    private fun colIndexFromRef(ref: String): Int {
        // ref 如 "B12"：前缀字母是列，后缀数字是行
        val letters = ref.takeWhile { it.isLetter() }
        var idx = 0
        for (c in letters) idx = idx * 26 + (c.uppercaseChar() - 'A' + 1)
        return idx - 1
    }

    fun parse(input: InputStream): List<ImportRow> {
        var sharedStrings: List<String> = emptyList()
        var sheet1Xml: String? = null

        ZipInputStream(input).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val name = entry.name
                when {
                    name == "xl/sharedStrings.xml" -> {
                        sharedStrings = readSharedStrings(zip)
                    }
                    name == "xl/worksheets/sheet1.xml" -> {
                        sheet1Xml = zip.readBytes().toString(Charsets.UTF_8)
                    }
                    // 其他 entries 忽略
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }

        val xml = sheet1Xml ?: return emptyList()
        return parseWorksheet(xml, sharedStrings)
    }

    /** 解 sharedStrings.xml → 字符串列表（按 <si> 顺序）。 */
    private fun readSharedStrings(input: InputStream): List<String> {
        val parser = newParser(input)
        val result = mutableListOf<String>()
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.name == "si") {
                result.add(readTextInsideSi(parser))
            }
            event = parser.next()
        }
        return result
    }

    /**
     * 读 <si>...</si> 内的文本。<si> 可包含单个 <t> 或多个 <t>（rich text，每个 <r><t>）。
     * 这里把所有 <t> 的文本拼起来，简化处理。
     */
    private fun readTextInsideSi(parser: XmlPullParser): String {
        val sb = StringBuilder()
        val startDepth = parser.depth
        var event = parser.next()
        while (!(event == XmlPullParser.END_TAG && parser.name == "si" && parser.depth == startDepth)) {
            if (event == XmlPullParser.START_TAG && parser.name == "t") {
                sb.append(parser.nextText())
            }
            if (event == XmlPullParser.END_DOCUMENT) break
            event = parser.next()
        }
        return sb.toString()
    }

    /** 解 sheet1.xml，每 <row> → 一个 ImportRow（word 列空则跳过）。 */
    private fun parseWorksheet(xml: String, sharedStrings: List<String>): List<ImportRow> {
        val parser = newParser(xml.byteInputStream(Charsets.UTF_8))
        val rows = mutableListOf<ImportRow>()
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.name == "row") {
                val cells = readCellsInRow(parser, sharedStrings)
                val row = buildRow(cells)
                if (row != null) rows.add(row)
            }
            event = parser.next()
        }
        return rows
    }

    /** 读 <row> 内的所有 <c> → Map<colIndex, value>。value 已解析（sharedStrings 解引用 / inlineStr）。 */
    private fun readCellsInRow(parser: XmlPullParser, sharedStrings: List<String>): Map<Int, String> {
        val cells = mutableMapOf<Int, String>()
        val startDepth = parser.depth
        var event = parser.next()
        while (!(event == XmlPullParser.END_TAG && parser.name == "row" && parser.depth == startDepth)) {
            if (event == XmlPullParser.START_TAG && parser.name == "c") {
                val ref = parser.getAttributeValue(null, "r") ?: ""
                val type = parser.getAttributeValue(null, "t") ?: "n"
                val colIdx = colIndexFromRef(ref)
                val value = readCellValue(parser, type) { sharedStrings }
                if (value != null) cells[colIdx] = value
            }
            if (event == XmlPullParser.END_DOCUMENT) break
            event = parser.next()
        }
        return cells
    }

    /**
     * 读 <c> 元素的值。
     * - t="s"：<v> 里是 sharedStrings 的索引
     * - t="inlineStr"：<is><t>...</t></is> 直接是文本
     * - t="n" 或无：<v> 里是数字字符串（保留原样）
     * - 无 <v> 也无 <is>：返回 null（空 cell）
     */
    private fun readCellValue(
        parser: XmlPullParser,
        type: String,
        sharedStringsView: () -> List<String>,
    ): String? {
        val startDepth = parser.depth
        var event = parser.next()
        var value: String? = null
        while (!(event == XmlPullParser.END_TAG && parser.name == "c" && parser.depth == startDepth)) {
            if (event == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "v" -> {
                        val raw = parser.nextText()
                        value = if (type == "s") {
                            // sharedStrings 解引用
                            val idx = raw.toIntOrNull() ?: return null
                            sharedStringsView().getOrNull(idx)
                        } else {
                            raw
                        }
                    }
                    "is" -> {
                        // inlineStr：<is><t>text</t></is>
                        value = readInlineString(parser)
                    }
                }
            }
            if (event == XmlPullParser.END_DOCUMENT) break
            event = parser.next()
        }
        return value
    }

    /** 读 <is> 内的 <t>...</t> 文本。 */
    private fun readInlineString(parser: XmlPullParser): String {
        val sb = StringBuilder()
        val startDepth = parser.depth
        var event = parser.next()
        while (!(event == XmlPullParser.END_TAG && parser.name == "is" && parser.depth == startDepth)) {
            if (event == XmlPullParser.START_TAG && parser.name == "t") {
                sb.append(parser.nextText())
            }
            if (event == XmlPullParser.END_DOCUMENT) break
            event = parser.next()
        }
        return sb.toString()
    }

    /** Map<colIndex, value> → ImportRow。col 1=word / col 2=phonetic / col 3=posMeaning。
     *  cells 里的 value 已在 [readCellValue] 时按 cell type 解引用过 sharedStrings。 */
    private fun buildRow(cells: Map<Int, String>): ImportRow? {
        val word = cells[1]?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val phonetic = cells[2]?.trim()?.takeIf { it.isNotEmpty() }
        val posMeaning = cells[3]?.trim()?.takeIf { it.isNotEmpty() }
        return ImportRow(word = word, phonetic = phonetic, posMeaning = posMeaning)
    }

    private fun newParser(input: InputStream): XmlPullParser {
        val parser = XmlPullParserFactory.newInstance().newPullParser()
        parser.setInput(input, "UTF-8")
        return parser
    }
}
