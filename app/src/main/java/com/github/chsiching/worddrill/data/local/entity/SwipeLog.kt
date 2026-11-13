package com.github.chsiching.worddrill.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Right-swipe (advance to the next card) event log. One raw record per card swipe.
 * All statistics (today's count, cumulative count, words swiped per book) are aggregated from
 * this table; progress is not stored separately.
 *
 * bookId / wordId have no foreign keys: historical logs must be kept after a book or word entry
 * is deleted, for cumulative statistics.
 */
@Entity(tableName = "swipe_log")
data class SwipeLog(
    @PrimaryKey(autoGenerate = true) val logId: Long = 0,
    val bookId: Long,
    val wordId: Long,
    /** Epoch milliseconds when the event occurred */
    val timestamp: Long,
)
