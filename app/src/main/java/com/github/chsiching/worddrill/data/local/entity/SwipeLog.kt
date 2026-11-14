package com.github.chsiching.worddrill.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Swipe event log. bookId/wordId deliberately have no foreign keys: rows must survive
 *  book/word deletion because all statistics are aggregated from this table. */
@Entity(tableName = "swipe_log")
data class SwipeLog(
    @PrimaryKey(autoGenerate = true) val logId: Long = 0,
    val bookId: Long,
    val wordId: Long,
    /** Epoch milliseconds when the event occurred */
    val timestamp: Long,
)
