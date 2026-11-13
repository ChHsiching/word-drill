package com.github.chsiching.worddrill.ui.library

import com.github.chsiching.worddrill.R
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Name validation when creating or renaming a word book (pure function, no Android runtime dependency).
 *
 * Spec:
 * - Enter a name when creating a book (is_preset = false)
 * - Rename a book
 * - Duplicate names are not allowed (handoff-chain pitfall C: @Insert(REPLACE)+autoGenerate does not dedupe by name, so an explicit duplicate check is needed)
 *
 * The logic is a pure function so JVM tests can cover it, with no Android runtime dependency and no Room/DataStore coupling.
 *
 * Ticket #12: the return type changed from String? to @StringRes Int?, so tests assert the resource id.
 */
class BookNameValidationTest {

    private val existingNames = listOf("CET-4", "CET-6", "考研")

    // ---- Empty name, report error ----

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

    // ---- Duplicate name, report error (case-sensitive, matching the SQLite default) ----

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
        // SQLite is case-sensitive by default (text LIKE and comparisons distinguish case).
        // "cet-4" and "CET-4" are two different names, so this is not a duplicate.
        assertThat(validateBookName("cet-4", existingNames)).isNull()
    }

    // ---- Regression: duplicate check on the trimmed value (prevents "CET-4 " from bypassing validation and being stored as "CET-4") ----

    @Test
    fun paddedDuplicate_isInvalid_afterTrim() {
        // The write path trims, so validation must judge the trimmed value, otherwise a padded duplicate slips through.
        assertThat(validateBookName("  CET-4  ", existingNames))
            .isEqualTo(R.string.validation_book_name_duplicate)
    }

    @Test
    fun paddedNewName_isValid_afterTrim() {
        // Padded but not a duplicate after trim, so it passes. The write trims the padded value.
        assertThat(validateBookName("  我的生词本  ", existingNames)).isNull()
    }
}
