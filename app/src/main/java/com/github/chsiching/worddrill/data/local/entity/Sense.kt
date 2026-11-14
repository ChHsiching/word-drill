package com.github.chsiching.worddrill.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

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
    val pos: String,
    val meaning: String,
)
