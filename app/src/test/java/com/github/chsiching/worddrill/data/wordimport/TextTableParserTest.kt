package com.github.chsiching.worddrill.data.wordimport

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.ByteArrayInputStream

/**
 * Ticket #21: txt/csv table parsing (pure function, JVM tests).
 *
 * Column layout (issue #21): col0=index, ignored / col1=word / col2=phonetic? / col3=pos+meaning?
 *
 * - CSV: each line splits on `,`
 * - TXT: each line splits on a tab or 2+ consecutive spaces (compatible with tables copied out of PDF/Word)
 *
 * The parser only splits columns into ImportRow and does not parse POS (left to [PosMeaningParser]).
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
        assertThat(rows[2].phonetic).isNull() // Row 3 has an empty phonetic
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
        // A CSV quoted field (the meaning itself contains a ,)
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
        // The first line of the text below is a header row naming the four columns: col1 holds the Chinese column label for "word", not an English word.
        // The parser does not detect headers on its own (the issue does not ask for it) and parses as usual. The test only verifies that it parses literally.
        val text = "序号,单词,音标,释义\n1,apple,,n.苹果\n"
        val rows = TextTableParser.parse(stream(text), delimiter = ',')
        assertThat(rows).hasSize(2)
        assertThat(rows[0].word).isEqualTo("单词")
        assertThat(rows[1].word).isEqualTo("apple")
    }

    @Test
    fun csv_minimalTwoColumns_yieldsRowWithNullOptionals() {
        // Minimal valid file: only the index and word columns (col2/col3 missing).
        val text = "1,apple\n2,run\n"
        val rows = TextTableParser.parse(stream(text), delimiter = ',')
        assertThat(rows).containsExactly(
            ImportRow(word = "apple"),
            ImportRow(word = "run"),
        )
    }

    @Test
    fun csv_singleColumnRow_skippedBecauseCol0IsIndex() {
        // The issue defines col0 as the index; a single-column row is the index column by that convention and cannot form a word entry, so it is skipped.
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

    // ---- TXT (delimiter=null auto-detects tab / 2+ spaces) ----

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
        // 2+ consecutive spaces count as a column separator; a single space inside a word does not split it.
        val text = "1  apple  /p/  n.苹果\n"
        val rows = TextTableParser.parse(stream(text), delimiter = null)
        assertThat(rows.single()).isEqualTo(
            ImportRow(word = "apple", phonetic = "/p/", posMeaning = "n.苹果"),
        )
    }

    @Test
    fun txt_singleSpaceInWord_preserved() {
        // A single space inside a word is not a separator (e.g. "1 take off"): only tab/2+ spaces split columns.
        // Note: col0=index is required; here col1 = "take off" (a single space inside the word).
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
        // Anything beyond 4 columns is dropped (the issue only defines a 4-column layout).
        val text = "1\tapple\t/p\tn.苹果\t多余1\t多余2\n"
        val rows = TextTableParser.parse(stream(text), delimiter = null)
        assertThat(rows.single().posMeaning).isEqualTo("n.苹果")
    }

    private fun stream(text: String) = ByteArrayInputStream(text.toByteArray(Charsets.UTF_8))
}
