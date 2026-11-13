package com.github.chsiching.worddrill.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Word book. Preset books (CET-4/CET-6/kaoyan) have isPreset=true and are read-only (cannot be renamed/deleted).
 * Custom books have isPreset=false and can be renamed/deleted.
 *
 * Ticket #22: [deleted] marks this book as "deleted" (hidden and moved to the recycle bin, recoverable).
 * Soft delete sets deleted=1, which filters the book out of lists and stats; restore sets it back to 0. Parallel to
 * [BookWord.deleted] but at a different granularity: BookWord.deleted means the association between
 * one book and one word entry is deleted, while Book.deleted means the whole book is deleted.
 * When a book is restored, all its book_word associations (including their individual deleted states) stay as they were.
 *
 * `@ColumnInfo(defaultValue = "0")` must be declared explicitly. Room codegen does not translate the Kotlin
 * default value into a SQL DEFAULT clause, while the ALTER TABLE in
 * [com.github.chsiching.worddrill.data.local.WordDrillDatabase.MIGRATION_4_5]
 * includes `DEFAULT 0`. With 0 written on both sides, Room's startup schema validation
 * (which compares `TableInfo.Column.defaultVal`) will not throw IllegalStateException.
 */
@Entity(tableName = "book")
data class Book(
    @PrimaryKey(autoGenerate = true) val bookId: Long = 0,
    val name: String,
    val isPreset: Boolean = false,
    @ColumnInfo(name = "deleted", defaultValue = "0") val deleted: Boolean = false,
)
