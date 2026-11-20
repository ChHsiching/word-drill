package com.github.chsiching.worddrill.data.wordimport

data class ImportRow(
    val word: String,
    val phonetic: String? = null,
    val posMeaning: String? = null,
    /** True when the source row carried no pos/meaning columns at all (story 8):
     *  a word-only row imports the bare word even when the dictionary does not
     *  know it, instead of counting it as incomplete data. */
    val wordOnly: Boolean = false,
)
