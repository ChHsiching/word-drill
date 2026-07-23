package com.github.chsiching.worddrill.ui.library

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * 新增词条 / 编辑义项的输入校验（纯函数，无 Android 依赖）。
 *
 * 规格（Ticket #7 acceptance）：
 * - 新增词条：输入单词、词性、释义，三者为空之一则拒绝
 * - 编辑义项：修改词性/释义，二者为空之一则拒绝
 *
 * 校验必须在 trim 后的基准上做（沿用 #6 坑 B 的教训：写库会 trim，
 * 校验不 trim 则 "  " 能绕过非空约束，写库后变 ""）。
 */
class WordInputValidationTest {

    // ---- 新增词条：text + pos + meaning ----

    @Test
    fun addWord_allFilled_isValid() {
        assertThat(validateAddWordInput("apple", "n.", "苹果")).isNull()
    }

    @Test
    fun addWord_blankText_isInvalid() {
        assertThat(validateAddWordInput("   ", "n.", "苹果")).isNotNull()
    }

    @Test
    fun addWord_blankPos_isInvalid() {
        assertThat(validateAddWordInput("apple", "  ", "苹果")).isNotNull()
    }

    @Test
    fun addWord_blankMeaning_isInvalid() {
        assertThat(validateAddWordInput("apple", "n.", "  ")).isNotNull()
    }

    @Test
    fun addWord_paddedValidInput_isValid_afterTrim() {
        // 写库会 trim，校验在 trim 后的基准上判 → 通过
        assertThat(validateAddWordInput("  apple  ", "  n.  ", "  苹果  ")).isNull()
    }

    // ---- 编辑义项：pos + meaning ----

    @Test
    fun editSense_bothFilled_isValid() {
        assertThat(validateSenseEditInput("v.", "运行")).isNull()
    }

    @Test
    fun editSense_blankPos_isInvalid() {
        assertThat(validateSenseEditInput("  ", "运行")).isNotNull()
    }

    @Test
    fun editSense_blankMeaning_isInvalid() {
        assertThat(validateSenseEditInput("v.", "  ")).isNotNull()
    }
}
