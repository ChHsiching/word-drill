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

    /** 累计刷卡数：全表事件计数。 */
    @Query("SELECT COUNT(*) FROM swipe_log")
    suspend fun totalCount(): Int

    /** 今日刷卡数：timestamp >= 一日起始 epoch 毫秒的事件计数。 */
    @Query("SELECT COUNT(*) FROM swipe_log WHERE timestamp >= :dayStartMillis")
    suspend fun countSince(dayStartMillis: Long): Int

    /**
     * 某词书已刷词数：该词书内被刷卡过的不同 wordId 数。
     * 用于"当前词书进度 = 已刷 X / 总 Y"。X = 本方法，Y = BookDao.countWordsInBook。
     */
    @Query(
        """
        SELECT COUNT(DISTINCT wordId) FROM swipe_log
        WHERE bookId = :bookId
        """
    )
    suspend fun distinctWordCountForBook(bookId: Long): Int

    // ---- Ticket #8: 响应式版本（「我的」Tab 统计随刷卡实时刷新）----

    /** 累计刷卡数（响应式）：swipe_log 任意写操作后自动重发。 */
    @Query("SELECT COUNT(*) FROM swipe_log")
    fun observeTotalCount(): Flow<Int>

    /** 今日刷卡数（响应式）：传入今日 0 点毫秒。 */
    @Query("SELECT COUNT(*) FROM swipe_log WHERE timestamp >= :dayStartMillis")
    fun observeCountSince(dayStartMillis: Long): Flow<Int>

    /** 某词书已刷词数（响应式）。 */
    @Query(
        """
        SELECT COUNT(DISTINCT wordId) FROM swipe_log
        WHERE bookId = :bookId
        """
    )
    fun observeDistinctWordCountForBook(bookId: Long): Flow<Int>
}
