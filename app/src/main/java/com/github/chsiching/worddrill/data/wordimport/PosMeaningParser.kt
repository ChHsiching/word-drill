package com.github.chsiching.worddrill.data.wordimport

/**
 * Ticket #21: pos abbreviation + Chinese meaning parser (pure function).
 *
 * Column 4 text (e.g. `"a.flat; plain n.apartment; plane"` in real data) -> `List<Pair<pos, meaning>>`.
 *
 * Fixed abbreviation list (verbatim from issue #21): each abbreviation ends with a half-width `.`:
 *   n. v. vt. vi. aux.v. adj. a. adv. ad. prep. conj. pron. art. num. int. interj. abbr.
 *
 * Parsing strategy:
 * 1. Use a regex to find every occurrence of a known abbreviation in the text; sorted by
 *    position, they act as split anchors. Abbreviations match in **descending length order**
 *    (`aux.v.` before `aux.`, `adj.` before `a.`) to avoid splitting on a short prefix by mistake.
 * 2. Each segment = the anchor abbreviation + the meaning text up to the next anchor (or end of
 *    string), trimmed into (pos, meaning).
 * 3. Text before the first anchor (if any): when non-empty after trim it is treated as a bare
 *    meaning with no abbreviation and assigned the default `n.` (conservative choice; the issue
 *    does not specify, and this avoids losing the word's meaning).
 * 4. Abbreviation with no meaning (e.g. a bare `"n."`) -> skipped.
 *
 * Note: abbreviations inside a meaning are still recognized (e.g. "n.watch etc. and so on" is
 * not split, because `etc.` is not in the list). The fixed abbreviation list is what guarantees
 * natural text is never split by mistake.
 */
object PosMeaningParser {

    /** Fixed abbreviation list (verbatim from issue #21, 17 entries). */
    private val ABBREVIATIONS: List<String> = listOf(
        "n.", "v.", "vt.", "vi.", "aux.v.", "adj.", "a.", "adv.", "ad.",
        "prep.", "conj.", "pron.", "art.", "num.", "int.", "interj.", "abbr.",
    )

    /**
     * Abbreviation list sorted by descending length, used to build the [Regex].
     *
     * Must be descending: `aux.v.` (5 chars) must match before `aux.` (4 chars), and `adj.` (4)
     * before `a.` (2). Otherwise the regex alternation `aux.v.|aux.|adj.|a.` matches `aux.` first
     * on "aux.v.do", leaving `v.do` in the meaning segment, which is wrong.
     */
    private val SORTED_ABBREVIATIONS: List<String> = ABBREVIATIONS.sortedByDescending { it.length }

    /** Escapes regex metacharacters (`.` -> `\.`). The abbreviations only contain `.`, so escaping is safe. */
    private val ABBREV_PATTERN: Regex = Regex(
        SORTED_ABBREVIATIONS.joinToString("|") { Regex.escape(it) }
    )

    /**
     * Parse column 4 text into a list of (pos, meaning).
     *
     * @return An empty list means the text has no valid sense (callers should count it as "skipped for incomplete data").
     */
    fun parse(text: String): List<Pair<String, String>> {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return emptyList()

        val matches = ABBREV_PATTERN.findAll(trimmed).toList()
        if (matches.isEmpty()) {
            // No abbreviation in the whole segment -> default n.
            return listOf("n." to trimmed)
        }

        val result = mutableListOf<Pair<String, String>>()

        // Text before the first anchor (non-empty after trim) -> default n.
        val leading = trimmed.substring(0, matches.first().range.first).trim()
        if (leading.isNotEmpty()) {
            result.add("n." to leading)
        }

        // Segment by segment: anchor abbreviation + meaning up to the next anchor (or end of string)
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
