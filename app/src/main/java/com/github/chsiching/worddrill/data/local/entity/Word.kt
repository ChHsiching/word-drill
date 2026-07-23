package com.github.chsiching.worddrill.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 全局词条池：一个单词在词池中只存一份，多个词书通过 book_word 引用共享。
 * word 表只存拼写；词性/释义挂在 sense 表上。
 *
 * 按"拼写"去重：text 有唯一索引，保证同一拼写全局只有一行。
 */
@Entity(
    tableName = "word",
    indices = [Index(value = ["text"], unique = true)]
)
data class Word(
    @PrimaryKey(autoGenerate = true) val wordId: Long = 0,
    val text: String,
)
