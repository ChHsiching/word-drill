package com.github.chsiching.worddrill.data.backup

import androidx.room.withTransaction
import com.github.chsiching.worddrill.data.local.WordDrillDatabase
import com.github.chsiching.worddrill.data.local.dao.BookDao
import com.github.chsiching.worddrill.data.local.dao.SwipeLogDao
import com.github.chsiching.worddrill.data.local.dao.WordDao
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Whole-database export/import (Ticket #10).
 *
 * Export reads all 5 tables into a [DatabaseSnapshot], which is handed to
 * [DatabaseJsonSerializer]. Import takes a [DatabaseSnapshot], clears the 5 tables in one
 * Room transaction, then batch re-inserts by original primary keys.
 *
 * Import strategy is **overwrite** (Ticket #10 default): clear the whole database and re-insert.
 * Restoring by original primary keys keeps cross-table foreign key references intact, especially
 * orphaned swipe_log rows (bookId/wordId may reference deleted books/words; because swipe_log
 * has no foreign key constraint they are preserved after re-insert, so cumulative statistics
 * are not lost (spec acceptance criteria).
 *
 * Foreign key ordering: sense / book_word have CASCADE foreign keys, so when clearing, delete
 * child tables before parent tables; when inserting, parent tables before child tables.
 * swipe_log has no foreign keys, so any order works.
 *
 * Serialization/deserialization are pure functions ([DatabaseJsonSerializer]) with independent
 * JVM unit test coverage; this class only handles the mapping between tables and the snapshot and
 * transaction orchestration, verified with in-memory Room round-trips in instrumented tests.
 */
@Singleton
class BackupService @Inject constructor(
    private val db: WordDrillDatabase,
    private val bookDao: BookDao,
    private val wordDao: WordDao,
    private val swipeLogDao: SwipeLogDao,
) {
    /** Reads the whole database into a snapshot. nickname may be null (no label written on export). */
    suspend fun export(nickname: String?): DatabaseSnapshot = db.withTransaction {
        DatabaseSnapshot(
            version = DatabaseJsonSerializer.CURRENT_VERSION,
            nickname = nickname,
            books = bookDao.getAll(),
            words = wordDao.getAll(),
            senses = wordDao.getAllSenses(),
            bookWords = bookDao.getAllLinks(),
            swipeLogs = swipeLogDao.getAll(),
        )
    }

    /** Whole-database overwrite restore. Clears the 5 tables and re-inserts by original primary
     *  keys in one transaction; on failure it rolls back, leaving no half-imported state. */
    suspend fun import(snapshot: DatabaseSnapshot) = db.withTransaction {
        // Clear: child tables (sense/book_word/swipe_log) first, then parent tables (book/word)
        wordDao.deleteAllSenses()
        bookDao.deleteAllLinks()
        swipeLogDao.deleteAll()
        bookDao.deleteAll()
        wordDao.deleteAll()

        // Re-insert: parent tables (book/word) first, then child tables (sense/book_word/swipe_log)
        bookDao.insertAll(snapshot.books)
        wordDao.insertAll(snapshot.words)
        wordDao.insertAllSenses(snapshot.senses)
        bookDao.linkAll(snapshot.bookWords)
        swipeLogDao.insertAll(snapshot.swipeLogs)
        Unit
    }
}
