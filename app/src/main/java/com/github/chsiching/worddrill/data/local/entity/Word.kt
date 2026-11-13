package com.github.chsiching.worddrill.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Global word entry pool: each word is stored only once, shared by multiple books through book_word references.
 * The word table stores only the spelling; pos/meaning live on the sense table.
 *
 * Deduplicated by "spelling": text has a unique index, guaranteeing one row per spelling globally.
 *
 * Ticket #14: phonetic stores the IPA transcription (e.g. "/əˈbændən/"), nullable (dictionary missing the phonetic or user-entered word).
 * Introduced by the v2 migration via ALTER TABLE ADD COLUMN; existing users get NULL after the upgrade.
 */
@Entity(
    tableName = "word",
    indices = [Index(value = ["text"], unique = true)]
)
data class Word(
    @PrimaryKey(autoGenerate = true) val wordId: Long = 0,
    val text: String,
    val phonetic: String? = null,
)
