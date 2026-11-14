package com.github.chsiching.worddrill.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "word",
    indices = [Index(value = ["text"], unique = true)]
)
data class Word(
    @PrimaryKey(autoGenerate = true) val wordId: Long = 0,
    val text: String,
    /** IPA transcription, e.g. "/əˈbændən/". */
    val phonetic: String? = null,
)
