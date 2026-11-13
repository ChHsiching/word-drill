package com.github.chsiching.worddrill.data

import org.json.JSONObject

/**
 * Ticket #19: parse assets/dictionary.json text into [DictionaryWords].
 * Uses Android's built-in org.json (no new dependency).
 *
 * The parsing logic is a standalone pure function, easy to verify in JVM unit tests by feeding
 * in small JSON strings (same pattern as [PresetWordsParser]).
 */
object DictionaryWordsParser {

    fun parse(json: String): DictionaryWords {
        val root = JSONObject(json)
        val source = root.optString("source", "")
        val wordsJson = root.getJSONArray("words")
        val words = buildList {
            for (i in 0 until wordsJson.length()) {
                val w = wordsJson.getJSONObject(i)
                // phonetic is nullable, and JSON null must be guarded with w.isNull first (same
                // trap as PresetWordsParser: org.json.optString(key, "") returns the string "null"
                // on a JSON null).
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
