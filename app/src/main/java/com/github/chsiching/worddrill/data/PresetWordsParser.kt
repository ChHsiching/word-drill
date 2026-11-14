package com.github.chsiching.worddrill.data

import org.json.JSONObject

object PresetWordsParser {

    fun parse(json: String): PresetWords {
        val root = JSONObject(json)
        val source = root.optString("source", "")
        val booksJson = root.getJSONArray("books")
        val books = buildList {
            for (i in 0 until booksJson.length()) {
                val b = booksJson.getJSONObject(i)
                val wordsJson = b.getJSONArray("words")
                val words = buildList {
                    for (j in 0 until wordsJson.length()) {
                        val w = wordsJson.getJSONObject(j)
                        val sensesJson = w.getJSONArray("senses")
                        val senses = buildList {
                            for (k in 0 until sensesJson.length()) {
                                val s = sensesJson.getJSONObject(k)
                                add(
                                    PresetWords.Sense(
                                        pos = s.getString("pos"),
                                        meaning = s.getString("meaning"),
                                    )
                                )
                            }
                        }
                        add(
                            PresetWords.Word(
                                text = w.getString("text"),
                                // Android's org.json.optString(key, "") returns the string "null" on a
                                // JSON null, so guard with isNull first.
                                phonetic = if (w.isNull("phonetic")) {
                                    null
                                } else {
                                    w.optString("phonetic", "").ifBlank { null }
                                },
                                senses = senses,
                            )
                        )
                    }
                }
                add(PresetWords.Book(name = b.getString("name"), words = words))
            }
        }
        return PresetWords(source = source, books = books)
    }
}
