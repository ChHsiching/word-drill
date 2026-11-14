package com.github.chsiching.worddrill.ui.library

import com.github.chsiching.worddrill.R
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BookNameValidationTest {

    private val existingNames = listOf("CET-4", "CET-6", "考研")

    @Test
    fun emptyName_isInvalid() {
        assertThat(validateBookName("", existingNames))
            .isEqualTo(R.string.validation_book_name_empty)
    }

    @Test
    fun blankName_isInvalid() {
        assertThat(validateBookName("   ", existingNames))
            .isEqualTo(R.string.validation_book_name_empty)
    }

    @Test
    fun duplicateName_isInvalid() {
        assertThat(validateBookName("CET-4", existingNames))
            .isEqualTo(R.string.validation_book_name_duplicate)
    }

    @Test
    fun newName_isValid() {
        assertThat(validateBookName("我的生词本", existingNames)).isNull()
    }

    @Test
    fun nameDifferingCase_isValid() {
        assertThat(validateBookName("cet-4", existingNames)).isNull()
    }

    @Test
    fun paddedDuplicate_isInvalid_afterTrim() {
        assertThat(validateBookName("  CET-4  ", existingNames))
            .isEqualTo(R.string.validation_book_name_duplicate)
    }

    @Test
    fun paddedNewName_isValid_afterTrim() {
        assertThat(validateBookName("  我的生词本  ", existingNames)).isNull()
    }
}
