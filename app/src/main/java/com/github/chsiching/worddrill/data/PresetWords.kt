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
 *        { "text": "apple", "phonetic": "/ˈæpl/", "senses": [ {"pos":"n.","meaning":"苹果"} ] }
 *     ]}
 *   ]
 * }
 *
 * Ticket #14：phonetic 可空（部分预置词词典缺音标，或用户手输词无音标）。
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
        val phonetic: String? = null,
        val senses: List<Sense>,
    )

    data class Sense(
        val pos: String,
        val meaning: String,
    )
}
