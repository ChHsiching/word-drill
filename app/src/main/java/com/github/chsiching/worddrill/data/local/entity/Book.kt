package com.github.chsiching.worddrill.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "book")
data class Book(
    @PrimaryKey(autoGenerate = true) val bookId: Long = 0,
    val name: String,
    val isPreset: Boolean = false,
    // defaultValue must be explicit: Room does not translate the Kotlin `= false` into a SQL
    // DEFAULT, and it must match the migration's DEFAULT 0 or schema validation
    // (TableInfo.Column.defaultVal) throws.
    @ColumnInfo(name = "deleted", defaultValue = "0") val deleted: Boolean = false,
    // Completed passes of this book (GLOSSARY.md, completion count); incremented when
    // the completion card appears, never cleared by a progress reset. Same
    // DEFAULT-match constraint as `deleted`.
    @ColumnInfo(name = "completionCount", defaultValue = "0") val completionCount: Int = 0,
)
