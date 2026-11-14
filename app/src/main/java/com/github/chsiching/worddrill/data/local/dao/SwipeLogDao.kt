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

    @Query("SELECT COUNT(*) FROM swipe_log")
    suspend fun totalCount(): Int

    @Query("SELECT COUNT(*) FROM swipe_log WHERE timestamp >= :dayStartMillis")
    suspend fun countSince(dayStartMillis: Long): Int

    @Query(
        """
        SELECT COUNT(DISTINCT wordId) FROM swipe_log
        WHERE bookId = :bookId
        """
    )
    suspend fun distinctWordCountForBook(bookId: Long): Int

    @Query("SELECT COUNT(*) FROM swipe_log")
    fun observeTotalCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM swipe_log WHERE timestamp >= :dayStartMillis")
    fun observeCountSince(dayStartMillis: Long): Flow<Int>

    @Query(
        """
        SELECT COUNT(DISTINCT wordId) FROM swipe_log
        WHERE bookId = :bookId
        """
    )
    fun observeDistinctWordCountForBook(bookId: Long): Flow<Int>

    @Query("SELECT * FROM swipe_log")
    suspend fun getAll(): List<SwipeLog>

    @Query("DELETE FROM swipe_log")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(logs: List<SwipeLog>)
}
