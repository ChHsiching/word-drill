package com.github.chsiching.worddrill.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.github.chsiching.worddrill.data.local.entity.Book
import com.github.chsiching.worddrill.data.local.entity.BookWord
import kotlinx.coroutines.flow.Flow

/**
 * 词书 + 词条数（Ticket #16：词库列表副标题需要展示词条数）。
 * JOIN + GROUP BY 一次查出所有词书的词条数，避免 N 次查询。
 *
 * Ticket #20：wordCount 只计 `skipped = 0` 的未跳过词条 —— 词书列表副标题的「X 词」
 * 反映用户实际会刷到的词数。跳过（隐藏不删）后副标题数字相应减少。
 *
 * Ticket #22：wordCount 还排除 `deleted = 1` 的软删词条 —— 删除后副标题数字相应减少，
 * 与 [WordDao.getWordsWithSensesByBook] / [observeWordsWithSensesByBook] 的过滤保持一致。
 */
data class BookWithCount(
    val bookId: Long,
    val name: String,
    val isPreset: Boolean,
    val wordCount: Int,
)

/**
 * 回收站条目（Ticket #22）：deleted=1 的 (bookId, wordId) 关联 + 关联的词书名/词文本。
 * 用于回收站列表展示「wordText（从 bookName 移除）」。
 */
data class DeletedEntry(
    val bookId: Long,
    val wordId: Long,
    val bookName: String,
    val wordText: String,
)

@Dao
interface BookDao {

    // ---- 词书 CRUD ----

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(book: Book): Long

    /**
     * 所有可见词书（Ticket #22：过滤 deleted=1 的软删词书）。
     * 排序：预置在前、name 升序。
     */
    @Query("SELECT * FROM book WHERE deleted = 0 ORDER BY isPreset DESC, CASE WHEN name = '复习' THEN 1 ELSE 0 END, name ASC")
    fun observeAll(): Flow<List<Book>>

    /**
     * 词书 + 词条数（Ticket #16）。LEFT JOIN 保证空词书（0 词）也列出。
     * 排序与 [observeAll] 一致：预置在前、name 升序。
     *
     * Ticket #20：只计 `bw.skipped = 0` 的未跳过词 —— 跳过的词不算进副标题词数。
     * Ticket #22：还排除 `bw.deleted = 1` 的软删词 —— 删除后副标题词数相应减少。
     *            且过滤 `b.deleted = 0` —— 软删的词书本身不进词库列表。
     */
    @Query(
        """
        SELECT b.bookId AS bookId, b.name AS name, b.isPreset AS isPreset,
               COUNT(bw.wordId) AS wordCount
        FROM book b
        LEFT JOIN book_word bw ON b.bookId = bw.bookId AND bw.skipped = 0 AND bw.deleted = 0
        WHERE b.deleted = 0
        GROUP BY b.bookId
        ORDER BY b.isPreset DESC, CASE WHEN b.name = '复习' THEN 1 ELSE 0 END, b.name ASC
        """
    )
    fun observeAllWithCounts(): Flow<List<BookWithCount>>

    @Query("SELECT * FROM book WHERE bookId = :bookId")
    suspend fun getById(bookId: Long): Book?

    /** 按名字查词书。导入幂等用：重名预置词书复用既有行，不新建重复行。 */
    @Query("SELECT * FROM book WHERE name = :name LIMIT 1")
    suspend fun getByName(name: String): Book?

    /**
     * 重命名词书。返回受影响行数：自定义词书返回 1，预置词书被 WHERE isPreset = 0 拒绝（返回 0）。
     * 与 [deleteCustom] 同模式：预置词书在 DAO 层兜底只读，UI 入口也隐藏。
     */
    @Query("UPDATE book SET name = :name WHERE bookId = :bookId AND isPreset = 0")
    suspend fun rename(bookId: Long, name: String): Int

