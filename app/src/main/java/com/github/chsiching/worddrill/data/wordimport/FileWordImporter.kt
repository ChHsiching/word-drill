package com.github.chsiching.worddrill.data.wordimport

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.github.chsiching.worddrill.data.local.WordDrillDatabase
import com.github.chsiching.worddrill.data.local.dao.BookDao
import com.github.chsiching.worddrill.data.local.dao.DictionaryDao
import com.github.chsiching.worddrill.data.local.dao.WordDao
import com.github.chsiching.worddrill.data.local.entity.Book
import com.github.chsiching.worddrill.data.local.entity.BookWord
import com.github.chsiching.worddrill.data.local.entity.Sense
import com.github.chsiching.worddrill.data.local.entity.Word
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** SAF 无法打开指定 Uri（权限被吊销 / 文件已被删除等）。由 VM 层转用户可见文案。 */
class FileNotOpenableException(val uri: String) : Exception("file not openable: $uri")

/**
 * Ticket #21：文件导入词书编排器（@Singleton）。
 *
 * 流程（issue #21）：
 * 1. 用户选文件（SAF，Uri 由 ViewModel 传入）+ 输入新词书名 → 调 [import]
 * 2. 按 Uri 后缀识别格式（[ImportFormat.byExtension]）→ 路由到对应解析器
 * 3. 解析得 [ImportRow] 列表
 * 4. 逐词处理：
 *    - **先查 dictionary**（issue #21 单词处理逻辑 step 1）：命中 → 用词典的 pos+meaning+phonetic
 *    - **词典没有** → 用文件 col3 音标 + col4 词性释义（[PosMeaningParser] 解析 col4；
 *      issue 用 1-based 列号：col1=序号 / col2=单词 / col3=音标 / col4=词性释义）
 *    - **col3+4 也空** → 跳过，计入"数据不完整"（[ImportSummary.skipped]）
 * 5. 去重静默：word 表 `text` 唯一索引（IGNORE 兜底）+ sense `(wordId,pos)` 唯一索引 +
 *    book_word `(bookId,wordId)` 复合主键。重复词在不同文件 / 跨词书自动复用同一 word 行，
 *    issue 明确"去重不提示"。
 * 6. 写入 word 词池 + book_word 关联，整步一个 Room 事务（[androidx.room.withTransaction]）
 *    → 原子，失败回滚不留半导入态。
 * 7. 返回 [ImportSummary]（成功 X / 跳过 Y）。
 *
 * 与 [com.github.chsiching.worddrill.data.PresetImporter] 同模式：upsert word（复用或新建）+
 * insertSense + linkBookWord。区别：本类还要查 dictionary 决定数据来源。
 *
 * 所有 I/O 在 [Dispatchers.IO] 上跑；Context 仅取 ContentResolver 打开 Uri。
 */
@Singleton
class FileWordImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: WordDrillDatabase,
    private val wordDao: WordDao,
    private val bookDao: BookDao,
    private val dictionaryDao: DictionaryDao,
) {

    /**
     * @param uri SAF 返回的文件 Uri
     * @param bookName 新词书名（已校验通过：非空、不重名）
     * @param filenameDecoded Uri 对应文件名（用于按扩展名识别格式）。SAF 在某些 Android 版本
     *   不直接暴露文件名，调用方应从 Cursor 查 DISPLAY_NAME 后传入；本类不查 Uri 元数据，
     *   保持"只读 stream"的单一职责。
     * @return 导入完成统计
     */
    suspend fun import(uri: Uri, bookName: String, filenameDecoded: String): ImportSummary =
        withContext(Dispatchers.IO) {
            val format = ImportFormat.byExtension(filenameDecoded)
            val rows = context.contentResolver.openInputStream(uri)?.use { stream ->
                when (format) {
                    ImportFormat.XLSX -> XlsxParser.parse(stream)
                    ImportFormat.TXT -> TextTableParser.parse(stream, delimiter = null)
                    ImportFormat.CSV -> TextTableParser.parse(stream, delimiter = ',')
                    ImportFormat.PDF -> PdfTableParser.parse(stream)
                }
            } ?: throw FileNotOpenableException(uri.toString())

            db.withTransaction {
                val bookId = bookDao.insert(Book(name = bookName.trim(), isPreset = false))
                var success = 0
                var skipped = 0
                for (row in rows) {
                    if (row.word.isBlank()) {
                        skipped++
                        continue
                    }
                    // 1) 先查 dictionary（按小写拼写，ECDICT word 列原形小写为主）
                    val dictEntries = dictionaryDao.findByWord(row.word.lowercase())
                    if (dictEntries.isNotEmpty()) {
                        val phonetic = dictEntries.first().phonetic
                        val wordId = upsertWord(row.word, phonetic)
                        for (entry in dictEntries) {
                            wordDao.insertSense(Sense(wordId = wordId, pos = entry.pos, meaning = entry.meaning))
                        }
                        bookDao.linkBookWord(BookWord(bookId = bookId, wordId = wordId))
                        success++
                        continue
                    }
                    // 2) 词典没有 → 用文件 col3（音标）+ col4（词性释义，需 POS 解析）
                    val phonetic = row.phonetic?.takeIf { it.isNotBlank() }
                    val senses = PosMeaningParser.parse(row.posMeaning ?: "")
                    if (phonetic == null && senses.isEmpty()) {
                        // 3) 全空 → 跳过，计入"数据不完整"
                        skipped++
                        continue
                    }
                    val wordId = upsertWord(row.word, phonetic)
                    for ((pos, meaning) in senses) {
                        wordDao.insertSense(Sense(wordId = wordId, pos = pos, meaning = meaning))
                    }
                    bookDao.linkBookWord(BookWord(bookId = bookId, wordId = wordId))
                    success++
                }
                ImportSummary(success = success, skipped = skipped)
            }
        }

    /**
     * 全局词条池 upsert：命中复用 wordId；未命中 insert（IGNORE 兜底并发，-1 取回查）。
     * 与 [com.github.chsiching.worddrill.data.PresetImporter] / [WordListViewModel.submitAdd] 同套路。
     */
    private suspend fun upsertWord(text: String, phonetic: String?): Long =
        wordDao.findIdByText(text)
            ?: wordDao.insert(Word(text = text, phonetic = phonetic)).let { id ->
                if (id == -1L) wordDao.findIdByText(text)!! else id
            }
}
