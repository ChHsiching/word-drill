package com.github.chsiching.worddrill.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Ticket #19：内置词典（ECDICT 完整版高频 10 万词）。
 *
 * 独立于 word 词池：dictionary 表只读，仅用于加词 / 文件导入时按拼写查
 * 词性 + 释义 + 音标，查到后复制写入 word 词池。word 词池可写、被词书引用。
 *
 * schema 来自 issue #19：dict_id（主键）、word（单词）、phonetic（音标）、
 * pos（词性）、meaning（释义）。同一单词的多个词性 = 多行（与 sense 表同形），
 * 便于 [com.github.chsiching.worddrill.data.local.dao.DictionaryDao.findByWord]
 * 返回 List 后直接映射到 sense。
 *
 * word 列建唯一索引？不建：同一 word 有多行（不同 pos），不行唯一。
 * 但 (word, pos) 应唯一——首启导入走 IGNORE 兜底，再加一个复合索引避免重复。
 * 简化：用 (word, pos) 复合唯一索引兜底重复导入即可。
 */
@Entity(
    tableName = "dictionary",
    indices = [Index(value = ["word", "pos"], unique = true)]
)
data class DictionaryEntry(
    @PrimaryKey(autoGenerate = true) val dictId: Long = 0,
    val word: String,
    val phonetic: String? = null,
    val pos: String,
    val meaning: String,
)
