package com.github.chsiching.worddrill.data.wordimport

object PosMeaningParser {

    private val ABBREVIATIONS: List<String> = listOf(
        "n.", "v.", "vt.", "vi.", "aux.v.", "adj.", "a.", "adv.", "ad.",
        "prep.", "conj.", "pron.", "art.", "num.", "int.", "interj.", "abbr.",
    )

    // Regex alternation is first-match, not longest-match: `aux.v.` must come before `aux.`
    // (and `adj.` before `a.`), or the longer abbreviation can never match.
    private val SORTED_ABBREVIATIONS: List<String> = ABBREVIATIONS.sortedByDescending { it.length }

    private val ABBREV_PATTERN: Regex = Regex(
        SORTED_ABBREVIATIONS.joinToString("|") { Regex.escape(it) }
    )

    /** Parses pos-prefixed meaning text (e.g. "a.flat; plain n.apartment"). Text with no
     *  recognized abbreviation defaults to "n."; an empty result means no valid sense. */
    fun parse(text: String): List<Pair<String, String>> {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return emptyList()

        val matches = ABBREV_PATTERN.findAll(trimmed).toList()
        if (matches.isEmpty()) {
            return listOf("n." to trimmed)
        }

        val result = mutableListOf<Pair<String, String>>()

        val leading = trimmed.substring(0, matches.first().range.first).trim()
        if (leading.isNotEmpty()) {
            result.add("n." to leading)
        }

        for (i in matches.indices) {
            val pos = matches[i].value
            val meaningStart = matches[i].range.last + 1
            val meaningEnd = if (i + 1 < matches.size) matches[i + 1].range.first else trimmed.length
            val meaning = trimmed.substring(meaningStart, meaningEnd).trim()
            if (meaning.isNotEmpty()) {
                result.add(pos to meaning)
            }
        }

        return result
    }
}
