package com.github.chsiching.worddrill.ui.library

import com.github.chsiching.worddrill.R
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class WordInputValidationTest {

    @Test
    fun addWord_allFilled_isValid() {
        assertThat(validateAddWordInput("apple", "n.", "苹果")).isNull()
    }

    @Test
    fun addWord_blankText_isInvalid() {
        assertThat(validateAddWordInput("   ", "n.", "苹果"))
            .isEqualTo(R.string.validation_word_empty)
    }

    @Test
    fun addWord_blankPos_isInvalid() {
        assertThat(validateAddWordInput("apple", "  ", "苹果"))
            .isEqualTo(R.string.validation_pos_empty)
    }

    @Test
    fun addWord_blankMeaning_isInvalid() {
        assertThat(validateAddWordInput("apple", "n.", "  "))
            .isEqualTo(R.string.validation_meaning_empty)
    }

    @Test
    fun addWord_paddedValidInput_isValid_afterTrim() {
        assertThat(validateAddWordInput("  apple  ", "  n.  ", "  苹果  ")).isNull()
    }

    @Test
    fun editSense_bothFilled_isValid() {
        assertThat(validateSenseEditInput("v.", "运行")).isNull()
    }

    @Test
    fun editSense_blankPos_isInvalid() {
        assertThat(validateSenseEditInput("  ", "运行"))
            .isEqualTo(R.string.validation_pos_empty)
    }

    @Test
    fun editSense_blankMeaning_isInvalid() {
        assertThat(validateSenseEditInput("v.", "  "))
            .isEqualTo(R.string.validation_meaning_empty)
    }
}
