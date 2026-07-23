package com.github.chsiching.worddrill.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.github.chsiching.worddrill.data.local.entity.SwipeLog

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
}