    /**
     * 软删除词书（Ticket #22）：标记 deleted=1，词书进回收站，可恢复。
     * 返回受影响行数：自定义词书返回 1，预置词书被 WHERE isPreset = 0 拒绝（返回 0）。
     * 与 [rename] 同模式：预置词书在 DAO 层兜底只读，UI 入口也隐藏。
     *
     * 软删不真删 book 行，也不动其下 book_word 关联（含各自 deleted/skipped 状态保留）。
     * 恢复走 [restoreBook]（deleted 置 0），永久删除走 [purgeBook]（真 DELETE + CASCADE）。
     */
    @Query("UPDATE book SET deleted = 1 WHERE bookId = :bookId AND isPreset = 0")
    suspend fun deleteCustom(bookId: Long): Int

    // ---- 词书-词条 关联 ----

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun linkBookWord(bookWord: BookWord): Long

    @Query("DELETE FROM book_word WHERE bookId = :bookId AND wordId = :wordId")
    suspend fun unlinkBookWord(bookId: Long, wordId: Long)

    /**
     * 词书内未跳过、未删除的词条数（Ticket #20 + #22）。
     * 「我的」Tab 进度统计用：已刷 distinct / 总 Y，Y 应只含未跳过 + 未删除的词。
     */
    @Query("SELECT COUNT(*) FROM book_word WHERE bookId = :bookId AND skipped = 0 AND deleted = 0")
    suspend fun countWordsInBook(bookId: Long): Int

    @Query("SELECT COUNT(*) FROM book_word WHERE bookId = :bookId AND skipped = 0 AND deleted = 0")
    fun observeWordCountInBook(bookId: Long): Flow<Int>

    // ---- Ticket #20：跳过标记（词书级独立）----

    /**
     * 设置某条 (bookId, wordId) 关联的跳过标记。词书级：只改这一条关联，
     * 不影响同词在其他词书的关联行（CET-4 跳过不动 CET-6）。
     *
     * @param skipped true=跳过（隐藏，skipped=1）；false=恢复（skipped=0，重新进刷卡列表）
     */
    @Query("UPDATE book_word SET skipped = :skipped WHERE bookId = :bookId AND wordId = :wordId")
    suspend fun setSkipped(bookId: Long, wordId: Long, skipped: Boolean)

    /** 查某条关联当前的 skipped 状态。无此关联（已 unlink）返回 null。 */
    @Query("SELECT skipped FROM book_word WHERE bookId = :bookId AND wordId = :wordId LIMIT 1")
    suspend fun getSkipped(bookId: Long, wordId: Long): Boolean?

    /**
     * 把该词在**所有词书**的 skipped 标记全置 0（恢复）。
     *
     * 恢复语义（issue #1）：一个词可能在 CET-4 和 CET-6 都被跳过，恢复时一次性全清，
     * 词回到所有原词书可刷。与 [setSkipped]（词书级，只改一条）互补：setSkipped 用于
     * 跳过（单本），unskipWordEverywhere 用于恢复（全局）。
     */
    @Query("UPDATE book_word SET skipped = 0 WHERE wordId = :wordId")
    suspend fun unskipWordEverywhere(wordId: Long)

    // ---- Ticket #22：软删除（词书级独立，进回收站）----

    /**
     * 设置某条 (bookId, wordId) 关联的软删标记。词书级：只改这一条关联，
     * 不影响同词在其他词书的关联行（CET-4 删只动 CET-4）。
     *
     * 与 [setSkipped] 平行但语义不同：
     * - skipped=true = 跳过（自动进复习词书，刷卡片列表过滤）
     * - deleted=true = 软删（进回收站，列表/统计过滤；可恢复或永久删除）
     *
     * @param deleted true=软删（隐藏，deleted=1，进回收站）；false=恢复（deleted=0，回词书列表）
     */
    @Query("UPDATE book_word SET deleted = :deleted WHERE bookId = :bookId AND wordId = :wordId")
    suspend fun setDeleted(bookId: Long, wordId: Long, deleted: Boolean)

