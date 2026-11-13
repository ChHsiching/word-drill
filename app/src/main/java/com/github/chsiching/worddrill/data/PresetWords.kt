package com.github.chsiching.worddrill.data

/**
 * In-memory model of the preset word library (matches the assets/words.json structure).
 * Pure data class with no Android dependency, easy to construct in JVM unit tests.
 *
 * JSON structure:
 * {
 *   "source": "ECDICT (MIT License, ...)",
 *   "books": [
 *     { "name": "CET-4", "words": [
 *        { "text": "apple", "phonetic": "/ˈæpl/", "senses": [ {"pos":"n.","meaning":"apple"} ] }
 *     ]}
 *   ]
 * }
 *
 * Ticket #14: phonetic is nullable (some preset words lack a phonetic in the dictionary, and
 * user-entered words have none).
 */
data class PresetWords(
    val source: String,
    val books: List<Book>,
) {
    data class Book(
        val name: String,
        val words: List<Word>,
    )

    data class Word(
        val text: String,
        val phonetic: String? = null,
        val senses: List<Sense>,
    )

    data class Sense(
        val pos: String,
        val meaning: String,
    )
}
