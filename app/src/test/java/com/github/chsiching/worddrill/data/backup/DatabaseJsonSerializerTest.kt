package com.github.chsiching.worddrill.data.backup

import com.github.chsiching.worddrill.data.local.entity.Book
import com.github.chsiching.worddrill.data.local.entity.BookWord
import com.github.chsiching.worddrill.data.local.entity.Sense
import com.github.chsiching.worddrill.data.local.entity.SwipeLog
import com.github.chsiching.worddrill.data.local.entity.Word
import com.google.common.truth.Truth.assertThat
import org.json.JSONException
import org.junit.Test

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
            SwipeLog(logId = 1000, bookId = 2, wordId = 10, timestamp = 1_700_000_000_000L),
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
        assertThat(restored.words.first { it.text == "run" }.phonetic).isNull()
    }

    @Test
    fun deserialize_phonetic_defaultsNull_whenMissing() {
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
        assertThat(json.contains("\"phonetic\"")).isFalse()
    }

    @Test
    fun deserialize_phonetic_isNull_whenJsonNullLiteral() {
        val json = """
            {"version":1,"books":[],"words":[
              {"wordId":1,"text":"x","phonetic":null}
            ],"senses":[],"bookWords":[],"swipeLogs":[]}
        """.trimIndent()
        val restored = DatabaseJsonSerializer.deserialize(json)
        // Android's bundled org.json optString returns the string "null" on a JSON null;
        // guard with isNull (see DatabaseJsonSerializer).
        assertThat(restored.words.single().phonetic).isNull()
    }

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
        val json = """
            {"version":1,"books":[],"words":[],"senses":[],
             "bookWords":[{"bookId":1,"wordId":10}],
             "swipeLogs":[]}
        """.trimIndent()
        val restored = DatabaseJsonSerializer.deserialize(json)
        assertThat(restored.bookWords.single().skipped).isFalse()
    }

    @Test
    fun roundTrip_preservesCompletionCount() {
        val withCount = snapshot.copy(
            books = listOf(
                Book(bookId = 1, name = "CET-4", isPreset = true, completionCount = 3),
                Book(bookId = 2, name = "我的词书", isPreset = false),
            )
        )
        val restored = DatabaseJsonSerializer.deserialize(
            DatabaseJsonSerializer.serialize(withCount)
        )
        assertThat(restored.books.first { it.name == "CET-4" }.completionCount).isEqualTo(3)
        assertThat(restored.books.first { it.name == "我的词书" }.completionCount).isEqualTo(0)
    }

    @Test
    fun deserialize_completionCount_defaultsZero_whenMissing() {
        // A backup written before the completion count existed.
        val json = """
            {"version":1,"books":[{"bookId":1,"name":"CET-4","isPreset":true}],
             "words":[],"senses":[],"bookWords":[],"swipeLogs":[]}
        """.trimIndent()
        val restored = DatabaseJsonSerializer.deserialize(json)
        assertThat(restored.books.single().completionCount).isEqualTo(0)
    }
}
