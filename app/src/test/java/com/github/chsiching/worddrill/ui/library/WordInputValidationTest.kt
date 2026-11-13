package com.github.chsiching.worddrill.ui.library

import com.github.chsiching.worddrill.R
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Input validation for adding a word or editing a sense (pure functions, no Android runtime dependency).
 *
 * Spec (Ticket #7 acceptance):
 * - Add word: word, part of speech, and meaning are all inputs; reject if any of the three is blank
 * - Edit sense: part of speech and meaning are the inputs; reject if either of the two is blank
 *
 * Validation must run on the trimmed value (lesson carried over from pitfall B in #6: the write path trims,
 * so without trimming in validation "  " bypasses the non-blank check and becomes "" after the write).
 *
 * Ticket #12: the return type changed from String? to @StringRes Int?, so tests assert the resource id.
 */
class WordInputValidationTest {

    // ---- Add word: text + pos + meaning ----

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
        // The write path trims, and validation judges the trimmed value, so this passes.
        assertThat(validateAddWordInput("  apple  ", "  n.  ", "  苹果  ")).isNull()
    }

    // ---- Edit sense: pos + meaning ----

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
