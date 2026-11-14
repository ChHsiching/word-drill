package com.github.chsiching.worddrill.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/** skipped = hidden from this book's cards; deleted = soft-deleted to the recycle bin.
 *  Both are per (book, word) association, so other books' rows for the same word are unaffected. */
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
    // defaultValue must be explicit (see Book.deleted): it must match the migrations' DEFAULT 0.
    @ColumnInfo(name = "skipped", defaultValue = "0") val skipped: Boolean = false,
    @ColumnInfo(name = "deleted", defaultValue = "0") val deleted: Boolean = false,
)
