package com.github.chsiching.worddrill.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.github.chsiching.worddrill.data.local.entity.SwipeLog
import kotlinx.coroutines.flow.Flow

@Dao
interface SwipeLogDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: SwipeLog): Long

    /** Cumulative swipe count: total event count across the table. */
    @Query("SELECT COUNT(*) FROM swipe_log")
    suspend fun totalCount(): Int

    /** Today's swipe count: events with timestamp >= the day-start epoch millis. */
    @Query("SELECT COUNT(*) FROM swipe_log WHERE timestamp >= :dayStartMillis")
    suspend fun countSince(dayStartMillis: Long): Int

    /**
     * Words swiped in a book: count of distinct wordIds swiped within that book.
     * Used for "current book progress = swiped X / total Y". X = this method, Y = BookDao.countWordsInBook.
     */
    @Query(
        """
        SELECT COUNT(DISTINCT wordId) FROM swipe_log
        WHERE bookId = :bookId
        """
    )
    suspend fun distinctWordCountForBook(bookId: Long): Int

    // ---- Ticket #8: reactive versions ("Mine" tab stats refresh live on each swipe) ----

    /** Cumulative swipe count (reactive): re-emitted automatically after any write to swipe_log. */
    @Query("SELECT COUNT(*) FROM swipe_log")
    fun observeTotalCount(): Flow<Int>

    /** Today's swipe count (reactive): pass today's midnight epoch millis. */
    @Query("SELECT COUNT(*) FROM swipe_log WHERE timestamp >= :dayStartMillis")
    fun observeCountSince(dayStartMillis: Long): Flow<Int>

    /** Words swiped in a book (reactive). */
    @Query(
        """
        SELECT COUNT(DISTINCT wordId) FROM swipe_log
        WHERE bookId = :bookId
        """
    )
    fun observeDistinctWordCountForBook(bookId: Long): Flow<Int>

    // ---- Ticket #10: full-database export/import ----

    /** Read all swipe logs (for the export snapshot; includes orphan logs referencing deleted books/entries). */
    @Query("SELECT * FROM swipe_log")
    suspend fun getAll(): List<SwipeLog>

    /** Clear the swipe log table (cleanup before an import overwrites). */
    @Query("DELETE FROM swipe_log")
    suspend fun deleteAll()

    /** Batch-insert swipe logs (with original logIds, to restore an import overwrite). */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(logs: List<SwipeLog>)
}
