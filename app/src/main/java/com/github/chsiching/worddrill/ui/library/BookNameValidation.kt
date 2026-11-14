package com.github.chsiching.worddrill.ui.library

import com.github.chsiching.worddrill.R

internal fun validateBookName(name: String, existingNames: List<String>): Int? {
    val trimmed = name.trim()
    return when {
        trimmed.isEmpty() -> R.string.validation_book_name_empty
        trimmed in existingNames -> R.string.validation_book_name_duplicate
        else -> null
    }
}
