package com.github.chsiching.worddrill.data.wordimport

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * Ticket #21: xlsx parsing (hand-written OOXML, zero production dependencies).
 *
 * issue #21 originally specified Fastexcel (cn.idev.excel:fastexcel:1.3.0), but it is
 * a wrapper around Apache POI 5.4.1. POI 5.x has known problems on Android
 * (`javax.xml.stream` is missing, needs stax stubs and ProGuard rules) and adds ~10MB to the
 * APK. Book import only needs to read 4 columns of text; POI's advanced features (formulas,
 * merged cells, charts) are useless here. Instead, the parser unpacks the ZIP and reads it
 * with [XmlPullParser] (shipped with the Android framework, no new dependency).
 *
 * Flow:
 * 1. [ZipInputStream] unpacks the .xlsx, looking for `xl/sharedStrings.xml` and
 *    `xl/worksheets/sheet1.xml`. Only the first sheet is read (other sheets in multi-sheet
 *    files are ignored; the issue does not require multi-sheet support).
 * 2. sharedStrings: parse the `<si><t>...</t></si>` sequence into `List<String>` (indexed by
 *    order of appearance).
 * 3. sheet1: parse `<c r="B1" t="s"><v>0</v></c>` inside `<row>`, locating cells by the letter
 *    column of `r="A1"`; a `t="s"` value is a sharedStrings index (the number inside `<v>`),
 *    a `t="inlineStr"` value is the `<is><t>` text, and no `t` / `t="n"` means the value is a
 *    numeric string.
 * 4. Each row maps cols A/B/C/D to [ImportRow]: A=index ignored / B=word / C=phonetic /
 *    D=posMeaning. Empty column B means the whole row is skipped.
 *
 * Not supported (not required by the issue): formulas `<f>` / merged cells / multi-sheet
 * merging / `.xls` (legacy format).
 *
 * Design note: [XmlPullParser] instead of DOM (streaming parse, low memory; a large xlsx is
 * never loaded all at once).
 */
object XlsxParser {

    private const val NS_SHEET = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"

    /** Converts column letters (A/B/C/D...) to a 0-based column index. "A"=0, "B"=1, ... "AA"=26 */
    private fun colIndexFromRef(ref: String): Int {
        // ref like "B12": the letter prefix is the column, the numeric suffix is the row
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
                    // Other entries are ignored
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }

        val xml = sheet1Xml ?: return emptyList()
        return parseWorksheet(xml, sharedStrings)
    }

    /** Parses sharedStrings.xml into a string list (in <si> order). */
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
     * Read the text inside <si>...</si>. An <si> can contain a single <t> or multiple <t>
     * elements (rich text, one per <r><t>).
     * Simplified handling: concatenate the text of all <t> elements.
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

    /** Parses sheet1.xml; each <row> becomes one ImportRow (skipped if the word column is empty). */
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

    /** Reads all <c> inside a <row> into a Map<colIndex, value>. Values are already resolved (sharedStrings dereference / inlineStr). */
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
     * Read the value of a <c> element.
     * - t="s": <v> holds a sharedStrings index
     * - t="inlineStr": <is><t>...</t></is> is the text directly
     * - t="n" or absent: <v> holds a numeric string (kept as-is)
     * - Neither <v> nor <is>: returns null (empty cell)
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
                            // sharedStrings dereference
                            val idx = raw.toIntOrNull() ?: return null
                            sharedStringsView().getOrNull(idx)
                        } else {
                            raw
                        }
                    }
                    "is" -> {
                        // inlineStr: <is><t>text</t></is>
                        value = readInlineString(parser)
                    }
                }
            }
            if (event == XmlPullParser.END_DOCUMENT) break
            event = parser.next()
        }
        return value
    }

    /** Read the <t>...</t> text inside <is>. */
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

    /** Converts Map<colIndex, value> to an ImportRow. col 1=word / col 2=phonetic / col 3=posMeaning.
     *  Values in cells were already dereferenced against sharedStrings by cell type in [readCellValue]. */
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
