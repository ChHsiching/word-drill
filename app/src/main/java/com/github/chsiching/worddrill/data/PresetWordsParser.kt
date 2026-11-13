package com.github.chsiching.worddrill.data

import org.json.JSONObject

/**
 * Parse assets/words.json text into [PresetWords].
 * Uses Android's built-in org.json (no new dependency).
 *
 * The parsing logic is a standalone pure function, easy to verify in JVM unit tests by feeding
 * in small JSON strings.
 */
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
                                // Ticket #14: phonetic is nullable.
                                // Warning: guard JSON null with w.isNull first, because Android's
                                // bundled org.json optString(key, "") returns the string "null" when
                                // the key exists with a JSON null value, so calling
                                // optString directly would persist phonetic = "null" (hit and verified on
                                // a real device). The same trap exists in DatabaseJsonSerializer's
                                // deserialization path, handled the same way.
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
