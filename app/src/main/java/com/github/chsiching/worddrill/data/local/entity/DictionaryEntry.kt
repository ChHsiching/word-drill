package com.github.chsiching.worddrill.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "dictionary",
    indices = [Index(value = ["word", "pos"], unique = true)]
)
data class DictionaryEntry(
    @PrimaryKey(autoGenerate = true) val dictId: Long = 0,
    val word: String,
    /** UK variant; the Youdao asset stores the US variant in [phoneticUs]. */
    val phonetic: String? = null,
    val phoneticUs: String? = null,
    val pos: String,
    val meaning: String,
)
