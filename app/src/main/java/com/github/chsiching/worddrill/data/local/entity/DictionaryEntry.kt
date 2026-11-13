package com.github.chsiching.worddrill.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Ticket #19: Built-in dictionary (top 100k frequent words from the full ECDICT).
 *
 * Independent from the word pool: the dictionary table is read-only and only used to look up
 * pos, meaning, and phonetic by spelling when adding words or importing files; matches are copied
 * into the word pool. The word pool is writable and referenced by books.
 *
 * Schema from issue #19: dict_id (primary key), word (spelling), phonetic (IPA),
 * pos (part of speech), meaning (definition). Multiple parts of speech for the same word mean
 * multiple rows (same shape as the sense table), so that
 * [com.github.chsiching.worddrill.data.local.dao.DictionaryDao.findByWord]
 * can return a List and map it directly to sense.
 *
 * The word column alone cannot be unique, because the same word spans multiple rows (one per pos).
 * But (word, pos) should be unique. The first-launch import relies on IGNORE as a fallback, plus a composite index to avoid duplicates.
 * Simplification: the (word, pos) composite unique index alone is enough to guard against duplicate imports.
 */
@Entity(
    tableName = "dictionary",
    indices = [Index(value = ["word", "pos"], unique = true)]
)
data class DictionaryEntry(
    @PrimaryKey(autoGenerate = true) val dictId: Long = 0,
    val word: String,
    val phonetic: String? = null,
    val pos: String,
    val meaning: String,
)
