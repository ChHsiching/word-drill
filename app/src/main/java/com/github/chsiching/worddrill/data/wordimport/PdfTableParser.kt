package com.github.chsiching.worddrill.data.wordimport

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.InputStream

/**
 * Ticket #21：PDF 表格解析（PdfBox-Android）。
 *
 * issue #21 原指定 tabula-java (technology.tabula:tabula:1.0.5)，但它依赖 PDFBox 2.0.24
 * （用 `java.awt.*` / `javax.imageio.*`，Android 不存在），项目自 2021 无更新。
 * 改用 `com.tom-roush:pdfbox-android:2.0.27.0`（PDFBox 2.0.27 的 Android 移植，去掉 AWT）。
 *
 * 简化策略（不做完整表格识别算法，原因见 [XlsxParser] 同款论证 —— issue 没要求复杂特性）：
 * 1. `PDFTextStripper` 提取文本，按行 split（默认输出已按 y 排序）。
 * 2. 每行按 tab 或 2+ 连续空格切列（与 [TextTableParser] 的 TXT 模式相同）。
 * 3. **水印过滤**：单行字符数 > [MAX_LINE_LENGTH] 的视为水印/页脚（如全页扫描背景文字、
 *    网站盗版 PDF 的整页广告水印）跳过。这是启发式 —— 词书 PDF 的正常表格行字符数远低于此阈值。
 * 4. 列结构同其他格式：col0=序号 / col1=word / col2=phonetic / col3=posMeaning。
 *
 * 不支持（issue 没要求）：扫描版 PDF（图片型，需 OCR）/ 复杂跨页表格合并 / 多栏排版。
 *
 * ⚠️ 本解析器依赖 Android 运行时（PdfBox-Android 的 native 库需 `PDFBoxResourceLoader.init(context)`
 *    在 Application 初始化），因此 **JVM 单测不可用**。覆盖路径：[FileWordImporterTest]
 *    （androidTest）放一个小测试 PDF 端到端验证。
 */
object PdfTableParser {

    /** 单行字符数上限，超过视为水印/页脚/扫描背景，跳过。 */
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
            // 水印过滤：超长行视为水印跳过
            if (rawLine.length > MAX_LINE_LENGTH) continue

            val cols = splitLine(rawLine)
            val row = toRow(cols) ?: continue
            rows.add(row)
        }
        return rows
    }

    /** 与 [TextTableParser] 的 TXT 模式相同：tab 或 2+ 连续空格切列 + 同样的列→ImportRow 映射。
     *  共用 [ImportRowMapper] 避免两处实现漂移（issue #21 同款列结构）。 */
    private fun splitLine(line: String): List<String> = ImportRowMapper.splitLine(line)

    private fun toRow(cols: List<String>): ImportRow? = ImportRowMapper.toRow(cols)
}
