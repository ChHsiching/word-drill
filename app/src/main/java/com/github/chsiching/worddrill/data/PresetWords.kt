package com.github.chsiching.worddrill.data

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
