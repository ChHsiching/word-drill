package com.github.chsiching.worddrill.data

import androidx.room.withTransaction
import com.github.chsiching.worddrill.data.local.WordDrillDatabase
import com.github.chsiching.worddrill.data.local.dao.BookDao
import com.github.chsiching.worddrill.data.local.dao.WordDao
import com.github.chsiching.worddrill.data.local.entity.BookWord
import com.github.chsiching.worddrill.data.local.entity.Sense
import com.github.chsiching.worddrill.data.local.entity.Word
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 预置词库导入：把一份 [PresetWords] 写入 Room 的全局词条池。
 *
 * 设计要点（与 Ticket #3 的 schema 约束对齐）：
 * - [Word.text] 全局唯一。同一单词在多个词书出现时只插一次 word，后续词书用 linkBookWord 引用。
 *   这里用 [WordDao.findIdByText] 命中则复用 wordId；未命中则 insert 新行。
 * - [Sense] 的 (wordId, pos) 唯一。数据准备阶段已保证一个词的同一词性只一条释义，
 *   这里直接 insertSense（IGNORE 兜底防止万一重复时整包导入失败）。
 * - 整个导入在一个 Room 事务里（[androidx.room.withTransaction]）：原子且读到自洽快照。
 *
 * 该类只负责"写库"，不负责读 assets / 解析 JSON，便于在 JVM 单测里喂入内存版 Room
 * 和构造好的 [PresetWords] 来验证导入正确性与幂等性。
 */
@Singleton
class PresetImporter @Inject constructor(
    private val db: WordDrillDatabase,
    private val wordDao: WordDao,
    private val bookDao: BookDao,
) {
    /**
     * 把 [preset] 写入数据库。返回导入的词书数量。
     * 重复调用安全：幂等（基于唯一索引 + findIdByText 去重）。
     */
    suspend fun importWords(preset: PresetWords): Int = db.withTransaction {
        var booksImported = 0
        for (bookSpec in preset.books) {
            // 词书幂等：重名则复用既有 bookId，避免重复导入产生重复 book 行
            // （BookDao.insert 的 REPLACE 只在自增主键上生效，重名兜底必须显式查重）。
            val bookId = bookDao.getByName(bookSpec.name)?.bookId
                ?: bookDao.insert(
                    com.github.chsiching.worddrill.data.local.entity.Book(
                        name = bookSpec.name, isPreset = true
                    )
                )
            booksImported++

            for (wordSpec in bookSpec.words) {
                // 1) 全局词条池去重：命中复用 wordId，未命中新建
                val wordId = wordDao.findIdByText(wordSpec.text)
                    ?: wordDao.insert(Word(text = wordSpec.text)).let { id ->
                        if (id == -1L) wordDao.findIdByText(wordSpec.text)!! else id
                    }

                // 2) 写义项（同一词性已合并成一条 meaning，IGNORE 兜底）
                for (sense in wordSpec.senses) {
                    wordDao.insertSense(Sense(wordId = wordId, pos = sense.pos, meaning = sense.meaning))
                }

                // 3) 建立词书-词条引用（IGNORE 兜底跨词书重复）
                bookDao.linkBookWord(BookWord(bookId = bookId, wordId = wordId))
            }
        }
        booksImported
    }
}
