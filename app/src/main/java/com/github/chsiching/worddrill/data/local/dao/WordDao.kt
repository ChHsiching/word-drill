package com.github.chsiching.worddrill.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.github.chsiching.worddrill.data.local.WordWithSenses
import com.github.chsiching.worddrill.data.local.entity.Sense
import com.github.chsiching.worddrill.data.local.entity.Word
import kotlinx.coroutines.flow.Flow

@Dao
interface WordDao {

    // ---- 词条 CRUD ----

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(word: Word): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSense(sense: Sense): Long

    @Query("SELECT * FROM word WHERE wordId = :wordId")
    suspend fun getById(wordId: Long): Word?

    /** 按文本查重，返回已存在的 wordId（若无则 null）。用于导入/新增时复用全局词条。 */
    @Query("SELECT wordId FROM word WHERE text = :text LIMIT 1")
    suspend fun findIdByText(text: String): Long?

    @Query("SELECT * FROM word WHERE text = :text LIMIT 1")
    suspend fun getByText(text: String): Word?

    @Query("SELECT * FROM sense WHERE wordId = :wordId")
    suspend fun getSensesForWord(wordId: Long): List<Sense>

    /**
     * 按词书查词条（含每个词的义项列表）。
     * Ticket #20：过滤 `bw.skipped = 0` —— 跳过的词不进刷卡卡片列表。
     * Ticket #22：过滤 `bw.deleted = 0` —— 软删的词不进刷卡卡片列表。
     * @Transaction 保证 @Relation 的两步查询在一个事务里，读到一致快照。
     */
    @Transaction
    @Query(
        """
        SELECT w.* FROM word w
        INNER JOIN book_word bw ON w.wordId = bw.wordId
        WHERE bw.bookId = :bookId AND bw.skipped = 0 AND bw.deleted = 0
        ORDER BY w.text
        """
    )
    suspend fun getWordsWithSensesByBook(bookId: Long): List<WordWithSenses>

    /**
     * Ticket #7：按词书响应式查词条（含义项列表）。
     * 词书列表页订阅，增删改后自动重发。
     * Ticket #20：过滤 `bw.skipped = 0`，词条列表页只显示未跳过的词。
     * Ticket #22：过滤 `bw.deleted = 0`，词条列表页只显示未软删的词。
     * @Transaction 保证 @Relation 两步查询读一致快照。
     */
    @Transaction
    @Query(
        """
        SELECT w.* FROM word w
        INNER JOIN book_word bw ON w.wordId = bw.wordId
        WHERE bw.bookId = :bookId AND bw.skipped = 0 AND bw.deleted = 0
        ORDER BY w.text
        """
    )
    fun observeWordsWithSensesByBook(bookId: Long): Flow<List<WordWithSenses>>

    @Query("UPDATE sense SET pos = :pos, meaning = :meaning WHERE senseId = :senseId")
    suspend fun updateSense(senseId: Long, pos: String, meaning: String)

    @Query("DELETE FROM sense WHERE senseId = :senseId")
    suspend fun deleteSense(senseId: Long)

    @Query("DELETE FROM word WHERE wordId = :wordId")
    suspend fun deleteWord(wordId: Long)

    // ---- Ticket #10：整库导出/导入 ----

    /** 全量读所有词条（导出快照用）。 */
    @Query("SELECT * FROM word")
    suspend fun getAll(): List<Word>

    /** 全量读所有义项（导出快照用）。 */
    @Query("SELECT * FROM sense")
    suspend fun getAllSenses(): List<Sense>

    /** 清空义项表（导入覆盖前清理）。 */
    @Query("DELETE FROM sense")
    suspend fun deleteAllSenses()

    /** 清空词条表（导入覆盖前清理）。 */
    @Query("DELETE FROM word")
    suspend fun deleteAll()

    /** 批量插入词条（带原始 wordId，导入覆盖恢复用）。 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(words: List<Word>)

    /** 批量插入义项（带原始 senseId，导入覆盖恢复用）。 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllSenses(senses: List<Sense>)
}
