package com.github.chsiching.worddrill.data.wordimport

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream
import java.util.zip.ZipInputStream

object XlsxParser {

    private fun colIndexFromRef(ref: String): Int {
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
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }

        val xml = sheet1Xml ?: return emptyList()
        return parseWorksheet(xml, sharedStrings)
    }

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

    // OOXML allows rich text: one <si> may hold multiple <r><t> runs, so concatenate all <t>.
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
     * OOXML cell value by `t` attribute: `s` = <v> holds a sharedStrings index; `inlineStr` =
     * <is><t> text; `n` or absent = <v> numeric string kept as-is; neither <v> nor <is> = null.
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
                            val idx = raw.toIntOrNull() ?: return null
                            sharedStringsView().getOrNull(idx)
                        } else {
                            raw
                        }
                    }
                    "is" -> {
                        value = readInlineString(parser)
                    }
                }
            }
            if (event == XmlPullParser.END_DOCUMENT) break
            event = parser.next()
        }
        return value
    }

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
