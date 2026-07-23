package com.github.chsiching.worddrill.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 全局词条池：一个单词在词池中只存一份，多个词书通过 book_word 引用共享。
 * word 表只存拼写；词性/释义挂在 sense 表上。
 *
 * 按"拼写"去重：text 有唯一索引，保证同一拼写全局只有一行。
 *
 * Ticket #14：phonetic 存 IPA 音标（如 "/əˈbændən/"），可为空（词典缺音标或用户手输词）。
 * 由 v2 migration 以 ALTER TABLE ADD COLUMN 引入，老用户升级后此列为 NULL。
 */
@Entity(
    tableName = "word",
    indices = [Index(value = ["text"], unique = true)]
)
data class Word(
    @PrimaryKey(autoGenerate = true) val wordId: Long = 0,
    val text: String,
    val phonetic: String? = null,
)
