package com.github.chsiching.worddrill.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 向右滑（前进到下一张）事件日志。每次刷卡一条原始记录。
 * 统计（今日计数、累计计数、某词书已刷词数）全部从此表聚合得出，不另存进度。
 *
 * bookId / wordId 不加外键：词书/词条删除后历史日志仍需保留，用于累计统计。
 */
@Entity(tableName = "swipe_log")
data class SwipeLog(
    @PrimaryKey(autoGenerate = true) val logId: Long = 0,
    val bookId: Long,
    val wordId: Long,
    /** 事件发生的 epoch 毫秒 */
    val timestamp: Long,
)
