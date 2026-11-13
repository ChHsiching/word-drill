package com.github.chsiching.worddrill.data.wordimport

/**
 * Ticket #21: file import completion statistics.
 *
 * - [success]: words imported successfully (both dictionary hits and the file cols 3/4 fallback)
 * - [skipped]: words skipped due to incomplete data (empty word / no dictionary entry with cols 3+4 also empty / POS parse yielding nothing)
 *
 * Deduplication is not counted: duplicate words are silently merged by the word table unique
 * index + IGNORE; the issue explicitly says "dedupe without prompting".
 */
data class ImportSummary(
    val success: Int,
    val skipped: Int,
)
