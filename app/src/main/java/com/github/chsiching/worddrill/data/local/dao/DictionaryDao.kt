package com.github.chsiching.worddrill.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.github.chsiching.worddrill.data.local.entity.DictionaryEntry

/**
 * Ticket #19: Built-in dictionary DAO. Read-only queries + first-launch batch insert; no
 * single-row insert/update/delete (the dictionary table is read-only reference data).
 *
 * Usage: when adding words or importing files, call [findByWord] to look up
 * pos + meaning + phonetic by spelling.
 */
@Dao
interface DictionaryDao {

    /**
     * Look up dictionary entries by spelling (multiple parts of speech for one word = multiple rows).
     * Case-sensitive: ECDICT's word column stores the base form (mostly lowercase), callers pass the base form.
     * Callers that need case-insensitive matching should lowercase first, then query.
     */
    @Query("SELECT * FROM dictionary WHERE word = :word")
    suspend fun findByWord(word: String): List<DictionaryEntry>

    /** First-launch batch insert (IGNORE plus the (word, pos) unique index, so duplicate imports do not error). */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(entries: List<DictionaryEntry>)

    /**
     * Ticket #23: Clear the dictionary table. The orchestrator calls this when it detects the
     * dictionary.json content has been fixed
     * ([com.github.chsiching.worddrill.data.DictionaryImportOrchestrator.DICTIONARY_VERSION]
     * upgraded), so old polluted rows are removed before refilling. INSERT IGNORE alone cannot overwrite old rows.
     */
    @Query("DELETE FROM dictionary")
    suspend fun clear()

    /** For tests / diagnostics: returns the total entry count. */
    @Query("SELECT COUNT(*) FROM dictionary")
    suspend fun count(): Int
}
