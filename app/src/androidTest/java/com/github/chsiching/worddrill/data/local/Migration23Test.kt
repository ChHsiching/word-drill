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
 * Ticket #19: test for the v2 -> v3 migration (add the dictionary table).
 *
 * Same pattern as [Migration12Test]: room-testing is not used (it needs schema export, and this project has
 * exportSchema=false, so that path is closed); instead, build a v2-shaped SQLite database by hand (manual CREATE TABLE
 * mimicking the Room codegen schema), run [WordDrillDatabase.MIGRATION_2_3.migrate],
 * then verify with PRAGMA / sqlite_master:
 *  - the dictionary table is created with a schema matching Room codegen (including AUTOINCREMENT, NOT NULL, backticks)
 *  - the (word, pos) unique index is created
 *  - old tables (word / sense, etc.) are untouched (existing user data is preserved)
 *
 * This is the minimal coverage of AGENTS.md §3 "surgical changes": test only this one cut, the migration.
 * The full chain (v2 database -> Room reads and writes normally after the upgrade) is covered by the in-memory Room
 * in [DictionaryDaoTest] (it operates on the dictionary table directly on the v3 schema).
 */
@RunWith(AndroidJUnit4::class)
class Migration23Test {

    private lateinit var db: SupportSQLiteDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val config = androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(null) // in-memory database
            .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(2) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    // v2 shape: word table (with phonetic) and sense table; the dictionary table does not exist yet.
                    // Only tables relevant to this test's assertions are created; the rest are skipped.
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
                    db.execSQL(
                        "CREATE UNIQUE INDEX `index_sense_wordId_pos` ON `sense` (`wordId`, `pos`)"
                    )
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
    fun migrate_createsDictionaryTable() {
        WordDrillDatabase.MIGRATION_2_3.migrate(db)

        val tables = mutableListOf<String>()
        db.query("SELECT name FROM sqlite_master WHERE type='table'").use { c ->
            while (c.moveToNext()) tables.add(c.getString(0))
        }
        assertThat(tables).contains("dictionary")
    }

    @Test
    fun migrate_dictionarySchemaMatchesRoomCodegen() {
        // Verifies the migration-generated CREATE TABLE matches the Room codegen shape.
        // Actual codegen shape (captured from sqlite_master on a real device):
        //   CREATE TABLE `dictionary` (`dictId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
        //     `word` TEXT NOT NULL, `phonetic` TEXT, `pos` TEXT NOT NULL, `meaning` TEXT NOT NULL)
        WordDrillDatabase.MIGRATION_2_3.migrate(db)

        db.query("SELECT sql FROM sqlite_master WHERE type='table' AND name='dictionary'").use { c ->
            assertThat(c.moveToFirst()).isTrue()
            val sql = c.getString(0)
            assertThat(sql).contains("PRIMARY KEY AUTOINCREMENT")
            assertThat(sql).contains("`dictId`")
            assertThat(sql).contains("`phonetic` TEXT")
            assertThat(sql).contains("`pos` TEXT NOT NULL")
            assertThat(sql).contains("`meaning` TEXT NOT NULL")
        }
    }

    @Test
    fun migrate_createsUniqueWordPosIndex() {
        WordDrillDatabase.MIGRATION_2_3.migrate(db)

        val indexes = mutableListOf<String>()
        db.query(
            "SELECT sql FROM sqlite_master WHERE type='index' AND tbl_name='dictionary'"
        ).use { c ->
            while (c.moveToNext()) indexes.add(c.getString(0) ?: "")
        }
        // codegen shape: CREATE UNIQUE INDEX `index_dictionary_word_pos` ON `dictionary` (`word`, `pos`)
        assertThat(indexes).hasSize(1)
        assertThat(indexes[0]).contains("UNIQUE INDEX")
        assertThat(indexes[0]).contains("`index_dictionary_word_pos`")
        assertThat(indexes[0]).contains("`word`, `pos`")
    }

    @Test
    fun migrate_preservesExistingWordRows() {
        db.execSQL("INSERT INTO word (text, phonetic) VALUES ('apple', '/ˈæpl/')")
        db.execSQL("INSERT INTO word (text) VALUES ('run')")
        WordDrillDatabase.MIGRATION_2_3.migrate(db)

        val rows = mutableListOf<String>()
        db.query("SELECT text FROM word ORDER BY text").use { c ->
            while (c.moveToNext()) rows.add(c.getString(0))
        }
        assertThat(rows).containsExactly("apple", "run")
    }

    @Test
    fun migrate_dictionaryIsInitiallyEmpty() {
        WordDrillDatabase.MIGRATION_2_3.migrate(db)

        db.query("SELECT COUNT(*) FROM dictionary").use { c ->
            assertThat(c.moveToFirst()).isTrue()
            assertThat(c.getInt(0)).isEqualTo(0)
        }
    }

    @Test
    fun migrate_dictionaryIsWritableAfterUpgrade() {
        // After the upgrade the table must be writable (first-launch import needs INSERT)
        WordDrillDatabase.MIGRATION_2_3.migrate(db)
        db.execSQL(
            "INSERT INTO dictionary (word, phonetic, pos, meaning) VALUES ('apple', '/ˈæpl/', 'n.', '苹果')"
        )

        db.query("SELECT meaning FROM dictionary WHERE word = 'apple'").use { c ->
            assertThat(c.moveToFirst()).isTrue()
            assertThat(c.getString(0)).isEqualTo("苹果")
        }
    }
}
