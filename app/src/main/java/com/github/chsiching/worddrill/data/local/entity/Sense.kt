package com.github.chsiching.worddrill.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 词义：一个单词的某一种词性下的中文释义。
 * 同一拼写不同词性 = 同一 word 下的不同 sense（一张卡 = 一个 word 的全部 sense）。
 *
 * (wordId, pos) 唯一：同一单词同一词性只允许一条释义。
 */
@Entity(
    tableName = "sense",
    foreignKeys = [
        ForeignKey(
            entity = Word::class,
            parentColumns = ["wordId"],
            childColumns = ["wordId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["wordId", "pos"], unique = true)]
)
data class Sense(
    @PrimaryKey(autoGenerate = true) val senseId: Long = 0,
    val wordId: Long,
    /** 词性，如 "n."、"v."、"adj." */
    val pos: String,
    /** 中文释义 */
    val meaning: String,
)
