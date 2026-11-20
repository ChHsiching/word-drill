package com.github.chsiching.worddrill.data.wordimport

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.ByteArrayInputStream

class TextTableParserTest {

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
        assertThat(rows[2].phonetic).isNull()
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
        val text = "序号,单词,音标,释义\n1,apple,,n.苹果\n"
        val rows = TextTableParser.parse(stream(text), delimiter = ',')
        assertThat(rows).hasSize(2)
        assertThat(rows[0].word).isEqualTo("单词")
        assertThat(rows[1].word).isEqualTo("apple")
    }

    @Test
    fun csv_minimalTwoColumns_yieldsRowWithNullOptionals() {
        val text = "1,apple\n2,run\n"
        val rows = TextTableParser.parse(stream(text), delimiter = ',')
        assertThat(rows).containsExactly(
            ImportRow(word = "apple", wordOnly = true),
            ImportRow(word = "run", wordOnly = true),
        )
    }

    @Test
    fun csv_indexAndWordOnly_marksWordOnly() {
        // Story 8: a word-only row (no pos/meaning columns at all) carries the
        // flag so the importer can accept a bare unknown word.
        val rows = TextTableParser.parse(stream("1,flurbozz\n"), delimiter = ',')
        assertThat(rows.single().wordOnly).isTrue()
    }

    @Test
    fun csv_columnsPresentButEmpty_notWordOnly() {
        // Empty trailing columns are still columns: incomplete data, not a
        // word-only row.
        val rows = TextTableParser.parse(stream("1,test,,\n"), delimiter = ',')
        assertThat(rows.single().wordOnly).isFalse()
    }

    @Test
    fun txt_tabRow_indexAndWordOnly_marksWordOnly() {
        val rows = TextTableParser.parse(stream("1\tflurbozz\n"), delimiter = null)
        assertThat(rows.single().wordOnly).isTrue()
    }

    @Test
    fun csv_singleColumnRow_skippedBecauseCol0IsIndex() {
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
        val text = "1  apple  /p/  n.苹果\n"
        val rows = TextTableParser.parse(stream(text), delimiter = null)
        assertThat(rows.single()).isEqualTo(
            ImportRow(word = "apple", phonetic = "/p/", posMeaning = "n.苹果"),
        )
    }

    @Test
    fun txt_singleSpaceInWord_preserved() {
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
        val text = "1\tapple\t/p\tn.苹果\t多余1\t多余2\n"
        val rows = TextTableParser.parse(stream(text), delimiter = null)
        assertThat(rows.single().posMeaning).isEqualTo("n.苹果")
    }

    private fun stream(text: String) = ByteArrayInputStream(text.toByteArray(Charsets.UTF_8))
}
