package com.github.chsiching.worddrill.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 词书。预置词书（CET-4/CET-6/考研）的 isPreset=true，只读（不可重命名/删除）。
 * 自定义词书 isPreset=false，可重命名/删除。
 */
@Entity(tableName = "book")
data class Book(
    @PrimaryKey(autoGenerate = true) val bookId: Long = 0,
    val name: String,
    val isPreset: Boolean = false,
)
