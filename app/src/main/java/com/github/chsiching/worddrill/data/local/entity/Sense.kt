package com.github.chsiching.worddrill.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Sense: the Chinese definition of a word under one part of speech.
 * The same spelling with different parts of speech means different senses under the same word
 * (one card shows all senses of one word).
 *
 * (wordId, pos) unique: only one definition is allowed per word per part of speech.
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
    /** Part of speech, e.g. "n.", "v.", "adj." */
    val pos: String,
    /** Chinese definition */
    val meaning: String,
)
