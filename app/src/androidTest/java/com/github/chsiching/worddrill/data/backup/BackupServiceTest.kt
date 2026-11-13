package com.github.chsiching.worddrill.data.backup

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.chsiching.worddrill.data.local.WordDrillDatabase
import com.github.chsiching.worddrill.data.local.entity.Book
import com.github.chsiching.worddrill.data.local.entity.BookWord
import com.github.chsiching.worddrill.data.local.entity.Sense
import com.github.chsiching.worddrill.data.local.entity.SwipeLog
import com.github.chsiching.worddrill.data.local.entity.Word
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented test for BackupService (in-memory Room).
 *
 * Acceptance coverage (spec AC "swipe stats, books, and entries all restore correctly after import"):
 * - export reads all 5 tables in full (including orphan swipe_logs)
 * - import overwrites: after importing a new snapshot the old data is cleared and the new data lands with the original primary keys
 * - After import, swipe stats, books, entries, senses, and links are all correct
 * - Full serialization round trip (export, serialize, deserialize, import)
 *
 * Complements [DatabaseJsonSerializerTest] (the JVM sub-seam): this test covers the mapping between tables and snapshots and the transaction orchestration.
 */
@RunWith(AndroidJUnit4::class)
class BackupServiceTest {

    private lateinit var db: WordDrillDatabase
    private lateinit var backup: BackupService

