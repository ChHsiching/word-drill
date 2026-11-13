package com.github.chsiching.worddrill.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.github.chsiching.worddrill.data.local.entity.Book
import com.github.chsiching.worddrill.data.local.entity.BookWord
import kotlinx.coroutines.flow.Flow

/**
 * Book + word count (Ticket #16: the book list subtitle needs to show the word count).
 * JOIN + GROUP BY fetches counts for all books in one query, avoiding N queries.
 *
 * Ticket #20: wordCount only counts entries with `skipped = 0`. The "X words" in the book list
 * subtitle reflects the number of words the user will actually swipe. After skipping (hidden, not deleted), the subtitle number drops accordingly.
 *
 * Ticket #22: wordCount also excludes soft-deleted entries with `deleted = 1`, so the subtitle
 * number drops after deletion, consistent with the filters in
 * [WordDao.getWordsWithSensesByBook] / [observeWordsWithSensesByBook].
 */
data class BookWithCount(
    val bookId: Long,
    val name: String,
    val isPreset: Boolean,
    val wordCount: Int,
)

/**
 * Recycle bin entry (Ticket #22): a (bookId, wordId) association with deleted=1 plus the
 * associated book name / word text. Used by the recycle bin list to show "wordText (removed from bookName)".
 */
data class DeletedEntry(
    val bookId: Long,
    val wordId: Long,
    val bookName: String,
    val wordText: String,
)

@Dao
interface BookDao {

    // ---- Book CRUD ----

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(book: Book): Long

    /**
     * All visible books (Ticket #22: filters out soft-deleted books with deleted=1).
     * Ordering: presets first, then name ascending.
     */
    @Query("SELECT * FROM book WHERE deleted = 0 ORDER BY isPreset DESC, CASE WHEN name = '复习' THEN 1 ELSE 0 END, name ASC")
    fun observeAll(): Flow<List<Book>>

    /**
     * Books + word counts (Ticket #16). LEFT JOIN so empty books (0 words) are still listed.
     * Ordering matches [observeAll]: presets first, then name ascending.
     *
     * Ticket #20: only counts words with `bw.skipped = 0`, so skipped words are not counted in the subtitle.
     * Ticket #22: also excludes soft-deleted words with `bw.deleted = 1`, so the subtitle count drops after deletion.
     *            Also filters `b.deleted = 0`, so soft-deleted books themselves stay out of the book list.
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

    /** Look up a book by name. Used for idempotent imports: a preset book with the same name reuses the existing row instead of creating a duplicate. */
    @Query("SELECT * FROM book WHERE name = :name LIMIT 1")
    suspend fun getByName(name: String): Book?

    /**
     * Rename a book. Returns the number of affected rows: 1 for a custom book, 0 for a preset
     * book rejected by WHERE isPreset = 0.
     * Same pattern as [deleteCustom]: preset books are read-only as a DAO-level fallback, and the UI entry is hidden too.
     */
    @Query("UPDATE book SET name = :name WHERE bookId = :bookId AND isPreset = 0")
    suspend fun rename(bookId: Long, name: String): Int

    /**
     * Soft-delete a book (Ticket #22): marks deleted=1, the book goes to the recycle bin and can be restored.
     * Returns the number of affected rows: 1 for a custom book, 0 for a preset book rejected by
     * WHERE isPreset = 0.
     * Same pattern as [rename]: preset books are read-only as a DAO-level fallback, and the UI entry is hidden too.
     *
     * Soft delete does not really delete the book row, nor touch its book_word associations
     * (their individual deleted/skipped states are preserved).
     * Restore goes through [restoreBook] (sets deleted to 0), permanent deletion through [purgeBook] (real DELETE + CASCADE).
     */
    @Query("UPDATE book SET deleted = 1 WHERE bookId = :bookId AND isPreset = 0")
    suspend fun deleteCustom(bookId: Long): Int

    // ---- Book-to-word-entry associations ----

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun linkBookWord(bookWord: BookWord): Long

    @Query("DELETE FROM book_word WHERE bookId = :bookId AND wordId = :wordId")
    suspend fun unlinkBookWord(bookId: Long, wordId: Long)

    /**
     * Count of non-skipped, non-deleted word entries in a book (Ticket #20 + #22).
     * Used by the "Mine" tab progress stat: swiped distinct / total Y, where Y should only
     * include non-skipped + non-deleted words.
     */
    @Query("SELECT COUNT(*) FROM book_word WHERE bookId = :bookId AND skipped = 0 AND deleted = 0")
    suspend fun countWordsInBook(bookId: Long): Int

    @Query("SELECT COUNT(*) FROM book_word WHERE bookId = :bookId AND skipped = 0 AND deleted = 0")
    fun observeWordCountInBook(bookId: Long): Flow<Int>

    // ---- Ticket #20: skip flag (independent per book) ----

    /**
     * Set the skip flag on one (bookId, wordId) association. Book-level: only this one
     * association changes; association rows for the same word in other books are unaffected
     * (skipping in CET-4 does not touch CET-6).
     *
     * @param skipped true=skip (hidden, skipped=1); false=restore (skipped=0, back on the card list)
     */
    @Query("UPDATE book_word SET skipped = :skipped WHERE bookId = :bookId AND wordId = :wordId")
    suspend fun setSkipped(bookId: Long, wordId: Long, skipped: Boolean)

