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

    // ---- Ticket #14：word.phonetic 往返（导出/导入不丢音标）----

    @Test
    fun roundTrip_preservesPhonetic() {
        val withPhonetic = snapshot.copy(
            words = listOf(
                Word(wordId = 10, text = "apple", phonetic = "/ˈæpl/"),
                Word(wordId = 20, text = "run"),
            )
        )
        val restored = DatabaseJsonSerializer.deserialize(
            DatabaseJsonSerializer.serialize(withPhonetic)
        )
        assertThat(restored.words.first { it.text == "apple" }.phonetic).isEqualTo("/ˈæpl/")
        // 无音标的词保持 null
        assertThat(restored.words.first { it.text == "run" }.phonetic).isNull()
    }

    @Test
    fun deserialize_phonetic_defaultsNull_whenMissing() {
        // 兼容 Ticket #14 之前的导出文件（words 里没有 phonetic 字段）
        val json = """
            {"version":1,"books":[],"words":[
              {"wordId":1,"text":"legacy"}
            ],"senses":[],"bookWords":[],"swipeLogs":[]}
        """.trimIndent()
        val restored = DatabaseJsonSerializer.deserialize(json)
        assertThat(restored.words.single().phonetic).isNull()
    }

    @Test
    fun serialize_omitsPhonetic_whenNull() {
        val noPhonetic = snapshot.copy(
            words = listOf(Word(wordId = 10, text = "apple", phonetic = null))
        )
        val json = DatabaseJsonSerializer.serialize(noPhonetic)
        // null phonetic 不出现在导出 JSON 里（避免空标签）
        // 匹配 "phonetic" 作为键名出现（注意不要误匹配到 "phonetic" 的值）
        assertThat(json.contains("\"phonetic\"")).isFalse()
    }

    @Test
    fun deserialize_phonetic_isNull_whenJsonNullLiteral() {
        // ⚠️ 回归防护：显式 "phonetic": null 必须解析为 Kotlin null（PresetWordsParser 同款坑）。
        val json = """
            {"version":1,"books":[],"words":[
              {"wordId":1,"text":"x","phonetic":null}
            ],"senses":[],"bookWords":[],"swipeLogs":[]}
        """.trimIndent()
        val restored = DatabaseJsonSerializer.deserialize(json)
        assertThat(restored.words.single().phonetic).isNull()
    }

    // ---- Ticket #20：book_word.skipped 往返（跳过状态导出/导入不丢）----

    @Test
    fun roundTrip_preservesSkippedFlag() {
        val withSkipped = snapshot.copy(
            bookWords = listOf(
                BookWord(bookId = 1, wordId = 10, skipped = true),
                BookWord(bookId = 1, wordId = 20, skipped = false),
            )
        )
        val restored = DatabaseJsonSerializer.deserialize(
            DatabaseJsonSerializer.serialize(withSkipped)
        )
        assertThat(restored.bookWords.find { it.wordId == 10L }?.skipped).isTrue()
        assertThat(restored.bookWords.find { it.wordId == 20L }?.skipped).isFalse()
    }

    @Test
    fun deserialize_skipped_defaultsFalse_whenMissing() {
        // 兼容 Ticket #20 之前的导出文件（bookWords 里没有 skipped 字段）
        val json = """
            {"version":1,"books":[],"words":[],"senses":[],
             "bookWords":[{"bookId":1,"wordId":10}],
             "swipeLogs":[]}
        """.trimIndent()
        val restored = DatabaseJsonSerializer.deserialize(json)
        assertThat(restored.bookWords.single().skipped).isFalse()
    }
}
