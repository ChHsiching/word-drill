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
 */
data class BookWithCount(
    val bookId: Long,
    val name: String,
    val isPreset: Boolean,
    val wordCount: Int,
)

@Dao
interface BookDao {

    // ---- 词书 CRUD ----

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(book: Book): Long

    @Query("SELECT * FROM book ORDER BY isPreset DESC, name ASC")
    fun observeAll(): Flow<List<Book>>

    /**
     * 词书 + 词条数（Ticket #16）。LEFT JOIN 保证空词书（0 词）也列出。
     * 排序与 [observeAll] 一致：预置在前、name 升序。
     *
     * Ticket #20：只计 `bw.skipped = 0` 的未跳过词 —— 跳过的词不算进副标题词数。
     */
    @Query(
        """
        SELECT b.bookId AS bookId, b.name AS name, b.isPreset AS isPreset,
               COUNT(bw.wordId) AS wordCount
        FROM book b
        LEFT JOIN book_word bw ON b.bookId = bw.bookId AND bw.skipped = 0
        GROUP BY b.bookId
        ORDER BY b.isPreset DESC, b.name ASC
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

    @Query("DELETE FROM book WHERE bookId = :bookId AND isPreset = 0")
    suspend fun deleteCustom(bookId: Long): Int

    // ---- 词书-词条 关联 ----

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun linkBookWord(bookWord: BookWord): Long

    @Query("DELETE FROM book_word WHERE bookId = :bookId AND wordId = :wordId")
    suspend fun unlinkBookWord(bookId: Long, wordId: Long)

    /**
     * 词书内未跳过的词条数（Ticket #20）。跳过的词（skipped=1）不计入。
     * 「我的」Tab 进度统计用：已刷 distinct / 总 Y，Y 应只含未跳过的词。
     */
    @Query("SELECT COUNT(*) FROM book_word WHERE bookId = :bookId AND skipped = 0")
    suspend fun countWordsInBook(bookId: Long): Int

    @Query("SELECT COUNT(*) FROM book_word WHERE bookId = :bookId AND skipped = 0")
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
