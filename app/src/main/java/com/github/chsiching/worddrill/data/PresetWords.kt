package com.github.chsiching.worddrill.data

/**
 * 预置词库的内存模型（与 assets/words.json 结构对应）。
 * 纯数据类，无 Android 依赖，便于 JVM 单测构造。
 *
 * JSON 结构：
 * {
 *   "source": "ECDICT (MIT License, ...)",
 *   "books": [
 *     { "name": "CET-4", "words": [
 *        { "text": "apple", "senses": [ {"pos":"n.","meaning":"苹果"} ] }
 *     ]}
 *   ]
 * }
 */
data class PresetWords(
    val source: String,
    val books: List<Book>,
) {
    data class Book(
        val name: String,
        val words: List<Word>,
    )

    data class Word(
        val text: String,
        val senses: List<Sense>,
    )

    data class Sense(
        val pos: String,
        val meaning: String,
    )
}
