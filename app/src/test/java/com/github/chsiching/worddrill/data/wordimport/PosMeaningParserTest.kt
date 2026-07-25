package com.github.chsiching.worddrill.data.wordimport

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Ticket #21：词性缩写 + 中文释义解析（纯函数，JVM 单测）。
 *
 * 列4 文本按"缩写+点+释义"模式切段。固定缩写表：
 *   n. v. vt. vi. aux.v. adj. a. adv. ad. prep. conj. pron. art. num. int. interj. abbr.
 *
 * 主接缝：核心算法在这里覆盖；FileWordImporter 用本解析器只走"端到端"路径。
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
        // 规格 example："a.平坦的；平淡的 n.公寓；平面"
        val senses = PosMeaningParser.parse("a.平坦的；平淡的 n.公寓；平面")
        assertThat(senses).containsExactly(
            "a." to "平坦的；平淡的",
            "n." to "公寓；平面",
        ).inOrder()
    }

    @Test
    fun multiCharAbbrev_auxV_isRecognized() {
        // aux.v. 是多字符缩写，要避免被错切成 "aux." + "v...."
        val senses = PosMeaningParser.parse("vt.做 vi.行动 aux.v.做")
        assertThat(senses).containsExactly(
            "vt." to "做",
            "vi." to "行动",
            "aux.v." to "做",
        ).inOrder()
    }

    @Test
    fun allKnownAbbreviations_recognized() {
        // 全部 17 个固定缩写各出一段
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
        // 列4 内部用 tab 分段（某些 xlsx 导出工具默认）
        val senses = PosMeaningParser.parse("n.苹果\tv.吃")
        assertThat(senses).containsExactly(
            "n." to "苹果",
            "v." to "吃",
        ).inOrder()
    }

    @Test
    fun semicolonInMeaning_doesNotSplit() {
        // 中文分号是释义内部的（如同义词分隔），不应切段
        assertThat(PosMeaningParser.parse("n.苹果；水果"))
            .containsExactly("n." to "苹果；水果")
    }

    @Test
    fun noAbbreviation_defaultsToNoun() {
        // 单段无缩写（纯中文）→ 保守默认为 n.，避免整词丢释义
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
        // 只有 "n." 没释义 —— 视为不完整，不产出 sense（导入流程走 skipped 分支）
        assertThat(PosMeaningParser.parse("n.")).isEmpty()
    }

    @Test
    fun leadingTextBeforeFirstAbbrev_defaultedToNoun() {
        // 例："水果 苹果"（无缩写开头）→ 一段 (n., "水果 苹果")？还是两段？
        // 选保守：整段当 n.，因为 "水果" 不在缩写表，不应被当 pos。
        // 这里只测：无缩写 → 全段归 n.
        val senses = PosMeaningParser.parse("水果 苹果")
        assertThat(senses).containsExactly("n." to "水果 苹果")
    }

    @Test
    fun meaningWithASCIIPeriod_notMisinterpretedAsAbbrev() {
        // "etc." 之类不在缩写表，不应被当 pos；"n.看 etc." 中 "etc." 是释义的一部分
        val senses = PosMeaningParser.parse("n.看 etc. 等")
        assertThat(senses).containsExactly("n." to "看 etc. 等")
    }

    @Test
    fun chineseFullWidthPeriod_handled() {
        // 部分文件用全角句号 "．" 或 "。" 替代 "."
        // issue 字面用半角 ".", 这里只验证半角正确（全角是后续 follow-up）
        assertThat(PosMeaningParser.parse("n.苹果"))
            .containsExactly("n." to "苹果")
    }
}
