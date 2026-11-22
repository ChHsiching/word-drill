package com.github.chsiching.worddrill.data

import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.chsiching.worddrill.data.local.WordDrillDatabase
import com.github.chsiching.worddrill.data.settings.SettingsRepository
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The upgrade path of #56, end to end at the data layer: an install holding v0.2-era
 * data (v6 database without phoneticUs, old dictionary rows, old preset membership,
 * user progress) runs the Room migration and both import orchestrators (the same code
 * the app launches on startup). The dictionary must be replaced, the preset books
 * refreshed in place, and every piece of user data keyed by ids must survive.
 */
@RunWith(AndroidJUnit4::class)
class UpgradePathTest {

    private lateinit var dbFile: java.io.File
    private lateinit var db: WordDrillDatabase
    private lateinit var settings: SettingsRepository

    // DataStore state to restore in tearDown: these keys are process-wide and shared
    // with every other instrumented test, so this test must leave them as it found them.
    private var dictionaryVersionBefore = 0L
    private var presetVersionBefore = 0L
    private var resumeWordBefore: Long? = null
    private val cet4FixtureBookId = 1L

    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Before
    fun setUp() = runBlocking {
        settings = SettingsRepository(context)
        dictionaryVersionBefore = settings.dictionaryVersion.first()
        presetVersionBefore = settings.presetVersion.first()
        resumeWordBefore = settings.resumeWordId(cet4FixtureBookId)

        dbFile = java.io.File(context.cacheDir, "upgrade-path-test.db")
        dbFile.delete()
        createV6DatabaseWithOldData()

        db = Room.databaseBuilder(context, WordDrillDatabase::class.java, dbFile.absolutePath)
            .addMigrations(
                WordDrillDatabase.MIGRATION_1_2,
                WordDrillDatabase.MIGRATION_2_3,
                WordDrillDatabase.MIGRATION_3_4,
                WordDrillDatabase.MIGRATION_4_5,
                WordDrillDatabase.MIGRATION_5_6,
                WordDrillDatabase.MIGRATION_6_7,
            )
            .build()
        // Opening the database runs MIGRATION_6_7 and Room's schema validation.
        db.openHelper.writableDatabase

        // Simulate the v0.2.1 DataStore: dictionary at version 2, presets pre-versioning.
        settings.markDictionaryVersion(2L)
        settings.markPresetVersion(0L)
        settings.setCurrentBookId(cet4FixtureBookId)
        settings.setResumeWordId(cet4FixtureBookId, wordIdOf("a"))
        Unit
    }

    @After
    fun tearDown() = runBlocking {
        db.close()
        dbFile.delete()
        settings.markDictionaryVersion(dictionaryVersionBefore)
        settings.markPresetVersion(presetVersionBefore)
        val resume = resumeWordBefore
        if (resume != null) settings.setResumeWordId(cet4FixtureBookId, resume)
        else settings.clearResumeWordId(cet4FixtureBookId)
        settings.setCurrentBookId(999_999L)
        Unit
    }

