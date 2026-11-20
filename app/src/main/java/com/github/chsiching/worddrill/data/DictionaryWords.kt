package com.github.chsiching.worddrill.data

data class DictionaryWords(
    val source: String,
    val words: List<Entry>,
) {
    data class Entry(
        val word: String,
        /** UK variant; the US variant ships as [phoneticUs]. */
        val phonetic: String? = null,
        val phoneticUs: String? = null,
        val pos: String,
        val meaning: String,
    )
}
