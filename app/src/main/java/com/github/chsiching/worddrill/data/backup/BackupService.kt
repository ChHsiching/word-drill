package com.github.chsiching.worddrill.data.backup

import androidx.room.withTransaction
import com.github.chsiching.worddrill.data.local.WordDrillDatabase
import com.github.chsiching.worddrill.data.local.dao.BookDao
import com.github.chsiching.worddrill.data.local.dao.SwipeLogDao
import com.github.chsiching.worddrill.data.local.dao.WordDao
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupService @Inject constructor(
    private val db: WordDrillDatabase,
    private val bookDao: BookDao,
    private val wordDao: WordDao,
    private val swipeLogDao: SwipeLogDao,
) {
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

    /** Destructive whole-database overwrite: clears all tables, then re-inserts the snapshot. */
    suspend fun import(snapshot: DatabaseSnapshot) = db.withTransaction {
        wordDao.deleteAllSenses()
        bookDao.deleteAllLinks()
        swipeLogDao.deleteAll()
        bookDao.deleteAll()
        wordDao.deleteAll()

        bookDao.insertAll(snapshot.books)
        wordDao.insertAll(snapshot.words)
        wordDao.insertAllSenses(snapshot.senses)
        bookDao.linkAll(snapshot.bookWords)
        swipeLogDao.insertAll(snapshot.swipeLogs)
        Unit
    }
}
