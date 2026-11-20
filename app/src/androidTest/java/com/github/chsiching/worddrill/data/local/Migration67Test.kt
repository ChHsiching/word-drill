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
class Migration67Test {

    private lateinit var db: SupportSQLiteDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val config = androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(null)
            .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(6) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    // Fixture SQL mimics the Room codegen schema shape at v6.
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
    fun migrate_addsPhoneticUsColumn() {
        WordDrillDatabase.MIGRATION_6_7.migrate(db)

        val dictionaryColumns = mutableListOf<String>()
        db.query("PRAGMA table_info(dictionary)").use { c ->
            while (c.moveToNext()) dictionaryColumns.add(c.getString(1))
        }
        assertThat(dictionaryColumns).contains("phoneticUs")
    }

    @Test
    fun migrate_preservesExistingRowsWithNullPhoneticUs() {
        db.execSQL(
            "INSERT INTO dictionary (word, phonetic, pos, meaning) VALUES ('apple', '/ˈæpl/', 'n.', '苹果')"
        )

        WordDrillDatabase.MIGRATION_6_7.migrate(db)

        db.query("SELECT word, phonetic, phoneticUs, pos, meaning FROM dictionary WHERE dictId = 1").use { c ->
            assertThat(c.moveToFirst()).isTrue()
            assertThat(c.getString(0)).isEqualTo("apple")
            assertThat(c.getString(1)).isEqualTo("/ˈæpl/")
            assertThat(c.isNull(2)).isTrue()
            assertThat(c.getString(3)).isEqualTo("n.")
            assertThat(c.getString(4)).isEqualTo("苹果")
        }
    }

    @Test
    fun roomOpensAfterMigration_schemaValidationPasses() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val dbFile = java.io.File(context.cacheDir, "migration67-room-test.db")
        dbFile.delete()

        val v6Config = androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
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
        val v6Helper = FrameworkSQLiteOpenHelperFactory().create(v6Config)
        val v6Db = v6Helper.writableDatabase
        v6Db.execSQL(
            "INSERT INTO dictionary (word, phonetic, pos, meaning) VALUES ('apple', '/ˈæpl/', 'n.', '苹果')"
        )
        v6Db.close()
        v6Helper.close()

        // Opening with Room triggers the v6-to-v7 migration and schema validation; a
        // column-definition mismatch throws IllegalStateException here.
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
                WordDrillDatabase.MIGRATION_5_6,
                WordDrillDatabase.MIGRATION_6_7,
            )
            .build()
        val entries = kotlinx.coroutines.runBlocking { roomDb.dictionaryDao().findByWord("apple") }
        roomDb.close()
        dbFile.delete()

        assertThat(entries).hasSize(1)
        assertThat(entries.single().phoneticUs).isNull()
    }
}
