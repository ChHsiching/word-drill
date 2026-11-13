package com.github.chsiching.worddrill.data

import androidx.room.withTransaction
import com.github.chsiching.worddrill.data.local.WordDrillDatabase
import com.github.chsiching.worddrill.data.local.dao.DictionaryDao
import com.github.chsiching.worddrill.data.local.entity.DictionaryEntry
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Ticket #19: built-in dictionary import. Writes a [DictionaryWords] into Room's read-only
 * dictionary table.
 *
 * Design points:
 * - The dictionary table's (word, pos) unique index guards against duplicates:
 *   [DictionaryDao.insertAll] uses IGNORE, so an existing (word, pos) is skipped.
 *   Safe to call repeatedly (idempotent).
 * - The whole import runs in one Room transaction ([androidx.room.withTransaction]): atomic
 *   and reading a self-consistent snapshot.
 * - Batch writes, avoiding 110k row-by-row inserts. Currently one bulk insertAll; if the data
 *   grows further it can be chunked. As is, 110k rows in a single transaction takes a few
 *   seconds on a mid-range phone for the first import, which is acceptable.
 *
 * This class only "writes the database"; it does not read assets / parse JSON, so JVM unit
 * tests can feed an in-memory Room and a constructed [DictionaryWords] to verify import
 * correctness and idempotency.
 */
@Singleton
class DictionaryImporter @Inject constructor(
    private val db: WordDrillDatabase,
    private val dictionaryDao: DictionaryDao,
) {
    /**
     * Write [data] into the database. Returns the number of entries written (the actual new
     * rows after (word,pos) unique-index dedupe).
     *
     * Parameter [clearFirst]: added in Ticket #23. When true, clears the dictionary table
     * before writing, for the version-upgrade re-import scenario (INSERT IGNORE cannot
     * overwrite old rows, so a clear must come first).
     * Defaults to false for older callers (first-launch import into an empty table).
     */
    suspend fun importDictionary(data: DictionaryWords, clearFirst: Boolean = false): Int = db.withTransaction {
        if (clearFirst) {
            dictionaryDao.clear()
        }
        val before = dictionaryDao.count()
        dictionaryDao.insertAll(
            data.words.map { e ->
                DictionaryEntry(word = e.word, phonetic = e.phonetic, pos = e.pos, meaning = e.meaning)
            }
        )
        dictionaryDao.count() - before
    }
}