    /** 查某条关联当前的 deleted 状态。无此关联（已 unlink）返回 null。 */
    @Query("SELECT deleted FROM book_word WHERE bookId = :bookId AND wordId = :wordId LIMIT 1")
    suspend fun getDeleted(bookId: Long, wordId: Long): Boolean?

    /**
     * 回收站列表（响应式）：所有 deleted=1 的关联 + 关联的词书名/词文本。
     *
     * INNER JOIN book/word：词书或词若被真删（CASCADE），关联行也跟着没了，
     * 不会出现「关联在但词书/词找不到」的孤儿条目。回收站永远只显示可恢复的完整条目。
     * 排序按 bookId 分组、词文本升序，便于用户在回收站里找。
     */
    @Query(
        """
        SELECT bw.bookId AS bookId, bw.wordId AS wordId,
               b.name AS bookName, w.text AS wordText
        FROM book_word bw
        INNER JOIN book b ON b.bookId = bw.bookId
        INNER JOIN word w ON w.wordId = bw.wordId
        WHERE bw.deleted = 1
        ORDER BY bw.bookId ASC, w.text ASC
        """
    )
    fun observeDeletedEntries(): Flow<List<DeletedEntry>>

    /**
     * 永久删除（真 DELETE）：清掉这条 (bookId, wordId) 关联。
     *
     * `deleted = 1` 兜底：只允许从回收站永久删除，避免误用此方法删未软删的关联。
     * CASCADE 不触发（关联行被删，不会级联到 book/word —— word 本身永远在全局词池保留）。
     * 与 [unlinkBookWord] 区别：unlinkBookWord 是「从词书移除」（直接删关联，无回收站），
     * purgeDeleted 是「从回收站永久删除」（仅对已软删的关联生效）。
     */
    @Query("DELETE FROM book_word WHERE bookId = :bookId AND wordId = :wordId AND deleted = 1")
    suspend fun purgeDeleted(bookId: Long, wordId: Long)

    // ---- Ticket #22：词书级软删除恢复 / 永久删除 ----

    /** 恢复词书：deleted 置 0，词书回到词库列表。其下 book_word 关联状态原样保留。 */
    @Query("UPDATE book SET deleted = 0 WHERE bookId = :bookId")
    suspend fun restoreBook(bookId: Long)

    /**
     * 回收站里的已软删词书（响应式）。只列自定义词书（预置词书 isPreset=0 兜底不会被软删，
     * 这里 WHERE deleted=1 已隐含 isPreset=0，但显式写更清晰）。
     */
    @Query("SELECT * FROM book WHERE deleted = 1 ORDER BY name ASC")
    fun observeDeletedBooks(): Flow<List<Book>>

    /**
     * 永久删除词书（真 DELETE）：CASCADE 清掉其下所有 book_word 关联。
     * 与 [deleteCustom]（软删）区别：这是不可逆的真删。`deleted = 1` 兜底只允许从回收站永久删。
     */
    @Query("DELETE FROM book WHERE bookId = :bookId AND deleted = 1")
    suspend fun purgeBook(bookId: Long)

    // ---- Ticket #10：整库导出/导入 ----

    /** 全量读所有词书（导出快照用）。 */
    @Query("SELECT * FROM book")
    suspend fun getAll(): List<Book>

    /** 全量读所有词书-词条关联（导出快照用）。 */
    @Query("SELECT * FROM book_word")
    suspend fun getAllLinks(): List<BookWord>

    /** 清空词书表（导入覆盖前清理）。 */
    @Query("DELETE FROM book")
    suspend fun deleteAll()

    /** 清空词书-词条关联表（导入覆盖前清理）。 */
    @Query("DELETE FROM book_word")
    suspend fun deleteAllLinks()

    /** 批量插入词书（带原始 bookId，导入覆盖恢复用）。 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(books: List<Book>)

    /** 批量建立词书-词条关联（导入恢复用）。 */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun linkAll(links: List<BookWord>)
}
