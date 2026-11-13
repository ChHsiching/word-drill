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

    // ---- Word entry CRUD ----

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(word: Word): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSense(sense: Sense): Long

    @Query("SELECT * FROM word WHERE wordId = :wordId")
    suspend fun getById(wordId: Long): Word?

    /** Deduplicate by text, returns the existing wordId (or null). Used to reuse global word entries on import/insert. */
    @Query("SELECT wordId FROM word WHERE text = :text LIMIT 1")
    suspend fun findIdByText(text: String): Long?

    @Query("SELECT * FROM word WHERE text = :text LIMIT 1")
    suspend fun getByText(text: String): Word?

    @Query("SELECT * FROM sense WHERE wordId = :wordId")
    suspend fun getSensesForWord(wordId: Long): List<Sense>

    /**
     * Query word entries by book (including each word's sense list).
     * Ticket #20: filters `bw.skipped = 0` so skipped words stay off the card list.
     * Ticket #22: filters `bw.deleted = 0` so soft-deleted words stay off the card list.
     * @Transaction keeps the @Relation two-step query in one transaction, reading a consistent snapshot.
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
     * Ticket #7: reactive word entry query by book (with sense lists).
     * The book list page subscribes; re-emitted automatically after inserts/deletes/updates.
     * Ticket #20: filters `bw.skipped = 0`, the word list page only shows non-skipped words.
     * Ticket #22: filters `bw.deleted = 0`, the word list page only shows non-soft-deleted words.
     * @Transaction keeps the @Relation two-step query reading a consistent snapshot.
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

    // ---- Ticket #10: full-database export/import ----

    /** Read all word entries (for the export snapshot). */
    @Query("SELECT * FROM word")
    suspend fun getAll(): List<Word>

    /** Read all senses (for the export snapshot). */
    @Query("SELECT * FROM sense")
    suspend fun getAllSenses(): List<Sense>

    /** Clear the sense table (cleanup before an import overwrites). */
    @Query("DELETE FROM sense")
    suspend fun deleteAllSenses()

    /** Clear the word table (cleanup before an import overwrites). */
    @Query("DELETE FROM word")
    suspend fun deleteAll()

    /** Batch-insert word entries (with original wordIds, to restore an import overwrite). */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(words: List<Word>)

    /** Batch-insert senses (with original senseIds, to restore an import overwrite). */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllSenses(senses: List<Sense>)
}
