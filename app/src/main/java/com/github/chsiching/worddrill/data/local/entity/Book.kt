package com.github.chsiching.worddrill.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 词书。预置词书（CET-4/CET-6/考研）的 isPreset=true，只读（不可重命名/删除）。
 * 自定义词书 isPreset=false，可重命名/删除。
 *
 * Ticket #22：[deleted] 标记本词书被「删除」（隐藏 + 进回收站，可恢复）。
 * 软删 = deleted=1，词书列表/统计过滤；恢复 = deleted=0。与 [BookWord.deleted]
 * 平行但粒度不同：BookWord.deleted 是「某词书的某词条关联」被删，Book.deleted 是
 * 「整个词书」被删。词书恢复时其下所有 book_word 关联（含各自 deleted 状态）保留原样。
 *
 * `@ColumnInfo(defaultValue = "0")` 必须显式声明 —— Room codegen 不会把 Kotlin 默认值
 * 翻译成 SQL DEFAULT 子句，而 [com.github.chsiching.worddrill.data.local.WordDrillDatabase.MIGRATION_4_5]
 * 的 ALTER TABLE 带了 `DEFAULT 0`。两边都写 0，Room 启动时的 schema 校验
 * （比 `TableInfo.Column.defaultVal`）才不会抛 IllegalStateException。
 */
@Entity(tableName = "book")
data class Book(
    @PrimaryKey(autoGenerate = true) val bookId: Long = 0,
    val name: String,
    val isPreset: Boolean = false,
    @ColumnInfo(name = "deleted", defaultValue = "0") val deleted: Boolean = false,
)
