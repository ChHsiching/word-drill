package com.github.chsiching.worddrill.data

/**
 * Ticket #19: in-memory model of the built-in dictionary (matches the assets/dictionary.json
 * structure).
 * Pure data class with no Android dependency, easy to construct in JVM unit tests.
 *
 * JSON structure (matches the tools/gen_dictionary_json.py output):
 * {
 *   "source": "ECDICT (MIT License, ...)",
 *   "words": [
 *     { "word": "apple", "phonetic": "/ˈæpl/", "pos": "n.", "meaning": "apple" },
 *     { "word": "apple", "phonetic": "/ˈæpl/", "pos": "v.", "meaning": "..." }
 *   ]
 * }
 *
 * Multiple parts of speech for the same word = multiple entries (same shape as the word/sense model).
 */
data class DictionaryWords(
    val source: String,
    val words: List<Entry>,
) {
    data class Entry(
        val word: String,
        val phonetic: String? = null,
        val pos: String,
        val meaning: String,
    )
}
