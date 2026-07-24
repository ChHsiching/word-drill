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
     */
    @Query(
        """
        SELECT b.bookId AS bookId, b.name AS name, b.isPreset AS isPreset,
               COUNT(bw.wordId) AS wordCount
        FROM book b
        LEFT JOIN book_word bw ON b.bookId = bw.bookId
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

    @Query("SELECT COUNT(*) FROM book_word WHERE bookId = :bookId")
    suspend fun countWordsInBook(bookId: Long): Int

    @Query("SELECT COUNT(*) FROM book_word WHERE bookId = :bookId")
    fun observeWordCountInBook(bookId: Long): Flow<Int>

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
