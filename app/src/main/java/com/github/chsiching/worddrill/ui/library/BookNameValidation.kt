package com.github.chsiching.worddrill.ui.library

import com.github.chsiching.worddrill.R

/**
 * Name validation for creating/renaming a book (pure function, no Android runtime dependency).
 *
 * Rules (unrelated to [androidx.room.OnConflictStrategy]; the duplicate check must be explicit):
 * - Trim first: the write path also trims, so validation must use the same baseline,
 *   otherwise "CET-4 " bypasses the duplicate check
 * - Blank name (empty after trim): error
 * - Duplicate of an existing book name (case-sensitive, matching the SQLite default): error
 *
 * @param name the user-entered name (not trimmed).
 * @param existingNames names of existing books (compared case-sensitively).
 * @return Resource id of the error text (R.string.*); null means validation passed and the
 *   input can be submitted. The Composable layer resolves it with `stringResource(...)`
 *   (no hardcoded Chinese inside the validation function, Ticket #12).
 */
internal fun validateBookName(name: String, existingNames: List<String>): Int? {
    val trimmed = name.trim()
    return when {
        trimmed.isEmpty() -> R.string.validation_book_name_empty
        trimmed in existingNames -> R.string.validation_book_name_duplicate
        else -> null
    }
}
