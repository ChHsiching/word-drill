package com.github.chsiching.worddrill.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * Book-to-word-entry association table (many-to-many).
 * Composite primary key (bookId, wordId): the same word entry cannot repeat within the same book.
 * Associations are cleaned up by cascade when a book or word entry is deleted.
 *
 * Ticket #20: [skipped] marks this word as "skipped" within this book (hidden, not deleted).
 * Independent per book: skipping in CET-4 only sets skipped=1 on the CET-4 association row and does not affect the CET-6 association row for the same word.
 * Skipping is recoverable: [com.github.chsiching.worddrill.data.local.dao.BookDao.setSkipped] sets it back to 0 to restore.
 *
 * Ticket #22: [deleted] marks this word as "deleted" within this book (hidden + moved to the recycle bin, recoverable).
 * Parallel to [skipped]: independent per book (deleting in CET-4 only touches the CET-4 association), deleted=0 restores.
 * Difference: skipped means "skip review" (automatically goes into the review book), deleted means "recover from accidental deletion" (goes to the recycle bin).
 * Soft delete = only mark deleted=1, do not actually delete the association row; the word itself always stays in the global word pool.
 *
 * `@ColumnInfo(defaultValue = "0")` must be declared explicitly. Room codegen does not translate the Kotlin
 * default value (`= false`) into a SQL DEFAULT clause, while the ALTER TABLE in
 * [com.github.chsiching.worddrill.data.local.WordDrillDatabase.MIGRATION_3_4]
 * / [com.github.chsiching.worddrill.data.local.WordDrillDatabase.MIGRATION_4_5]
 * includes `DEFAULT 0`. With 0 written on both sides, Room's startup schema validation
 * (which compares `TableInfo.Column.defaultVal`) will not throw IllegalStateException.
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
    @ColumnInfo(name = "skipped", defaultValue = "0") val skipped: Boolean = false,
    @ColumnInfo(name = "deleted", defaultValue = "0") val deleted: Boolean = false,
)
