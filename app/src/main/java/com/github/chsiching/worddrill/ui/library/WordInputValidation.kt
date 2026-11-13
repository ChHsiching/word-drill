package com.github.chsiching.worddrill.ui.library

import com.github.chsiching.worddrill.R

/**
 * Word-entry input validation (pure function, no Android runtime dependency).
 *
 * Reuses the #6 trim-baseline lesson (pitfall B): the write path trims, so validation must
 * run on the trimmed value, otherwise "  " bypasses the non-empty check and becomes ""
 * after the write.
 *
 * @return Resource id of the error text (R.string.*); null means validation passed and the
 *   input can be submitted. The Composable layer resolves it with `stringResource(...)`
 *   (no hardcoded Chinese inside the VM/validation function, Ticket #12).
 */
internal fun validateAddWordInput(text: String, pos: String, meaning: String): Int? = when {
    text.trim().isEmpty() -> R.string.validation_word_empty
    pos.trim().isEmpty() -> R.string.validation_pos_empty
    meaning.trim().isEmpty() -> R.string.validation_meaning_empty
    else -> null
}

/** Validation when editing a sense (part of speech + meaning, both required). */
internal fun validateSenseEditInput(pos: String, meaning: String): Int? = when {
    pos.trim().isEmpty() -> R.string.validation_pos_empty
    meaning.trim().isEmpty() -> R.string.validation_meaning_empty
    else -> null
}
