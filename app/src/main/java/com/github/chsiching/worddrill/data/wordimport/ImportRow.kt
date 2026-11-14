package com.github.chsiching.worddrill.data.wordimport

data class ImportRow(
    val word: String,
    val phonetic: String? = null,
    val posMeaning: String? = null,
)
