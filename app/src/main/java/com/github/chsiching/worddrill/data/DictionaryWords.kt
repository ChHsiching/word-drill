package com.github.chsiching.worddrill.data

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
