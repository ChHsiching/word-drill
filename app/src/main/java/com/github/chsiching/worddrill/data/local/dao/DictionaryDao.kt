package com.github.chsiching.worddrill.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.github.chsiching.worddrill.data.local.entity.DictionaryEntry

/**
 * Ticket #19：内置词典 DAO。只读查询 + 首启批量写入，不提供单条增删改
 * （dictionary 表是只读参考数据）。
 *
 * 用法：加词 / 文件导入时调 [findByWord] 按拼写查词性 + 释义 + 音标。
 */
@Dao
interface DictionaryDao {

    /**
     * 按拼写查词典条目（一个词的多个词性 = 多行）。
     * 大小写敏感：ECDICT 的 word 列存原形（小写为主），调用方传原形即可。
     * 调用方需要大小写不敏感时自行 lower 后再查。
     */
    @Query("SELECT * FROM dictionary WHERE word = :word")
    suspend fun findByWord(word: String): List<DictionaryEntry>

    /** 首启批量写入（IGNORE 兜底 (word, pos) 唯一索引，重复导入不报错）。 */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(entries: List<DictionaryEntry>)

    /**
     * Ticket #23：清空 dictionary 表。orchestrator 在检测到 dictionary.json 内容有修复
     * （[com.github.chsiching.worddrill.data.DictionaryImportOrchestrator.DICTIONARY_VERSION]
     * 升级）时调用，保证旧污染行被清除后再重新填充——单靠 INSERT IGNORE 无法覆盖旧行。
     */
    @Query("DELETE FROM dictionary")
    suspend fun clear()

    /** 测试 / 诊断用：返回总条目数。 */
    @Query("SELECT COUNT(*) FROM dictionary")
    suspend fun count(): Int
}
