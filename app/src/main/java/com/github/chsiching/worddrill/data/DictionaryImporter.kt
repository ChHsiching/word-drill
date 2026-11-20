package com.github.chsiching.worddrill.data

import androidx.room.withTransaction
import com.github.chsiching.worddrill.data.local.WordDrillDatabase
import com.github.chsiching.worddrill.data.local.dao.DictionaryDao
import com.github.chsiching.worddrill.data.local.entity.DictionaryEntry
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DictionaryImporter @Inject constructor(
    private val db: WordDrillDatabase,
    private val dictionaryDao: DictionaryDao,
) {
    /** Writes [data]; returns the number of new rows inserted (existing (word, pos) entries are ignored). */
    suspend fun importDictionary(data: DictionaryWords, clearFirst: Boolean = false): Int = db.withTransaction {
        if (clearFirst) {
            dictionaryDao.clear()
        }
        val before = dictionaryDao.count()
        dictionaryDao.insertAll(
            data.words.map { e ->
                DictionaryEntry(
                    word = e.word,
                    phonetic = e.phonetic,
                    phoneticUs = e.phoneticUs,
                    pos = e.pos,
                    meaning = e.meaning,
                )
            }
        )
        dictionaryDao.count() - before
    }
}
