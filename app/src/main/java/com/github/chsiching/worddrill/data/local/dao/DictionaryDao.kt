package com.github.chsiching.worddrill.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.github.chsiching.worddrill.data.local.entity.DictionaryEntry

@Dao
interface DictionaryDao {

    /** Case-sensitive: ECDICT stores base-form (mostly lowercase) spellings, so callers pass
     *  the lowercased base form. */
    @Query("SELECT * FROM dictionary WHERE word = :word")
    suspend fun findByWord(word: String): List<DictionaryEntry>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(entries: List<DictionaryEntry>)

    @Query("DELETE FROM dictionary")
    suspend fun clear()

    @Query("SELECT COUNT(*) FROM dictionary")
    suspend fun count(): Int
}
