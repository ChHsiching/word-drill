package com.github.chsiching.worddrill.data.backup

import androidx.room.withTransaction
import com.github.chsiching.worddrill.data.local.WordDrillDatabase
import com.github.chsiching.worddrill.data.local.dao.BookDao
import com.github.chsiching.worddrill.data.local.dao.SwipeLogDao
import com.github.chsiching.worddrill.data.local.dao.WordDao
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 整库导出/导入（Ticket #10）。
 *
 * 导出：读 5 张表全量 → [DatabaseSnapshot]（交由 [DatabaseJsonSerializer] 序列化）。
 * 导入：[DatabaseSnapshot] → 在一个 Room 事务里清空 5 张表后按原始主键批量重插。
 *
 * 导入策略为**覆盖**（Ticket #10 默认）：整库清空再重插。按原始主键恢复保证跨表外键
 * 引用关系完整，尤其是孤立的 swipe_log（bookId/wordId 可能引用已删除的词书/词条，
 * 因 swipe_log 无外键约束，重插后保留 → 累计统计不可丢，规格 AC）。
 *
 * 外键顺序：sense / book_word 有 CASCADE 外键 → 清理先删子表再删父表；
 * 插入先父表再子表。swipe_log 无外键，可任意顺序。
 *
 * 序列化/反序列化是纯函数（[DatabaseJsonSerializer]），有独立 JVM 单测覆盖；
 * 本类只负责"表 ↔ 快照"映射与事务编排，在 instrumented 测试里用内存 Room 验证往返。
 */
@Singleton
class BackupService @Inject constructor(
    private val db: WordDrillDatabase,
    private val bookDao: BookDao,
    private val wordDao: WordDao,
    private val swipeLogDao: SwipeLogDao,
) {
    /** 读整库为快照。nickname 可为 null（导出时不写标签）。 */
    suspend fun export(nickname: String?): DatabaseSnapshot = db.withTransaction {
        DatabaseSnapshot(
            version = DatabaseJsonSerializer.CURRENT_VERSION,
            nickname = nickname,
            books = bookDao.getAll(),
            words = wordDao.getAll(),
            senses = wordDao.getAllSenses(),
            bookWords = bookDao.getAllLinks(),
            swipeLogs = swipeLogDao.getAll(),
        )
    }

    /** 整库覆盖恢复。在一个事务里清空 5 张表后按原始主键重插，失败回滚不留半导入状态。 */
    suspend fun import(snapshot: DatabaseSnapshot) = db.withTransaction {
        // 清空：先子表（sense/book_word/swipe_log）再父表（book/word）
        wordDao.deleteAllSenses()
        bookDao.deleteAllLinks()
        swipeLogDao.deleteAll()
        bookDao.deleteAll()
        wordDao.deleteAll()

        // 重插：先父表（book/word）再子表（sense/book_word/swipe_log）
        bookDao.insertAll(snapshot.books)
        wordDao.insertAll(snapshot.words)
        wordDao.insertAllSenses(snapshot.senses)
        bookDao.linkAll(snapshot.bookWords)
        swipeLogDao.insertAll(snapshot.swipeLogs)
        Unit
    }
}
