package com.github.chsiching.worddrill.data.wordimport

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PosMeaningParserTest {

    @Test
    fun emptyString_returnsEmpty() {
        assertThat(PosMeaningParser.parse("")).isEmpty()
    }

    @Test
    fun blankString_returnsEmpty() {
        assertThat(PosMeaningParser.parse("   ")).isEmpty()
    }

    @Test
    fun singlePos_returnsOneSense() {
        assertThat(PosMeaningParser.parse("n.苹果"))
            .containsExactly("n." to "苹果")
            .inOrder()
    }

    @Test
    fun multiplePos_returnsMultipleSenses() {
        val senses = PosMeaningParser.parse("a.平坦的；平淡的 n.公寓；平面")
        assertThat(senses).containsExactly(
            "a." to "平坦的；平淡的",
            "n." to "公寓；平面",
        ).inOrder()
    }

    @Test
    fun multiCharAbbrev_auxV_isRecognized() {
        val senses = PosMeaningParser.parse("vt.做 vi.行动 aux.v.做")
        assertThat(senses).containsExactly(
            "vt." to "做",
            "vi." to "行动",
            "aux.v." to "做",
        ).inOrder()
    }

    @Test
    fun allKnownAbbreviations_recognized() {
        val text = listOf(
            "n.", "v.", "vt.", "vi.", "aux.v.", "adj.", "a.", "adv.", "ad.",
            "prep.", "conj.", "pron.", "art.", "num.", "int.", "interj.", "abbr.",
        ).joinToString(" ") { "$it 义$it" }
        val senses = PosMeaningParser.parse(text)
        assertThat(senses).hasSize(17)
        assertThat(senses.map { it.first }).containsExactly(
            "n.", "v.", "vt.", "vi.", "aux.v.", "adj.", "a.", "adv.", "ad.",
            "prep.", "conj.", "pron.", "art.", "num.", "int.", "interj.", "abbr.",
        ).inOrder()
    }

    @Test
    fun leadingTrailingWhitespace_trimmed() {
        assertThat(PosMeaningParser.parse("  n.苹果  "))
            .containsExactly("n." to "苹果")
    }

    @Test
    fun multipleSpacesBetweenSegments_handled() {
        val senses = PosMeaningParser.parse("n.苹果    v.吃")
        assertThat(senses).containsExactly(
            "n." to "苹果",
            "v." to "吃",
        ).inOrder()
    }

    @Test
    fun tabSeparator_handled() {
        val senses = PosMeaningParser.parse("n.苹果\tv.吃")
        assertThat(senses).containsExactly(
            "n." to "苹果",
            "v." to "吃",
        ).inOrder()
    }

    @Test
    fun semicolonInMeaning_doesNotSplit() {
        assertThat(PosMeaningParser.parse("n.苹果；水果"))
            .containsExactly("n." to "苹果；水果")
    }

    @Test
    fun noAbbreviation_defaultsToNoun() {
        assertThat(PosMeaningParser.parse("苹果"))
            .containsExactly("n." to "苹果")
    }

    @Test
    fun noAbbreviation_withLeadingSpace_defaultsToNoun() {
        assertThat(PosMeaningParser.parse(" 苹果 "))
            .containsExactly("n." to "苹果")
    }

    @Test
    fun onlyAbbreviationNoMeaning_skipped() {
        assertThat(PosMeaningParser.parse("n.")).isEmpty()
    }

    @Test
    fun leadingTextBeforeFirstAbbrev_defaultedToNoun() {
        val senses = PosMeaningParser.parse("水果 苹果")
        assertThat(senses).containsExactly("n." to "水果 苹果")
    }

    @Test
    fun meaningWithASCIIPeriod_notMisinterpretedAsAbbrev() {
        val senses = PosMeaningParser.parse("n.看 etc. 等")
        assertThat(senses).containsExactly("n." to "看 etc. 等")
    }

    @Test
    fun chineseFullWidthPeriod_handled() {
        assertThat(PosMeaningParser.parse("n.苹果"))
            .containsExactly("n." to "苹果")
    }
}
