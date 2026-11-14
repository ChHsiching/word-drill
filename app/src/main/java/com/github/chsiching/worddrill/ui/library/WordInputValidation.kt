package com.github.chsiching.worddrill.ui.library

import com.github.chsiching.worddrill.R

internal fun validateAddWordInput(text: String, pos: String, meaning: String): Int? = when {
    text.trim().isEmpty() -> R.string.validation_word_empty
    pos.trim().isEmpty() -> R.string.validation_pos_empty
    meaning.trim().isEmpty() -> R.string.validation_meaning_empty
    else -> null
}

internal fun validateSenseEditInput(pos: String, meaning: String): Int? = when {
    pos.trim().isEmpty() -> R.string.validation_pos_empty
    meaning.trim().isEmpty() -> R.string.validation_meaning_empty
    else -> null
}
