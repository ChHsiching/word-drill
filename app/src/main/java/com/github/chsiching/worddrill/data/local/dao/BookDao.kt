package com.github.chsiching.worddrill.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.github.chsiching.worddrill.data.local.entity.Book
import com.github.chsiching.worddrill.data.local.entity.BookWord
import kotlinx.coroutines.flow.Flow

data class BookWithCount(
    val bookId: Long,
    val name: String,
    val isPreset: Boolean,
    val wordCount: Int,
)

data class DeletedEntry(
    val bookId: Long,
    val wordId: Long,
    val bookName: String,
    val wordText: String,
)

@Dao
interface BookDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(book: Book): Long

    @Query("SELECT * FROM book WHERE deleted = 0 ORDER BY isPreset DESC, CASE WHEN name = '复习' THEN 1 ELSE 0 END, name ASC")
    fun observeAll(): Flow<List<Book>>

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

    @Query("SELECT * FROM book WHERE name = :name LIMIT 1")
    suspend fun getByName(name: String): Book?

    @Query("UPDATE book SET name = :name WHERE bookId = :bookId AND isPreset = 0")
    suspend fun rename(bookId: Long, name: String): Int

    @Query("UPDATE book SET deleted = 1 WHERE bookId = :bookId AND isPreset = 0")
    suspend fun deleteCustom(bookId: Long): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun linkBookWord(bookWord: BookWord): Long

    @Query("DELETE FROM book_word WHERE bookId = :bookId AND wordId = :wordId")
    suspend fun unlinkBookWord(bookId: Long, wordId: Long)

    @Query("SELECT COUNT(*) FROM book_word WHERE bookId = :bookId AND skipped = 0 AND deleted = 0")
    suspend fun countWordsInBook(bookId: Long): Int

    @Query("SELECT COUNT(*) FROM book_word WHERE bookId = :bookId AND skipped = 0 AND deleted = 0")
    fun observeWordCountInBook(bookId: Long): Flow<Int>

    @Query("UPDATE book_word SET skipped = :skipped WHERE bookId = :bookId AND wordId = :wordId")
    suspend fun setSkipped(bookId: Long, wordId: Long, skipped: Boolean)

    @Query("SELECT skipped FROM book_word WHERE bookId = :bookId AND wordId = :wordId LIMIT 1")
    suspend fun getSkipped(bookId: Long, wordId: Long): Boolean?

    @Query("UPDATE book_word SET skipped = 0 WHERE wordId = :wordId")
    suspend fun unskipWordEverywhere(wordId: Long)

    @Query("UPDATE book_word SET deleted = :deleted WHERE bookId = :bookId AND wordId = :wordId")
    suspend fun setDeleted(bookId: Long, wordId: Long, deleted: Boolean)

    @Query("SELECT deleted FROM book_word WHERE bookId = :bookId AND wordId = :wordId LIMIT 1")
    suspend fun getDeleted(bookId: Long, wordId: Long): Boolean?

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

    @Query("DELETE FROM book_word WHERE bookId = :bookId AND wordId = :wordId AND deleted = 1")
    suspend fun purgeDeleted(bookId: Long, wordId: Long)

    @Query("UPDATE book SET deleted = 0 WHERE bookId = :bookId")
    suspend fun restoreBook(bookId: Long)

    @Query("SELECT * FROM book WHERE deleted = 1 ORDER BY name ASC")
    fun observeDeletedBooks(): Flow<List<Book>>

    @Query("DELETE FROM book WHERE bookId = :bookId AND deleted = 1")
    suspend fun purgeBook(bookId: Long)

    @Query("SELECT * FROM book")
    suspend fun getAll(): List<Book>

    @Query("SELECT * FROM book_word")
    suspend fun getAllLinks(): List<BookWord>

    @Query("DELETE FROM book")
    suspend fun deleteAll()

    @Query("DELETE FROM book_word")
    suspend fun deleteAllLinks()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(books: List<Book>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun linkAll(links: List<BookWord>)
}