    /** Current skipped state of one association. Returns null if the association no longer exists (already unlinked). */
    @Query("SELECT skipped FROM book_word WHERE bookId = :bookId AND wordId = :wordId LIMIT 1")
    suspend fun getSkipped(bookId: Long, wordId: Long): Boolean?

    /**
     * Reset the skipped flag to 0 (restore) for this word in **all books**.
     *
     * Restore semantics (issue #1): a word may be skipped in both CET-4 and CET-6; restoring
     * clears all of them at once, and the word becomes swipeable again in all its original books.
     * Complements [setSkipped] (book-level, changes one row): setSkipped is for skipping
     * (single book), unskipWordEverywhere is for restoring (global).
     */
    @Query("UPDATE book_word SET skipped = 0 WHERE wordId = :wordId")
    suspend fun unskipWordEverywhere(wordId: Long)

    // ---- Ticket #22: soft delete (independent per book, goes to recycle bin) ----

    /**
     * Set the soft-delete flag on one (bookId, wordId) association. Book-level: only this one
     * association changes; association rows for the same word in other books are unaffected
     * (deleting in CET-4 only touches CET-4).
     *
     * Parallel to [setSkipped] but with different semantics:
     * - skipped=true = skip (automatically goes into the review book, filtered from the card list)
     * - deleted=true = soft delete (goes to the recycle bin, filtered from lists/stats; recoverable or permanently deletable)
     *
     * @param deleted true=soft delete (hidden, deleted=1, goes to the recycle bin); false=restore (deleted=0, back on the book list)
     */
    @Query("UPDATE book_word SET deleted = :deleted WHERE bookId = :bookId AND wordId = :wordId")
    suspend fun setDeleted(bookId: Long, wordId: Long, deleted: Boolean)

    /** Current deleted state of one association. Returns null if the association no longer exists (already unlinked). */
    @Query("SELECT deleted FROM book_word WHERE bookId = :bookId AND wordId = :wordId LIMIT 1")
    suspend fun getDeleted(bookId: Long, wordId: Long): Boolean?

    /**
     * Recycle bin list (reactive): all associations with deleted=1 plus the associated book
     * name / word text.
     *
     * INNER JOIN book/word: if a book or word is really deleted (CASCADE), the association row
     * is gone with it, so there are no orphan entries where the association exists but the
     * book/word cannot be found. The recycle bin only ever shows complete, recoverable entries.
     * Ordered grouped by bookId, then word text ascending, so entries are easy to find.
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
     * Permanent delete (real DELETE): removes this (bookId, wordId) association.
     *
     * `deleted = 1` guard: only allows permanent deletion from the recycle bin, preventing
     * misuse of this method on associations that were not soft-deleted.
     * CASCADE is not triggered (the association row is deleted, nothing cascades to book/word;
     * the word itself always stays in the global word pool).
     * Difference from [unlinkBookWord]: unlinkBookWord means "remove from book" (deletes the
     * association directly, no recycle bin), purgeDeleted means "permanently delete from the
     * recycle bin" (only works on already soft-deleted associations).
     */
    @Query("DELETE FROM book_word WHERE bookId = :bookId AND wordId = :wordId AND deleted = 1")
    suspend fun purgeDeleted(bookId: Long, wordId: Long)

    // ---- Ticket #22: book-level soft-delete restore / permanent delete ----

    /** Restore a book: sets deleted to 0, the book returns to the book list. Its book_word association states are preserved as they were. */
    @Query("UPDATE book SET deleted = 0 WHERE bookId = :bookId")
    suspend fun restoreBook(bookId: Long)

    /**
     * Soft-deleted books in the recycle bin (reactive). Lists only custom books (preset books
     * cannot be soft-deleted thanks to the isPreset=0 guard; here WHERE deleted=1 already implies
     * isPreset=0, but writing it explicitly would be clearer).
     */
    @Query("SELECT * FROM book WHERE deleted = 1 ORDER BY name ASC")
    fun observeDeletedBooks(): Flow<List<Book>>

    /**
     * Permanently delete a book (real DELETE): CASCADE removes all its book_word associations.
     * Difference from [deleteCustom] (soft delete): this is an irreversible real delete.
     * The `deleted = 1` guard only allows permanent deletion from the recycle bin.
     */
    @Query("DELETE FROM book WHERE bookId = :bookId AND deleted = 1")
    suspend fun purgeBook(bookId: Long)

    // ---- Ticket #10: full-database export/import ----

    /** Read all books (for the export snapshot). */
    @Query("SELECT * FROM book")
    suspend fun getAll(): List<Book>

    /** Read all book-word associations (for the export snapshot). */
    @Query("SELECT * FROM book_word")
    suspend fun getAllLinks(): List<BookWord>

    /** Clear the book table (cleanup before an import overwrites). */
    @Query("DELETE FROM book")
    suspend fun deleteAll()

    /** Clear the book-word association table (cleanup before an import overwrites). */
    @Query("DELETE FROM book_word")
    suspend fun deleteAllLinks()

    /** Batch-insert books (with original bookIds, to restore an import overwrite). */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(books: List<Book>)

    /** Batch-create book-word associations (to restore an import). */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun linkAll(links: List<BookWord>)
}
