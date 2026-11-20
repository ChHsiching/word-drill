package com.github.chsiching.worddrill.data.wordimport

data class ImportSummary(
    val success: Int,
    val skipped: Int,
    /** Up to a few skipped words, so the summary can name them instead of only
     *  counting. Shorter than the full skip list by design. */
    val skippedWords: List<String> = emptyList(),
)
