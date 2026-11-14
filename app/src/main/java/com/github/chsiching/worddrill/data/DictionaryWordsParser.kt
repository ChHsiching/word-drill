package com.github.chsiching.worddrill.data

import org.json.JSONObject

object DictionaryWordsParser {

    fun parse(json: String): DictionaryWords {
        val root = JSONObject(json)
        val source = root.optString("source", "")
        val wordsJson = root.getJSONArray("words")
        val words = buildList {
            for (i in 0 until wordsJson.length()) {
                val w = wordsJson.getJSONObject(i)
                // Android's org.json.optString(key, "") returns the string "null" on a JSON null,
                // so guard with isNull first.
                val phonetic = if (w.isNull("phonetic")) {
                    null
                } else {
                    w.optString("phonetic", "").ifBlank { null }
                }
                add(
                    DictionaryWords.Entry(
                        word = w.getString("word"),
                        phonetic = phonetic,
                        pos = w.getString("pos"),
                        meaning = w.getString("meaning"),
                    )
                )
            }
        }
        return DictionaryWords(source = source, words = words)
    }
}