    @Test
    fun upgrade_migrationAndReImport_replaceDataPreserveUserState() = runBlocking {
        // The same trigger the app runs on startup, against this test's database.
        PresetImportOrchestrator(context, settings, PresetImporter(db, db.wordDao(), db.bookDao()))
            .ensurePresetImported()
        DictionaryImportOrchestrator(context, settings, DictionaryImporter(db, db.dictionaryDao()))
            .ensureDictionaryImported()

        // --- Dictionary replaced with the new asset ---
        val dictionaryDao = db.dictionaryDao()
        assertThat(dictionaryDao.count()).isGreaterThan(100_000)
        // One entry per pos; every entry carries the shipped Youdao phonetics.
        val telephoneEntries = dictionaryDao.findByWord("telephone")
        assertThat(telephoneEntries).isNotEmpty()
        telephoneEntries.forEach { entry ->
            assertThat(entry.phonetic).isEqualTo("/ˈtelɪfəʊn/")
            assertThat(entry.phoneticUs).isEqualTo("/ˈtelɪfoʊn/")
            assertThat(entry.meaning).isNotEqualTo("旧电话")
        }
        // Stale rows from the old dataset are cleared, not kept besides the new ones.
        assertThat(dictionaryDao.findByWord("staleword")).isEmpty()

        // --- Preset books refreshed in place ---
        val cet4 = db.bookDao().getByName("CET-4")!!
        assertThat(cet4.bookId).isEqualTo(cet4FixtureBookId)
        assertThat(db.bookDao().getWordIdsInBook(cet4.bookId)).hasSize(4_070)
        assertThat(db.bookDao().getWordIdsInBook(db.bookDao().getByName("CET-6")!!.bookId)).hasSize(5_341)
        assertThat(db.bookDao().getWordIdsInBook(db.bookDao().getByName("考研英语")!!.bookId)).hasSize(5_658)

        // Word content refreshed to the new asset...
        val a = db.wordDao().getByText("a")!!
        assertThat(a.phonetic).isEqualTo("/ə; eɪ/")
        val aMeanings = db.wordDao().getSensesForWord(a.wordId).map { it.meaning }
        assertThat(aMeanings).doesNotContain("旧释义")
        // ...membership removals applied (Africa left CET-4's new syllabus)...
        assertThat(db.bookDao().getSkipped(cet4.bookId, wordIdOf("Africa"))).isNull()
        // ...and the word row itself survives for cumulative stats.
        assertThat(db.wordDao().getById(wordIdOf("Africa"))).isNotNull()

        // --- User data intact ---
        assertThat(cet4.completionCount).isEqualTo(3)
        assertThat(db.bookDao().getSkipped(cet4.bookId, a.wordId)).isTrue()
        val user = db.bookDao().getByName("我的词书")!!
        assertThat(user.isPreset).isFalse()
        assertThat(db.wordDao().getWordsWithSensesByBook(user.bookId).map { it.word.text })
            .containsExactly("myword")
        assertThat(db.wordDao().getByText("myword")).isNotNull()
        // Swipe logs are cumulative history and key on ids; unlinking must not touch them.
        assertThat(db.swipeLogDao().getAll()).hasSize(2)
        // Settings survive: resume position, current book, and the bumped version keys.
        assertThat(settings.resumeWordId(cet4.bookId)).isEqualTo(a.wordId)
        assertThat(settings.currentBookId.first()).isEqualTo(cet4FixtureBookId)
        assertThat(settings.dictionaryVersion.first()).isEqualTo(3L)
        assertThat(settings.presetVersion.first()).isEqualTo(1L)
        Unit
    }

    private fun wordIdOf(text: String): Long = runBlocking {
        db.wordDao().findIdByText(text)!!
    }

