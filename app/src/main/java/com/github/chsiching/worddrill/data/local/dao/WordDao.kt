package com.github.chsiching.worddrill.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.github.chsiching.worddrill.data.local.WordPhonetics
import com.github.chsiching.worddrill.data.local.WordWithSenses
import com.github.chsiching.worddrill.data.local.entity.Sense
import com.github.chsiching.worddrill.data.local.entity.Word
import kotlinx.coroutines.flow.Flow

@Dao
interface WordDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(word: Word): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSense(sense: Sense): Long

    @Query("SELECT * FROM word WHERE wordId = :wordId")
    suspend fun getById(wordId: Long): Word?

    @Query("SELECT wordId FROM word WHERE text = :text LIMIT 1")
    suspend fun findIdByText(text: String): Long?

    suspend fun findOrInsertWord(text: String, phonetic: String?): Long =
        findIdByText(text)
            ?: insert(Word(text = text, phonetic = phonetic)).let { id ->
                if (id == -1L) findIdByText(text)!! else id
            }

    @Query("SELECT * FROM word WHERE text = :text LIMIT 1")
    suspend fun getByText(text: String): Word?

    /** Preset refresh path: word-table phonetic is app-managed (no user editing), so the
     *  bundled asset's value is authoritative. */
    @Query("UPDATE word SET phonetic = :phonetic WHERE wordId = :wordId")
    suspend fun updatePhonetic(wordId: Long, phonetic: String?)

    @Query("SELECT * FROM sense WHERE wordId = :wordId")
    suspend fun getSensesForWord(wordId: Long): List<Sense>

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

    /** UK/US phonetic pair per deck word (issue #58), for render-time variant
     *  choice. The dictionary entry is the live source (its join folds case like
     *  DictionaryDao.findByWord; MAX dedupes the per-pos rows, which share one
     *  pair per word); the word row's own phonetic backs UK up for words the
     *  dictionary does not carry. Same deck filter as the card query above. */
    @Query(
        """
        SELECT w.wordId AS wordId,
               COALESCE(MAX(d.phonetic), w.phonetic) AS phoneticUk,
               MAX(d.phoneticUs) AS phoneticUs
        FROM word w
        INNER JOIN book_word bw ON w.wordId = bw.wordId
        LEFT JOIN dictionary d ON d.word = w.text COLLATE NOCASE
        WHERE bw.bookId = :bookId AND bw.skipped = 0 AND bw.deleted = 0
        GROUP BY w.wordId
        """
    )
    suspend fun getPhoneticsByBook(bookId: Long): List<WordPhonetics>

    @Query("UPDATE sense SET pos = :pos, meaning = :meaning WHERE senseId = :senseId")
    suspend fun updateSense(senseId: Long, pos: String, meaning: String)

    @Query("DELETE FROM sense WHERE senseId = :senseId")
    suspend fun deleteSense(senseId: Long)

    @Query("DELETE FROM word WHERE wordId = :wordId")
    suspend fun deleteWord(wordId: Long)

    @Query("SELECT * FROM word")
    suspend fun getAll(): List<Word>

    @Query("SELECT * FROM sense")
    suspend fun getAllSenses(): List<Sense>

    @Query("DELETE FROM sense")
    suspend fun deleteAllSenses()

    @Query("DELETE FROM word")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(words: List<Word>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllSenses(senses: List<Sense>)
}
