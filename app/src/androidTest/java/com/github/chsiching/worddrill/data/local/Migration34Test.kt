package com.github.chsiching.worddrill.data.local

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Ticket #20: test for the v3 -> v4 migration (add the skipped column to the book_word table).
 *
 * Same pattern as [Migration23Test]: room-testing is not used (it needs schema export, and this project has
 * exportSchema=false, so that path is closed); instead, build a v3-shaped SQLite database by hand (manual CREATE TABLE
 * mimicking the Room codegen schema), run [WordDrillDatabase.MIGRATION_3_4.migrate],
 * then verify with PRAGMA:
 *  - the skipped column is added, typed INTEGER NOT NULL DEFAULT 0
 *  - old link rows are not lost and skipped defaults to 0 (not skipped)
 *  - after the upgrade the column is writable (the UPDATE path of setSkipped)
 *
 * The full chain (v3 database -> Room reads and writes normally after the upgrade) is covered by the in-memory Room
 * in [WordDrillDatabaseTest] (it operates on book_word.skipped directly on the v4 schema).
 */
@RunWith(AndroidJUnit4::class)
class Migration34Test {

    private lateinit var db: SupportSQLiteDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val config = androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(null) // in-memory database
            .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(3) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    // v3 shape: book / word / book_word tables (no skipped column).
                    // Mimics the Room codegen schema shape.
                    db.execSQL(
                        """
                        CREATE TABLE `book` (
                            `bookId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            `name` TEXT NOT NULL,
                            `isPreset` INTEGER NOT NULL
                        )
                        """.trimIndent()
                    )
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
                        CREATE TABLE `book_word` (
                            `bookId` INTEGER NOT NULL,
                            `wordId` INTEGER NOT NULL,
                            PRIMARY KEY(`bookId`, `wordId`),
                            FOREIGN KEY(`bookId`) REFERENCES `book`(`bookId`) ON UPDATE NO ACTION ON DELETE CASCADE,
                            FOREIGN KEY(`wordId`) REFERENCES `word`(`wordId`) ON UPDATE NO ACTION ON DELETE CASCADE
                        )
                        """.trimIndent()
                    )
                    db.execSQL("CREATE INDEX `index_book_word_wordId` ON `book_word` (`wordId`)")
                }

                override fun onUpgrade(
                    db: SupportSQLiteDatabase,
                    oldVersion: Int,
                    newVersion: Int,
                ) {
                    // The test body calls migrate() manually; not implemented here.
                }
            })
            .build()
        val helper = FrameworkSQLiteOpenHelperFactory().create(config)
        db = helper.writableDatabase
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun migrate_addsSkippedColumn() {
        WordDrillDatabase.MIGRATION_3_4.migrate(db)

        val columns = mutableListOf<String>()
        db.query("PRAGMA table_info(book_word)").use { c ->
            while (c.moveToNext()) columns.add(c.getString(1)) // column 1 = name
        }
        assertThat(columns).contains("skipped")
    }

    @Test
    fun migrate_skippedDefaultsZero() {
        db.execSQL("INSERT INTO book (name, isPreset) VALUES ('b', 0)")
        db.execSQL("INSERT INTO word (text) VALUES ('apple')")
        db.execSQL("INSERT INTO book_word (bookId, wordId) VALUES (1, 1)")

        WordDrillDatabase.MIGRATION_3_4.migrate(db)

        // Old link rows should have skipped 0 (ALTER ADD COLUMN DEFAULT 0 backfills old rows)
        db.query("SELECT skipped FROM book_word WHERE bookId = 1 AND wordId = 1").use { c ->
            assertThat(c.moveToFirst()).isTrue()
            assertThat(c.getInt(0)).isEqualTo(0)
        }
    }

    @Test
    fun migrate_preservesExistingLinks() {
        db.execSQL("INSERT INTO book (name, isPreset) VALUES ('b', 0)")
        db.execSQL("INSERT INTO word (text) VALUES ('apple')")
        db.execSQL("INSERT INTO word (text) VALUES ('run')")
        db.execSQL("INSERT INTO book_word (bookId, wordId) VALUES (1, 1)")
        db.execSQL("INSERT INTO book_word (bookId, wordId) VALUES (1, 2)")

        WordDrillDatabase.MIGRATION_3_4.migrate(db)

        db.query("SELECT COUNT(*) FROM book_word").use { c ->
            assertThat(c.moveToFirst()).isTrue()
            assertThat(c.getInt(0)).isEqualTo(2)
        }
    }

    @Test
    fun migrate_skippedIsWritable() {
        // After the upgrade skipped must be writable (the UPDATE path of setSkipped)
        db.execSQL("INSERT INTO book (name, isPreset) VALUES ('b', 0)")
        db.execSQL("INSERT INTO word (text) VALUES ('apple')")
        db.execSQL("INSERT INTO book_word (bookId, wordId) VALUES (1, 1)")
        WordDrillDatabase.MIGRATION_3_4.migrate(db)

        db.execSQL("UPDATE book_word SET skipped = 1 WHERE bookId = 1 AND wordId = 1")
        db.query("SELECT skipped FROM book_word WHERE bookId = 1 AND wordId = 1").use { c ->
            assertThat(c.moveToFirst()).isTrue()
            assertThat(c.getInt(0)).isEqualTo(1)
        }
    }

    /**
     * End-to-end schema validation: create an on-disk v3 database, let Room run the migration and open v4, then do CRUD.
     *
     * The other cases in [Migration34Test] (calling migrate() directly on a SupportSQLiteDatabase) cannot cover this:
     * on `databaseBuilder.openDatabase()` Room compares the codegen-generated v4 schema with the actual on-disk schema,
     * and if the entity's `@ColumnInfo(defaultValue)` disagrees with the migration's `ALTER ... DEFAULT`,
     * this throws IllegalStateException ("Migration didn't properly handle: skipped").
     *
     * An on-disk database is used (not in-memory): for an in-memory database Room skips the version/migration path and just calls onCreate to build v4.
     * The v3 fixture must create all v3 tables (word/sense/book/book_word/swipe_log/dictionary),
     * otherwise Room schema validation fails on the missing tables (unrelated to skipped).
     */
    @Test
    fun roomOpensAfterMigration_schemaValidationPasses() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val dbFile = java.io.File(context.cacheDir, "migration34-room-test.db")
        dbFile.delete()

        // 1) Build a complete v3-shaped database file (all 6 tables mimicking Room codegen)
        val v3Config = androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbFile.absolutePath)
            .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(3) {
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
                            `isPreset` INTEGER NOT NULL
                        )
                        """.trimIndent()
                    )
                    db.execSQL(
                        """
                        CREATE TABLE `book_word` (
                            `bookId` INTEGER NOT NULL,
                            `wordId` INTEGER NOT NULL,
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
                        CREATE TABLE IF NOT EXISTS `dictionary` (
                            `dictId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            `word` TEXT NOT NULL,
                            `phonetic` TEXT,
                            `pos` TEXT NOT NULL,
                            `meaning` TEXT NOT NULL
                        )
                        """.trimIndent()
                    )
                    db.execSQL(
                        "CREATE UNIQUE INDEX IF NOT EXISTS `index_dictionary_word_pos` ON `dictionary` (`word`, `pos`)"
                    )
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()
        val v3Helper = FrameworkSQLiteOpenHelperFactory().create(v3Config)
        val v3Db = v3Helper.writableDatabase
        // Load one row of data to verify it survives the upgrade
        v3Db.execSQL("INSERT INTO book (name, isPreset) VALUES ('b', 0)")
        v3Db.execSQL("INSERT INTO word (text) VALUES ('apple')")
        v3Db.execSQL("INSERT INTO book_word (bookId, wordId) VALUES (1, 1)")
        v3Db.close()
        v3Helper.close()

        // 2) Let Room open this v3 database file with all migrations registered, triggering the v3->v4 upgrade + schema validation.
        //    If the entity defaultValue disagrees with the migration DEFAULT, IllegalStateException is thrown here.
        val roomDb = androidx.room.Room.databaseBuilder(
            context,
            WordDrillDatabase::class.java,
            dbFile.absolutePath,
        )
            .addMigrations(
                WordDrillDatabase.MIGRATION_1_2,
                WordDrillDatabase.MIGRATION_2_3,
                WordDrillDatabase.MIGRATION_3_4,
                WordDrillDatabase.MIGRATION_4_5,
            )
            .build()
        val skipped = kotlinx.coroutines.runBlocking {
            // Opening triggers the migration; CRUD verifies the skipped column is readable and writable
            roomDb.bookDao().getSkipped(1, 1)
        }
        roomDb.close()
        dbFile.delete()

        // After the upgrade old link rows have skipped defaulting to 0 (not skipped)
        assertThat(skipped).isFalse()
    }
}
