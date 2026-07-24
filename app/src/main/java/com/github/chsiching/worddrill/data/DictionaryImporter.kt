package com.github.chsiching.worddrill.data

import androidx.room.withTransaction
import com.github.chsiching.worddrill.data.local.WordDrillDatabase
import com.github.chsiching.worddrill.data.local.dao.DictionaryDao
import com.github.chsiching.worddrill.data.local.entity.DictionaryEntry
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Ticket #19：内置词典导入。把一份 [DictionaryWords] 写入 Room 的只读 dictionary 表。
 *
 * 设计要点：
 * - dictionary 表 (word, pos) 唯一索引兜底重复：[DictionaryDao.insertAll] 用 IGNORE，
 *   同 (word, pos) 已存在则跳过。重复调用安全（幂等）。
 * - 整个导入在一个 Room 事务里（[androidx.room.withTransaction]）：原子且读到自洽快照。
 * - 批量写入，避免 11 万条逐行 insert。当前一次性 insertAll——若数据规模继续增长可改分批，
 *   现 11 万条单事务在中等手机上首次导入约数秒，可接受。
 *
 * 该类只负责"写库"，不负责读 assets / 解析 JSON，便于在 JVM 单测里喂入内存版 Room
 * 和构造好的 [DictionaryWords] 验证导入正确性与幂等性。
 */
@Singleton
class DictionaryImporter @Inject constructor(
    private val db: WordDrillDatabase,
    private val dictionaryDao: DictionaryDao,
) {
    /**
     * 把 [data] 写入数据库。返回写入的条目数（含 (word,pos) 唯一索引去重后实际新增）。
     * 重复调用安全：基于 (word, pos) 唯一索引 + IGNORE 兜底，不会产生重复行。
     */
    suspend fun importDictionary(data: DictionaryWords): Int = db.withTransaction {
        val before = dictionaryDao.count()
        dictionaryDao.insertAll(
            data.words.map { e ->
                DictionaryEntry(word = e.word, phonetic = e.phonetic, pos = e.pos, meaning = e.meaning)
            }
        )
        dictionaryDao.count() - before
    }
}
