package com.github.chsiching.worddrill.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * 词书-词条 关联表（多对多）。
 * 复合主键 (bookId, wordId)：同一词书内同一词条不重复。
 * 词书或词条删除时级联清理关联。
 */
@Entity(
    tableName = "book_word",
    primaryKeys = ["bookId", "wordId"],
    foreignKeys = [
        ForeignKey(
            entity = Book::class,
            parentColumns = ["bookId"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Word::class,
            parentColumns = ["wordId"],
            childColumns = ["wordId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["wordId"])]
)
data class BookWord(
    val bookId: Long,
    val wordId: Long,
)
