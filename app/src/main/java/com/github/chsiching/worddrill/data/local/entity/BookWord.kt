package com.github.chsiching.worddrill.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * 词书-词条 关联表（多对多）。
 * 复合主键 (bookId, wordId)：同一词书内同一词条不重复。
 * 词书或词条删除时级联清理关联。
 *
 * Ticket #20：[skipped] 标记本词书内此词被「跳过」（隐藏不删）。
 * 词书级独立：CET-4 跳过只置 CET-4 这条关联的 skipped=1，不影响 CET-6 同词的关联行。
 * 跳过可恢复：[com.github.chsiching.worddrill.data.local.dao.BookDao.setSkipped] 置回 0 即恢复。
 *
 * `@ColumnInfo(defaultValue = "0")` 必须显式声明 —— Room codegen 不会把 Kotlin 默认值
 * （`= false`）翻译成 SQL DEFAULT 子句，而 [com.github.chsiching.worddrill.data.local.WordDrillDatabase.MIGRATION_3_4]
 * 的 ALTER TABLE 带了 `DEFAULT 0`。两边都写 0，Room 启动时的 schema 校验
 * （比 `TableInfo.Column.defaultVal`）才不会抛 IllegalStateException。
 */
@Entity(
    tableName = "book_word",
    primaryKeys = ["bookId", "wordId"],
    foreignKeys = [
        ForeignKey(
            entity = Book::class,
            parentColumns = ["bookId"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Word::class,
            parentColumns = ["wordId"],
            childColumns = ["wordId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["wordId"])]
)
data class BookWord(
    val bookId: Long,
    val wordId: Long,
    @ColumnInfo(name = "skipped", defaultValue = "0") val skipped: Boolean = false,
)
