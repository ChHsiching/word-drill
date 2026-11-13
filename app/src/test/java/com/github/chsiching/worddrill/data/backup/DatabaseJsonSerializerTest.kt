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
 * Pure JVM tests for DatabaseJsonSerializer (secondary seam: the JSON serialization logic for export/import).
 * The spec lists serialization/deserialization as a secondary seam, and it must not depend on the Android file
 * system, so these tests only feed in-memory data and JSON strings and verify the serialize/deserialize round
 * trip and its edge cases.
 *
 * Covers: round-trip equality, the empty database, with/without nickname, orphan swipe_log rows
 * (referencing a deleted book) kept, and corrupt JSON fallback.
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
            // The book with bookId=2 still exists after import; a normal reference.
            SwipeLog(logId = 1000, bookId = 2, wordId = 10, timestamp = 1_700_000_000_000L),
            // Orphan log: bookId=999 does not exist in the exported snapshot (simulates a deleted book whose logs are kept for cumulative statistics).
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
        // Orphan logs must be kept, since cumulative statistics cannot lose them. The restore-with-original-IDs strategy guarantees this.
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
        // The nickname field does not appear in the JSON, because null is not written.
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
        // A blank nickname counts as "no nickname" (avoids a blank label in the exported file).
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

    // ---- Ticket #14: word.phonetic round trip (the transcription survives export/import) ----

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
        // A word without a transcription stays null.
        assertThat(restored.words.first { it.text == "run" }.phonetic).isNull()
    }

    @Test
    fun deserialize_phonetic_defaultsNull_whenMissing() {
        // Compatible with export files from before Ticket #14 (words has no phonetic field).
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
        // A null phonetic does not appear in the exported JSON (avoids an empty tag).
        // Match "phonetic" appearing as a key name (careful not to match a value that contains "phonetic").
        assertThat(json.contains("\"phonetic\"")).isFalse()
    }

    @Test
    fun deserialize_phonetic_isNull_whenJsonNullLiteral() {
        // ⚠️ Regression guard: an explicit "phonetic": null must parse to Kotlin null (same pitfall as PresetWordsParser).
        val json = """
            {"version":1,"books":[],"words":[
              {"wordId":1,"text":"x","phonetic":null}
            ],"senses":[],"bookWords":[],"swipeLogs":[]}
        """.trimIndent()
        val restored = DatabaseJsonSerializer.deserialize(json)
        assertThat(restored.words.single().phonetic).isNull()
    }

    // ---- Ticket #20: book_word.skipped round trip (the skipped flag survives export/import) ----

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
        // Compatible with export files from before Ticket #20 (bookWords has no skipped field).
        val json = """
            {"version":1,"books":[],"words":[],"senses":[],
             "bookWords":[{"bookId":1,"wordId":10}],
             "swipeLogs":[]}
        """.trimIndent()
        val restored = DatabaseJsonSerializer.deserialize(json)
        assertThat(restored.bookWords.single().skipped).isFalse()
    }
}
