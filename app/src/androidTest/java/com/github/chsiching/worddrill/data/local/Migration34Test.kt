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

@RunWith(AndroidJUnit4::class)
class Migration34Test {

    private lateinit var db: SupportSQLiteDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val config = androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(null)
            .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(3) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    // Fixture SQL mimics the Room codegen schema shape
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

    @Test
    fun roomOpensAfterMigration_schemaValidationPasses() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val dbFile = java.io.File(context.cacheDir, "migration34-room-test.db")
        dbFile.delete()

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
        v3Db.execSQL("INSERT INTO book (name, isPreset) VALUES ('b', 0)")
        v3Db.execSQL("INSERT INTO word (text) VALUES ('apple')")
        v3Db.execSQL("INSERT INTO book_word (bookId, wordId) VALUES (1, 1)")
        v3Db.close()
        v3Helper.close()

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
            roomDb.bookDao().getSkipped(1, 1)
        }
        roomDb.close()
        dbFile.delete()

        assertThat(skipped).isFalse()
    }
}