    @Before
    fun setUp() = kotlinx.coroutines.runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, WordDrillDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        backup = BackupService(db, db.bookDao(), db.wordDao(), db.swipeLogDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun export_readsAllFiveTables_includingOrphanSwipeLogs() = runTest {
        seedFixtures()
        // Create an orphan swipe_log (referencing a nonexistent bookId)
        db.swipeLogDao().insert(SwipeLog(logId = 9999, bookId = 999, wordId = 10, timestamp = 123L))

        val snapshot = backup.export(nickname = "测试")

        assertThat(snapshot.nickname).isEqualTo("测试")
        assertThat(snapshot.books.map { it.name }).containsExactly("CET-4", "我的")
        assertThat(snapshot.words.map { it.text }).containsExactly("apple", "run")
        assertThat(snapshot.senses.map { it.pos }).containsExactly("n.", "vi.", "vt.")
        assertThat(snapshot.bookWords).hasSize(3)
        // The orphan log must be read (total stats must not be lost)
        assertThat(snapshot.swipeLogs.map { it.logId }).contains(9999L)
    }

    @Test
    fun import_overwritesOldData_andRestoresByOriginalIds() = runTest {
        seedFixtures()
        // Original state: 2 books, 2 words
        assertThat(db.bookDao().getAll()).hasSize(2)
        assertThat(db.wordDao().getAll()).hasSize(2)

        // Import a brand-new snapshot (overwrite)
        val snapshot = DatabaseSnapshot(
            version = 1,
            nickname = null,
            books = listOf(Book(bookId = 50, name = "新词书", isPreset = false)),
            words = listOf(Word(wordId = 60, text = "newword")),
            senses = listOf(Sense(senseId = 61, wordId = 60, pos = "n.", meaning = "新词")),
            bookWords = listOf(BookWord(bookId = 50, wordId = 60)),
            swipeLogs = listOf(SwipeLog(logId = 70, bookId = 50, wordId = 60, timestamp = 999L)),
        )
        backup.import(snapshot)

        // The old data is cleared
        assertThat(db.bookDao().getAll().map { it.name }).containsExactly("新词书")
        assertThat(db.wordDao().getAll().map { it.text }).containsExactly("newword")
        // Landed with the original primary keys
        assertThat(db.bookDao().getById(50)?.name).isEqualTo("新词书")
        assertThat(db.wordDao().getById(60)?.text).isEqualTo("newword")
        assertThat(db.wordDao().getSensesForWord(60).map { it.pos }).containsExactly("n.")
        // Links and logs restored
        assertThat(db.bookDao().countWordsInBook(50)).isEqualTo(1)
        assertThat(db.swipeLogDao().totalCount()).isEqualTo(1)
    }

    @Test
    fun import_clearsAllSwipeLogs_beforeRestore() = runTest {
        seedFixtures()
        assertThat(db.swipeLogDao().totalCount()).isGreaterThan(0)

        backup.import(DatabaseSnapshot(version = 1, nickname = null))

        // After importing an empty snapshot the logs are cleared
        assertThat(db.swipeLogDao().totalCount()).isEqualTo(0)
        assertThat(db.bookDao().getAll()).isEmpty()
    }

    @Test
    fun roundTrip_exportSerializeDeserializeImport_preservesAllData() = runTest {
        seedFixtures()
        val original = backup.export(nickname = "往返")
        val json = DatabaseJsonSerializer.serialize(original)
        // Clear, then restore with the deserialized snapshot
        backup.import(DatabaseJsonSerializer.deserialize(json))

        val restored = backup.export(nickname = null)
        // nickname is overwritten by the snapshot on import, and the second export passes none, so it is null; compare the other fields
        assertThat(restored.books).isEqualTo(original.books)
        assertThat(restored.words).isEqualTo(original.words)
        assertThat(restored.senses).isEqualTo(original.senses)
        assertThat(restored.bookWords).isEqualTo(original.bookWords)
        assertThat(restored.swipeLogs).isEqualTo(original.swipeLogs)
    }

    @Test
    fun import_thenStats_recoverCorrectly() = runTest {
        // Spec AC "swipe stats, books, and entries all restore correctly after import"
        // Book 1 (10 words, 3 distinct swiped), book 2 (5 words, 2 distinct swiped), 5 logs in total
        val book1Words = (1..10).map { Word(wordId = it.toLong(), text = "w$it") }
        val book2Words = (11..15).map { Word(wordId = it.toLong(), text = "w$it") }
        val book1Links = (1..10).map { BookWord(bookId = 1, wordId = it.toLong()) }
        val book2Links = (11..15).map { BookWord(bookId = 2, wordId = it.toLong()) }
        val logs = listOf(
            SwipeLog(logId = 100, bookId = 1, wordId = 1, timestamp = 1),
            SwipeLog(logId = 101, bookId = 1, wordId = 2, timestamp = 2),
            SwipeLog(logId = 102, bookId = 1, wordId = 3, timestamp = 3),
            SwipeLog(logId = 103, bookId = 2, wordId = 11, timestamp = 4),
            SwipeLog(logId = 104, bookId = 2, wordId = 12, timestamp = 5),
        )
        backup.import(
            DatabaseSnapshot(
                version = 1,
                nickname = null,
                books = listOf(
                    Book(bookId = 1, name = "B1", isPreset = false),
                    Book(bookId = 2, name = "B2", isPreset = false),
                ),
                words = book1Words + book2Words,
                senses = emptyList(),
                bookWords = book1Links + book2Links,
                swipeLogs = logs,
            )
        )

        // 5 logs in total
        assertThat(db.swipeLogDao().totalCount()).isEqualTo(5)
        // B1 has 3 distinct swiped / 10 total; B2 has 2 distinct swiped / 5 total
        assertThat(db.swipeLogDao().distinctWordCountForBook(1)).isEqualTo(3)
        assertThat(db.bookDao().countWordsInBook(1)).isEqualTo(10)
        assertThat(db.swipeLogDao().distinctWordCountForBook(2)).isEqualTo(2)
        assertThat(db.bookDao().countWordsInBook(2)).isEqualTo(5)
        // Book list order: preset first (none here), then by name
        assertThat(db.bookDao().observeAll().first().map { it.name }).containsExactly("B1", "B2")
    }

    /** Seeds standard fixtures: 2 books (preset CET-4 + a custom one), 2 words, 3 senses, 3 links, 2 logs. */
    private suspend fun seedFixtures() {
        db.bookDao().insertAll(
            listOf(
                Book(bookId = 1, name = "CET-4", isPreset = true),
                Book(bookId = 2, name = "我的", isPreset = false),
            )
        )
        db.wordDao().insertAll(
            listOf(
                Word(wordId = 10, text = "apple"),
                Word(wordId = 20, text = "run"),
            )
        )
        db.wordDao().insertAllSenses(
            listOf(
                Sense(senseId = 100, wordId = 10, pos = "n.", meaning = "苹果"),
                Sense(senseId = 101, wordId = 20, pos = "vi.", meaning = "跑"),
                Sense(senseId = 102, wordId = 20, pos = "vt.", meaning = "经营"),
            )
        )
        db.bookDao().linkAll(
            listOf(
                BookWord(bookId = 1, wordId = 10),
                BookWord(bookId = 1, wordId = 20),
                BookWord(bookId = 2, wordId = 10),
            )
        )
        db.swipeLogDao().insertAll(
            listOf(
                SwipeLog(logId = 1000, bookId = 1, wordId = 10, timestamp = 1_700_000_000_000L),
                SwipeLog(logId = 1001, bookId = 2, wordId = 10, timestamp = 1_700_000_001_000L),
            )
        )
    }
}
