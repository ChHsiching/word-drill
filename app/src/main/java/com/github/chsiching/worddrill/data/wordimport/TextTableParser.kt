package com.github.chsiching.worddrill.data.wordimport

import java.io.InputStream

/**
 * Ticket #21：txt / csv 表格解析（纯函数）。
 *
 * 列结构（issue #21，所有格式统一）：
 * - col 0：序号（忽略）
 * - col 1：单词
 * - col 2：音标（可空）
 * - col 3：词性+释义（可空）
 *
 * 两种模式：
 * - [delimiter] = `,`：CSV 模式，支持双引号包裹的 field（释义内部含 `,`）
 * - [delimiter] = null：TXT 模式，按 tab 或 2+ 连续空格切列（兼容从 PDF/Word 复制的对齐表）
 *
 * 不做的事：
 * - 不解析 POS（[PosMeaningParser] 负责，[FileWordImporter] 在词典未命中时调用）
 * - 不识别表头（issue 没要求；如需排除表头，调用方在导入后手动判断）
 *
 * 解析后字段统一 trim，空字段 → null（[ImportRow] 字段语义对齐）。
 * 行内 word 列（col 1）为空的行直接跳过（无法构成词条）。
 */
object TextTableParser {

    /**
     * @param input 文本输入流（UTF-8 解码）
     * @param delimiter `,` → CSV 模式；`null` → TXT 自动识别（tab / 2+ 空格）
     */
    fun parse(input: InputStream, delimiter: Char?): List<ImportRow> {
        val text = input.bufferedReader(Charsets.UTF_8).readText()
        if (text.isEmpty()) return emptyList()

        val rows = mutableListOf<ImportRow>()
        for (rawLine in text.lines()) {
            // 跳过空行（含只有 \r 的 CRLF 残留）
            if (rawLine.isBlank()) continue

            val cols = if (delimiter != null) {
                splitCsvLine(rawLine, delimiter)
            } else {
                // TXT 模式：tab / 2+ 空格切列，与 PdfTableParser 共用 [ImportRowMapper.splitLine]
                ImportRowMapper.splitLine(rawLine)
            }
            val row = ImportRowMapper.toRow(cols) ?: continue
            rows.add(row)
        }
        return rows
    }

    /**
     * CSV 行切分：支持双引号包裹的 field。
     *
     * 简易状态机：quote 开关交替，遇到分隔符（在非 quote 内）切一段。
     * 引号内的分隔符不切；引号本身被剥离。
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
                        // CSV 转义：两个连续引号 → 一个字面引号
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
