package com.github.chsiching.worddrill.data

/**
 * Ticket #19：内置词典的内存模型（与 assets/dictionary.json 结构对应）。
 * 纯数据类，无 Android 依赖，便于 JVM 单测构造。
 *
 * JSON 结构（与 tools/gen_dictionary_json.py 输出一致）：
 * {
 *   "source": "ECDICT (MIT License, ...)",
 *   "words": [
 *     { "word": "apple", "phonetic": "/ˈæpl/", "pos": "n.", "meaning": "苹果" },
 *     { "word": "apple", "phonetic": "/ˈæpl/", "pos": "v.", "meaning": "..." }
 *   ]
 * }
 *
 * 同一 word 的多个词性 = 多条 entries（与 word/sense 模型同形）。
 */
data class DictionaryWords(
    val source: String,
    val words: List<Entry>,
) {
    data class Entry(
        val word: String,
        val phonetic: String? = null,
        val pos: String,
        val meaning: String,
    )
}
