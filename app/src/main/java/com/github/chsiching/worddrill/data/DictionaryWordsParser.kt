package com.github.chsiching.worddrill.data

import org.json.JSONObject

/**
 * Ticket #19：解析 assets/dictionary.json 文本为 [DictionaryWords]。
 * 用 Android 自带的 org.json（无新增依赖）。
 *
 * 解析逻辑独立成纯函数，便于在 JVM 单测里喂入小段 JSON 字符串验证
 * （与 [PresetWordsParser] 同模式）。
 */
object DictionaryWordsParser {

    fun parse(json: String): DictionaryWords {
        val root = JSONObject(json)
        val source = root.optString("source", "")
        val wordsJson = root.getJSONArray("words")
        val words = buildList {
            for (i in 0 until wordsJson.length()) {
                val w = wordsJson.getJSONObject(i)
                // phonetic 可空，且必须 w.isNull 先挡 JSON null（与 PresetWordsParser 同坑：
                // org.json.optString(key, "") 在 JSON null 时返回 "null" 字符串）。
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
