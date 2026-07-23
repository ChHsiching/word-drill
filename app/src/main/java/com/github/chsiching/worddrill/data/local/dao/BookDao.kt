package com.github.chsiching.worddrill.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.github.chsiching.worddrill.data.local.entity.Book
import com.github.chsiching.worddrill.data.local.entity.BookWord
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {

    // ---- 词书 CRUD ----

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(book: Book): Long

    @Query("SELECT * FROM book ORDER BY isPreset DESC, name ASC")
    fun observeAll(): Flow<List<Book>>

    @Query("SELECT * FROM book WHERE bookId = :bookId")
    suspend fun getById(bookId: Long): Book?

    /** 按名字查词书。导入幂等用：重名预置词书复用既有行，不新建重复行。 */
    @Query("SELECT * FROM book WHERE name = :name LIMIT 1")
    suspend fun getByName(name: String): Book?

    @Query("UPDATE book SET name = :name WHERE bookId = :bookId")
    suspend fun rename(bookId: Long, name: String)

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
