package com.github.chsiching.worddrill.data.backup

import com.github.chsiching.worddrill.data.local.entity.Book
import com.github.chsiching.worddrill.data.local.entity.BookWord
import com.github.chsiching.worddrill.data.local.entity.Sense
import com.github.chsiching.worddrill.data.local.entity.SwipeLog
import com.github.chsiching.worddrill.data.local.entity.Word
import com.google.common.truth.Truth.assertThat
import org.json.JSONException
import org.junit.Test

/**
 * DatabaseJsonSerializer 的纯 JVM 单测（副接缝：导出/导入的 JSON 序列化逻辑）。
 * 规格把序列化/反序列化列为副接缝，必须不依赖 Android 文件系统 —— 这里只喂内存数据
 * 与 JSON 字符串，验证 serialize/deserialize 往返与边界。
 *
 * 覆盖：往返等价、空库、含昵称/不含昵称、孤立的 swipe_log（引用已删词书）保留、损坏 JSON 回退。
 */
class DatabaseJsonSerializerTest {

    private val snapshot = DatabaseSnapshot(
        version = 1,
        nickname = "我的备份",
        books = listOf(
            Book(bookId = 1, name = "CET-4", isPreset = true),
            Book(bookId = 2, name = "我的词书", isPreset = false),
        ),
        words = listOf(
            Word(wordId = 10, text = "apple"),
            Word(wordId = 20, text = "run"),
        ),
        senses = listOf(
            Sense(senseId = 100, wordId = 10, pos = "n.", meaning = "苹果"),
            Sense(senseId = 101, wordId = 20, pos = "vi.", meaning = "跑"),
            Sense(senseId = 102, wordId = 20, pos = "vt.", meaning = "经营"),
        ),
        bookWords = listOf(
            BookWord(bookId = 1, wordId = 10),
            BookWord(bookId = 1, wordId = 20),
            BookWord(bookId = 2, wordId = 10),
        ),
        swipeLogs = listOf(
            // bookId=2 的词书导入后仍在；正常引用
            SwipeLog(logId = 1000, bookId = 2, wordId = 10, timestamp = 1_700_000_000_000L),
            // 孤立日志：bookId=999 在导出快照里不存在（模拟词书已删但日志保留用于累计统计）
            SwipeLog(logId = 1001, bookId = 999, wordId = 10, timestamp = 1_700_000_001_000L),
        ),
    )

    @Test
    fun roundTrip_preservesAllData() {
        val json = DatabaseJsonSerializer.serialize(snapshot)
        val restored = DatabaseJsonSerializer.deserialize(json)
        assertThat(restored).isEqualTo(snapshot)
    }

    @Test
    fun roundTrip_preservesOrphanSwipeLogs() {
        // 孤立日志必须保留（累计统计不可丢）—— 用原始 ID 恢复策略保证
        val json = DatabaseJsonSerializer.serialize(snapshot)
        val restored = DatabaseJsonSerializer.deserialize(json)
        assertThat(restored.swipeLogs.map { it.bookId }).contains(999L)
        assertThat(restored.swipeLogs).hasSize(2)
    }

    @Test
    fun serialize_includesNickname_whenPresent() {
        val json = DatabaseJsonSerializer.serialize(snapshot)
        assertThat(json).contains("\"nickname\"")
        assertThat(json).contains("我的备份")
    }

    @Test
    fun serialize_omitsNickname_whenNull() {
        val noNick = snapshot.copy(nickname = null)
        val json = DatabaseJsonSerializer.serialize(noNick)
        // nickname 字段不出现在 JSON 里（null 不写）
        assertThat(json.contains("nickname")).isFalse()
    }

    @Test
    fun roundTrip_emptyDatabase() {
        val empty = DatabaseSnapshot(version = 1, nickname = null)
        val restored = DatabaseJsonSerializer.deserialize(DatabaseJsonSerializer.serialize(empty))
        assertThat(restored).isEqualTo(empty)
        assertThat(restored.books).isEmpty()
        assertThat(restored.words).isEmpty()
        assertThat(restored.swipeLogs).isEmpty()
    }

    @Test
    fun deserialize_stripsNicknameWhitespace_toEmpty() {
        // 空白昵称视为"无昵称"（避免导出文件里出现空白标签）
        val json = """{"version":1,"nickname":"   ","books":[],"words":[],"senses":[],"bookWords":[],"swipeLogs":[]}"""
        val restored = DatabaseJsonSerializer.deserialize(json)
        assertThat(restored.nickname).isNull()
    }

    @Test
    fun deserialize_defaultsVersion_whenMissing() {
        val json = """{"nickname":null,"books":[],"words":[],"senses":[],"bookWords":[],"swipeLogs":[]}"""
        val restored = DatabaseJsonSerializer.deserialize(json)
        assertThat(restored.version).isEqualTo(1)
    }

    @Test(expected = JSONException::class)
    fun deserialize_corruptJson_throws() {
        DatabaseJsonSerializer.deserialize("not a json")
    }

    @Test(expected = JSONException::class)
    fun deserialize_missingBooksArray_throws() {
        DatabaseJsonSerializer.deserialize("""{"version":1}""")
    }
}
