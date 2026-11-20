package com.github.chsiching.worddrill.data.local

/** Phonetic variants of one word for drill-card display (GLOSSARY.md, phonetic
 *  preference): the dictionary entry's UK/US pair, with the word row's own
 *  phonetic as the UK fallback for words the dictionary does not carry. */
data class WordPhonetics(
    val wordId: Long,
    val phoneticUk: String?,
    val phoneticUs: String?,
)
