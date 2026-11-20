package com.github.chsiching.worddrill.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.github.chsiching.worddrill.data.local.entity.DictionaryEntry

@Dao
interface DictionaryDao {

    /** Case-insensitive: the Youdao asset keeps entry casing (April, AIDS), while callers
     *  pass the lowercased import row, so the match folds case in SQL. */
    @Query("SELECT * FROM dictionary WHERE word = :word COLLATE NOCASE")
    suspend fun findByWord(word: String): List<DictionaryEntry>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(entries: List<DictionaryEntry>)

    @Query("DELETE FROM dictionary")
    suspend fun clear()

    @Query("SELECT COUNT(*) FROM dictionary")
    suspend fun count(): Int
}