    /** A v6-schema database holding v0.2-era rows: old dictionary entries (no US
     *  phonetic), old preset membership and content, user books, skip flags, review
     *  links, swipe logs, completion count. */
    private fun createV6DatabaseWithOldData() {
        val config = androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbFile.absolutePath)
            .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(6) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        CREATE TABLE `word` (
                            `wordId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            `text` TEXT NOT NULL,
                            `phonetic` TEXT
                        )
                        """.trimIndent()
                    )
                    db.execSQL("CREATE UNIQUE INDEX `index_word_text` ON `word` (`text`)")
                    db.execSQL(
                        """
                        CREATE TABLE `sense` (
                            `senseId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            `wordId` INTEGER NOT NULL,
                            `pos` TEXT NOT NULL,
                            `meaning` TEXT NOT NULL,
                            FOREIGN KEY(`wordId`) REFERENCES `word`(`wordId`) ON UPDATE NO ACTION ON DELETE CASCADE
                        )
                        """.trimIndent()
                    )
                    db.execSQL("CREATE UNIQUE INDEX `index_sense_wordId_pos` ON `sense` (`wordId`, `pos`)")
                    db.execSQL(
                        """
                        CREATE TABLE `book` (
                            `bookId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            `name` TEXT NOT NULL,
                            `isPreset` INTEGER NOT NULL,
                            `deleted` INTEGER NOT NULL DEFAULT 0,
                            `completionCount` INTEGER NOT NULL DEFAULT 0
                        )
                        """.trimIndent()
                    )
                    db.execSQL(
                        """
                        CREATE TABLE `book_word` (
                            `bookId` INTEGER NOT NULL,
                            `wordId` INTEGER NOT NULL,
                            `skipped` INTEGER NOT NULL DEFAULT 0,
                            `deleted` INTEGER NOT NULL DEFAULT 0,
                            PRIMARY KEY(`bookId`, `wordId`),
                            FOREIGN KEY(`bookId`) REFERENCES `book`(`bookId`) ON UPDATE NO ACTION ON DELETE CASCADE,
                            FOREIGN KEY(`wordId`) REFERENCES `word`(`wordId`) ON UPDATE NO ACTION ON DELETE CASCADE
                        )
                        """.trimIndent()
                    )
                    db.execSQL("CREATE INDEX `index_book_word_wordId` ON `book_word` (`wordId`)")
                    db.execSQL(
                        """
                        CREATE TABLE `swipe_log` (
                            `logId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            `bookId` INTEGER NOT NULL,
                            `wordId` INTEGER NOT NULL,
                            `timestamp` INTEGER NOT NULL
                        )
                        """.trimIndent()
                    )
                    db.execSQL(
                        """
                        CREATE TABLE `dictionary` (
                            `dictId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            `word` TEXT NOT NULL,
                            `phonetic` TEXT,
                            `pos` TEXT NOT NULL,
                            `meaning` TEXT NOT NULL
                        )
                        """.trimIndent()
                    )
                    db.execSQL(
                        "CREATE UNIQUE INDEX `index_dictionary_word_pos` ON `dictionary` (`word`, `pos`)"
                    )

                    // Old dictionary rows (the ECDICT-era dataset this install shipped with).
                    db.execSQL("INSERT INTO dictionary (word, phonetic, pos, meaning) VALUES ('telephone', '/tel/', 'n.', '旧电话')")
                    db.execSQL("INSERT INTO dictionary (word, phonetic, pos, meaning) VALUES ('staleword', '/x/', 'n.', '旧词')")

                    // Books: CET-4 (id 1), review (id 2), user book (id 3).
                    db.execSQL("INSERT INTO book (name, isPreset, completionCount) VALUES ('CET-4', 1, 3)")
                    db.execSQL("INSERT INTO book (name, isPreset) VALUES ('复习', 1)")
                    db.execSQL("INSERT INTO book (name, isPreset) VALUES ('我的词书', 0)")

                    // Old preset membership: "a" (kept by the new syllabus, old content)
                    // and "Africa" (dropped by the new syllabus).
                    db.execSQL("INSERT INTO word (text, phonetic) VALUES ('a', '/ei/')")
                    db.execSQL("INSERT INTO sense (wordId, pos, meaning) VALUES (1, 'art.', '旧释义')")
                    db.execSQL("INSERT INTO word (text, phonetic) VALUES ('Africa', '/ˈæfrɪkə/')")
                    db.execSQL("INSERT INTO sense (wordId, pos, meaning) VALUES (2, 'n.', '非洲')")
                    db.execSQL("INSERT INTO word (text, phonetic) VALUES ('myword', null)")
                    db.execSQL("INSERT INTO sense (wordId, pos, meaning) VALUES (3, 'n.', '用户词')")

                    // "a" is the user's resume position and is skipped in CET-4, so the
                    // review book links it; Africa is skipped too.
                    db.execSQL("INSERT INTO book_word (bookId, wordId, skipped) VALUES (1, 1, 1)")
                    db.execSQL("INSERT INTO book_word (bookId, wordId, skipped) VALUES (1, 2, 1)")
                    db.execSQL("INSERT INTO book_word (bookId, wordId) VALUES (2, 1)")
                    db.execSQL("INSERT INTO book_word (bookId, wordId) VALUES (2, 2)")
                    db.execSQL("INSERT INTO book_word (bookId, wordId) VALUES (3, 3)")

                    db.execSQL("INSERT INTO swipe_log (bookId, wordId, timestamp) VALUES (1, 1, 100)")
                    db.execSQL("INSERT INTO swipe_log (bookId, wordId, timestamp) VALUES (1, 2, 200)")
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()
        val helper = FrameworkSQLiteOpenHelperFactory().create(config)
        helper.writableDatabase.close()
        helper.close()
    }
}
