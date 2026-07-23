package com.github.chsiching.worddrill.data

import org.json.JSONObject

/**
 * 解析 assets/words.json 文本为 [PresetWords]。
 * 用 Android 自带的 org.json（无新增依赖）。
 *
 * 解析逻辑独立成纯函数，便于在 JVM 单测里喂入小段 JSON 字符串验证。
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
                                // Ticket #14：phonetic 可空。
                                // ⚠️ 必须 w.isNull 先挡 JSON null：Android 自带 org.json 的 optString(key, "")
                                // 在 key 存在且值为 JSON null 时会返回字符串 "null"（而非 fallback ""),
                                // 直接 optString 会让 phonetic = "null" 落库（实机验证踩到）。
                                // 同样的坑也出现在 DatabaseJsonSerializer 反序列化路径，处理方式一致。
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
