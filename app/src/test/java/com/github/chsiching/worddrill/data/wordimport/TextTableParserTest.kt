package com.github.chsiching.worddrill.data.wordimport

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.ByteArrayInputStream

/**
 * Ticket #21：txt/csv 表格解析（纯函数，JVM 单测）。
 *
 * 列结构（issue #21）：col0=序号忽略 / col1=单词 / col2=音标? / col3=词性+释义?
 *
 * - CSV：每行按 `,` 切分
 * - TXT：每行按 tab 或 2+ 连续空格切分（兼容复制粘贴的 PDF/Word 表格）
 *
 * 解析器只负责"切列 → ImportRow"，不解析 POS（留给 [PosMeaningParser]）。
 */
class TextTableParserTest {

    // ---- CSV ----

    @Test
    fun csv_fourColumns_parsesAllFields() {
        val text = "1,apple,/ˈæpl/,n.苹果\n"
        val rows = TextTableParser.parse(stream(text), delimiter = ',')
        assertThat(rows).containsExactly(
            ImportRow(word = "apple", phonetic = "/ˈæpl/", posMeaning = "n.苹果"),
        )
    }

    @Test
    fun csv_multipleLines_parsesAllRows() {
        val text = """
            1,apple,/p1/,n.苹果
            2,run,/p2/,vi.跑
            3,test,,n.测试
        """.trimIndent() + "\n"
        val rows = TextTableParser.parse(stream(text), delimiter = ',')
        assertThat(rows).hasSize(3)
        assertThat(rows.map { it.word }).containsExactly("apple", "run", "test")
        assertThat(rows[2].phonetic).isNull() // 第 3 行音标空
        assertThat(rows[2].posMeaning).isEqualTo("n.测试")
    }

    @Test
    fun csv_emptyPhoneticColumn_yieldsNull() {
        val text = "1,test,,n.测试\n"
        val rows = TextTableParser.parse(stream(text), delimiter = ',')
        assertThat(rows.single().phonetic).isNull()
    }

    @Test
    fun csv_emptyPosMeaningColumn_yieldsNull() {
        val text = "1,test,/p/,\n"
        val rows = TextTableParser.parse(stream(text), delimiter = ',')
        assertThat(rows.single().posMeaning).isNull()
    }

    @Test
    fun csv_quotedField_isSupported() {
        // CSV 引号包裹的 field（释义内本身含 ,）
        val text = "1,test,/p/,\"n.苹果, 水果\"\n"
        val rows = TextTableParser.parse(stream(text), delimiter = ',')
        assertThat(rows.single().posMeaning).isEqualTo("n.苹果, 水果")
    }

    @Test
    fun csv_skipsBlankLines() {
        val text = "\n1,a,,n.甲\n\n2,b,,n.乙\n\n"
        val rows = TextTableParser.parse(stream(text), delimiter = ',')
        assertThat(rows.map { it.word }).containsExactly("a", "b")
    }

    @Test
    fun csv_skipsHeaderIfWordColumnNotOneToken() {
        // 第一行 "序号,单词,音标,释义" 这种表头：col1 是 "单词" 而非英文词，
        // 解析器不主动判断表头（issue 没说），照常解析。测试只验证：照实解析即可。
        val text = "序号,单词,音标,释义\n1,apple,,n.苹果\n"
        val rows = TextTableParser.parse(stream(text), delimiter = ',')
        assertThat(rows).hasSize(2)
        assertThat(rows[0].word).isEqualTo("单词")
        assertThat(rows[1].word).isEqualTo("apple")
    }

    @Test
    fun csv_minimalTwoColumns_yieldsRowWithNullOptionals() {
        // 最简合法文件：只有序号 + 单词两列（col2/col3 缺）
        val text = "1,apple\n2,run\n"
        val rows = TextTableParser.parse(stream(text), delimiter = ',')
        assertThat(rows).containsExactly(
            ImportRow(word = "apple"),
            ImportRow(word = "run"),
        )
    }

    @Test
    fun csv_singleColumnRow_skippedBecauseCol0IsIndex() {
        // issue 规定 col0 是序号；只有一列时按规约是序号列，无法构成词条 → 跳过
        val text = "apple\n2,run\n"
        val rows = TextTableParser.parse(stream(text), delimiter = ',')
        assertThat(rows.map { it.word }).containsExactly("run")
    }

    @Test
    fun csv_skipsRowWithBlankWord() {
        val text = "1,,,n.空单词\n2,apple,,n.苹果\n"
        val rows = TextTableParser.parse(stream(text), delimiter = ',')
        assertThat(rows.map { it.word }).containsExactly("apple")
    }

    @Test
    fun csv_trimsEachField() {
        val text = "1, apple , /p/ , n.苹果 \n"
        val rows = TextTableParser.parse(stream(text), delimiter = ',')
        val row = rows.single()
        assertThat(row.word).isEqualTo("apple")
        assertThat(row.phonetic).isEqualTo("/p/")
        assertThat(row.posMeaning).isEqualTo("n.苹果")
    }

    // ---- TXT（delimiter=null 自动识别 tab / 2+ 空格）----

    @Test
    fun txt_tabSeparated_parsesCorrectly() {
        val text = "1\tapple\t/p/\tn.苹果\n"
        val rows = TextTableParser.parse(stream(text), delimiter = null)
        assertThat(rows.single()).isEqualTo(
            ImportRow(word = "apple", phonetic = "/p/", posMeaning = "n.苹果"),
        )
    }

    @Test
    fun txt_multiSpaceSeparated_parsesCorrectly() {
        // 2+ 连续空格视为列分隔；单词内部的单词不会被空格切开（只 1 个空格不算分隔）
        val text = "1  apple  /p/  n.苹果\n"
        val rows = TextTableParser.parse(stream(text), delimiter = null)
        assertThat(rows.single()).isEqualTo(
            ImportRow(word = "apple", phonetic = "/p/", posMeaning = "n.苹果"),
        )
    }

    @Test
    fun txt_singleSpaceInWord_preserved() {
        // 单词内部单个空格不视为分隔（如 "1 take off"）：tab/2+ 空格才切列。
        // 注：col0=序号必须有；这里 col1 = "take off"（单词内部单空格）。
        val text = "1\ttake off\t\tn.\n"
        val rows = TextTableParser.parse(stream(text), delimiter = null)
        assertThat(rows.single().word).isEqualTo("take off")
    }

    @Test
    fun txt_emptyStream_returnsEmptyList() {
        val rows = TextTableParser.parse(stream(""), delimiter = null)
        assertThat(rows).isEmpty()
    }

    @Test
    fun txt_handlesCrlfLineEndings() {
        val text = "1\ta\t\tn.甲\r\n2\tb\t\tn.乙\r\n"
        val rows = TextTableParser.parse(stream(text), delimiter = null)
        assertThat(rows.map { it.word }).containsExactly("a", "b")
    }

    @Test
    fun txt_extraColumnsBeyondFour_ignored() {
        // 超过 4 列的部分丢弃（issue 只规定 4 列结构）
        val text = "1\tapple\t/p\tn.苹果\t多余1\t多余2\n"
        val rows = TextTableParser.parse(stream(text), delimiter = null)
        assertThat(rows.single().posMeaning).isEqualTo("n.苹果")
    }

    private fun stream(text: String) = ByteArrayInputStream(text.toByteArray(Charsets.UTF_8))
}
