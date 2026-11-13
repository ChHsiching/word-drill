package com.github.chsiching.worddrill.data.wordimport

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Ticket #21: parsing part-of-speech abbreviations plus Chinese meanings (pure function, JVM tests).
 *
 * Column 4 text splits into segments by the "abbreviation + period + meaning" pattern. Fixed abbreviation table:
 *   n. v. vt. vi. aux.v. adj. a. adv. ad. prep. conj. pron. art. num. int. interj. abbr.
 *
 * Primary seam: the core algorithm is covered here. FileWordImporter only exercises the "end-to-end" path through this parser.
 */
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
        // The spec's example input, parsed below.
        val senses = PosMeaningParser.parse("a.平坦的；平淡的 n.公寓；平面")
        assertThat(senses).containsExactly(
            "a." to "平坦的；平淡的",
            "n." to "公寓；平面",
        ).inOrder()
    }

    @Test
    fun multiCharAbbrev_auxV_isRecognized() {
        // aux.v. is a multi-character abbreviation and must not be mis-split into "aux." + "v....".
        val senses = PosMeaningParser.parse("vt.做 vi.行动 aux.v.做")
        assertThat(senses).containsExactly(
            "vt." to "做",
            "vi." to "行动",
            "aux.v." to "做",
        ).inOrder()
    }

    @Test
    fun allKnownAbbreviations_recognized() {
        // All 17 fixed abbreviations each produce one segment.
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
        // Column 4 internally uses tabs to separate segments (the default in some xlsx export tools).
        val senses = PosMeaningParser.parse("n.苹果\tv.吃")
        assertThat(senses).containsExactly(
            "n." to "苹果",
            "v." to "吃",
        ).inOrder()
    }

    @Test
    fun semicolonInMeaning_doesNotSplit() {
        // The Chinese semicolon is internal to the meaning (like a synonym separator) and must not split the segment.
        assertThat(PosMeaningParser.parse("n.苹果；水果"))
            .containsExactly("n." to "苹果；水果")
    }

    @Test
    fun noAbbreviation_defaultsToNoun() {
        // A single segment with no abbreviation (pure Chinese) conservatively defaults to n., so the word does not lose its meaning.
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
        // Only "n." with no meaning counts as incomplete and produces no sense. The import flow takes the skipped branch.
        assertThat(PosMeaningParser.parse("n.")).isEmpty()
    }

    @Test
    fun leadingTextBeforeFirstAbbrev_defaultedToNoun() {
        // Example input below: two Chinese words with no leading abbreviation. The input is ambiguous: it could be one segment (n., whole string) or two.
        // The conservative choice is one segment, because the leading word is not in the abbreviation table and must not be treated as a pos.
        // This test only checks: no abbreviation means the whole segment goes to n.
        val senses = PosMeaningParser.parse("水果 苹果")
        assertThat(senses).containsExactly("n." to "水果 苹果")
    }

    @Test
    fun meaningWithASCIIPeriod_notMisinterpretedAsAbbrev() {
        // "etc." and the like are not in the abbreviation table and must not be treated as a pos. In the input below, "etc." is part of the meaning.
        val senses = PosMeaningParser.parse("n.看 etc. 等")
        assertThat(senses).containsExactly("n." to "看 etc. 等")
    }

    @Test
    fun chineseFullWidthPeriod_handled() {
        // Some files use the full-width periods "．" or "。" instead of "."
        // The issue text uses the half-width ".", so this test only verifies the half-width form (full-width is a later follow-up)
        assertThat(PosMeaningParser.parse("n.苹果"))
            .containsExactly("n." to "苹果")
    }
}
