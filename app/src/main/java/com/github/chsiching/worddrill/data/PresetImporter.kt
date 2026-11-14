package com.github.chsiching.worddrill.data

import androidx.room.withTransaction
import com.github.chsiching.worddrill.data.local.WordDrillDatabase
import com.github.chsiching.worddrill.data.local.dao.BookDao
import com.github.chsiching.worddrill.data.local.dao.WordDao
import com.github.chsiching.worddrill.data.local.entity.BookWord
import com.github.chsiching.worddrill.data.local.entity.Sense
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PresetImporter @Inject constructor(
    private val db: WordDrillDatabase,
    private val wordDao: WordDao,
    private val bookDao: BookDao,
) {
    /** Idempotent: repeated imports reuse existing books/words/senses. */
    suspend fun importWords(preset: PresetWords): Int = db.withTransaction {
        var booksImported = 0
        for (bookSpec in preset.books) {
            val bookId = bookDao.getByName(bookSpec.name)?.bookId
                ?: bookDao.insert(
                    com.github.chsiching.worddrill.data.local.entity.Book(
                        name = bookSpec.name, isPreset = true
                    )
                )
            booksImported++

            for (wordSpec in bookSpec.words) {
                val wordId = wordDao.findOrInsertWord(wordSpec.text, wordSpec.phonetic)

                for (sense in wordSpec.senses) {
                    wordDao.insertSense(Sense(wordId = wordId, pos = sense.pos, meaning = sense.meaning))
                }

                bookDao.linkBookWord(BookWord(bookId = bookId, wordId = wordId))
            }
        }
        booksImported
    }
}
