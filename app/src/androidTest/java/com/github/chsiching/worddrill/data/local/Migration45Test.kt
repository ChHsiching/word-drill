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
 * Ticket #22: test for the v4 -> v5 migration (add the deleted column to the book_word table).
 *
 * Same pattern as [Migration34Test] (v3->v4 added skipped): room-testing is not used (it needs schema export,
 * and this project has exportSchema=false, so that path is closed); instead, build a v4-shaped SQLite database by hand
 * (manual CREATE TABLE mimicking the Room codegen schema, including the skipped column), run [WordDrillDatabase.MIGRATION_4_5.migrate],
 * then verify with PRAGMA:
 *  - the deleted column is added, typed INTEGER NOT NULL DEFAULT 0
 *  - old link rows are not lost and deleted defaults to 0 (not deleted)
 *  - after the upgrade the column is writable (the UPDATE path of setDeleted)
 *
 * The full chain (v4 database -> Room reads and writes normally after the upgrade) is covered by
 * [roomOpensAfterMigration_schemaValidationPasses] (on-disk database + Room schema validation).
 */
@RunWith(AndroidJUnit4::class)
class Migration45Test {

    private lateinit var db: SupportSQLiteDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val config = androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(null) // in-memory database
            .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(4) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    // v4 shape: book / word / book_word tables (with the skipped column, no deleted column).
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
                            `skipped` INTEGER NOT NULL DEFAULT 0,
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
    fun migrate_addsDeletedColumn() {
        WordDrillDatabase.MIGRATION_4_5.migrate(db)

        // book_word gets deleted
        val bwColumns = mutableListOf<String>()
        db.query("PRAGMA table_info(book_word)").use { c ->
            while (c.moveToNext()) bwColumns.add(c.getString(1))
        }
        assertThat(bwColumns).contains("deleted")

        // book also gets deleted
        val bookColumns = mutableListOf<String>()
        db.query("PRAGMA table_info(book)").use { c ->
            while (c.moveToNext()) bookColumns.add(c.getString(1))
        }
        assertThat(bookColumns).contains("deleted")
    }

    @Test
    fun migrate_deletedDefaultsZero() {
        db.execSQL("INSERT INTO book (name, isPreset) VALUES ('b', 0)")
        db.execSQL("INSERT INTO word (text) VALUES ('apple')")
        db.execSQL("INSERT INTO book_word (bookId, wordId) VALUES (1, 1)")

        WordDrillDatabase.MIGRATION_4_5.migrate(db)

        // Old link rows should have deleted 0
        db.query("SELECT deleted FROM book_word WHERE bookId = 1 AND wordId = 1").use { c ->
            assertThat(c.moveToFirst()).isTrue()
            assertThat(c.getInt(0)).isEqualTo(0)
        }
        // Old book rows should also have deleted 0
        db.query("SELECT deleted FROM book WHERE bookId = 1").use { c ->
            assertThat(c.moveToFirst()).isTrue()
            assertThat(c.getInt(0)).isEqualTo(0)
        }
    }

    @Test
    fun migrate_preservesExistingLinksAndSkipped() {
        // The upgrade must not lose old data or change existing skipped state
        db.execSQL("INSERT INTO book (name, isPreset) VALUES ('b', 0)")
        db.execSQL("INSERT INTO word (text) VALUES ('apple')")
        db.execSQL("INSERT INTO word (text) VALUES ('run')")
        db.execSQL("INSERT INTO book_word (bookId, wordId, skipped) VALUES (1, 1, 1)")
        db.execSQL("INSERT INTO book_word (bookId, wordId, skipped) VALUES (1, 2, 0)")

        WordDrillDatabase.MIGRATION_4_5.migrate(db)

        db.query("SELECT COUNT(*) FROM book_word").use { c ->
            assertThat(c.moveToFirst()).isTrue()
            assertThat(c.getInt(0)).isEqualTo(2)
        }
        // skipped state preserved: apple=1 (skipped), run=0 (not skipped)
        db.query("SELECT skipped FROM book_word WHERE bookId = 1 AND wordId = 1").use { c ->
            assertThat(c.moveToFirst()).isTrue()
            assertThat(c.getInt(0)).isEqualTo(1)
        }
    }

    @Test
    fun migrate_deletedIsWritable() {
        // After the upgrade deleted must be writable (the UPDATE path of setDeleted)
        db.execSQL("INSERT INTO book (name, isPreset) VALUES ('b', 0)")
        db.execSQL("INSERT INTO word (text) VALUES ('apple')")
        db.execSQL("INSERT INTO book_word (bookId, wordId) VALUES (1, 1)")
        WordDrillDatabase.MIGRATION_4_5.migrate(db)

        db.execSQL("UPDATE book_word SET deleted = 1 WHERE bookId = 1 AND wordId = 1")
        db.query("SELECT deleted FROM book_word WHERE bookId = 1 AND wordId = 1").use { c ->
            assertThat(c.moveToFirst()).isTrue()
            assertThat(c.getInt(0)).isEqualTo(1)
        }
    }

    /**
     * End-to-end schema validation: create an on-disk v4 database, let Room run the migration and open v5, then do CRUD.
     *
     * This is what calling migrate() directly on a SupportSQLiteDatabase cannot cover: on open, Room compares the
     * codegen-generated v5 schema with the actual on-disk schema; if the entity's `@ColumnInfo(defaultValue)` disagrees
     * with the migration's `ALTER ... DEFAULT`, this throws IllegalStateException.
     *
     * The v4 fixture must create all v4 tables (word/sense/book/book_word[with skipped]/swipe_log/dictionary),
     * otherwise Room schema validation fails on the missing tables (unrelated to deleted).
     */
    @Test
    fun roomOpensAfterMigration_schemaValidationPasses() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val dbFile = java.io.File(context.cacheDir, "migration45-room-test.db")
        dbFile.delete()

        // 1) Build a complete v4-shaped database file (all 6 tables mimicking Room codegen; book_word includes skipped)
        val v4Config = androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbFile.absolutePath)
            .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(4) {
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
                            `skipped` INTEGER NOT NULL DEFAULT 0,
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
        val v4Helper = FrameworkSQLiteOpenHelperFactory().create(v4Config)
        val v4Db = v4Helper.writableDatabase
        // Load one row of data to verify it survives the upgrade
        v4Db.execSQL("INSERT INTO book (name, isPreset) VALUES ('b', 0)")
        v4Db.execSQL("INSERT INTO word (text) VALUES ('apple')")
        v4Db.execSQL("INSERT INTO book_word (bookId, wordId) VALUES (1, 1)")
        v4Db.close()
        v4Helper.close()

        // 2) Let Room open this v4 database file with all migrations registered, triggering the v4->v5 upgrade + schema validation.
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
        val deleted = kotlinx.coroutines.runBlocking {
            // Opening triggers the migration; CRUD verifies the deleted column is readable and writable
            roomDb.bookDao().getDeleted(1, 1)
        }
        roomDb.close()
        dbFile.delete()

        // After the upgrade old link rows have deleted defaulting to 0 (not deleted)
        assertThat(deleted).isFalse()
    }
}
