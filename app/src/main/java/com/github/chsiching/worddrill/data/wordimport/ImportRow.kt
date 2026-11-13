package com.github.chsiching.worddrill.data.wordimport

/**
 * Ticket #21: unified intermediate model after parsing any file format (one word per row).
 *
 * Fields map to issue #21's column layout (minus the index column):
 * - [word]: the word (col 1, required; parsers may still leave it empty, [FileWordImporter] decides to skip)
 * - [phonetic]: phonetic (col 2, nullable)
 * - [posMeaning]: raw pos + meaning text (col 3, nullable; parsed on demand by [PosMeaningParser] during import)
 *
 * Design: [posMeaning] keeps the raw text rather than a parsed senses list, so the parsers
 * (XLSX/TXT/PDF) keep a single responsibility ("extract text") and POS splitting is left to
 * [FileWordImporter] when the dictionary lookup misses. That way col 3 never needs parsing on a
 * dictionary hit, saving one step.
 */
data class ImportRow(
    val word: String,
    val phonetic: String? = null,
    val posMeaning: String? = null,
)
