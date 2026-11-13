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
 * Ticket #14: test for the v1-to-v2 migration (add the phonetic column to the word table).
 *
 * room-testing is not used (it needs schema export, and this project has exportSchema=false, so that path is closed);
 * instead, build a v1-shaped SQLite database by hand (manual CREATE TABLE mimicking the Room-generated schema),
 * run [WordDrillDatabase.MIGRATION_1_2.migrate], then verify with PRAGMA / SELECT:
 *  - the phonetic column is added (it shows up in PRAGMA table_info)
 *  - old data (word/sense rows) is not lost
 *  - the phonetic column defaults to NULL (ALTER wrote no value into old rows)
 *
 * This is the minimal coverage of AGENTS.md §3 "surgical changes": test only this one cut, the migration.
 * [WordDrillDatabaseTest] covers the full chain (Room reads and writes normally on the upgraded database);
 * it directly inserts a Word with phonetic on the v2 schema.
 */
@RunWith(AndroidJUnit4::class)
class Migration12Test {

    private lateinit var db: SupportSQLiteDatabase

    @Before
    fun setUp() {
        // In-memory SQLite, nothing on disk; use SupportSQLiteOpenHelper to get the underlying SupportSQLiteDatabase directly.
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val config = androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(null) // in-memory database
            .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(1) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    // Mimics the Room v1-generated word table schema (no phonetic column). The other tables are irrelevant to this test and are not created.
                    db.execSQL(
                        """
                        CREATE TABLE word (
                            wordId INTEGER NOT NULL,
                            text TEXT NOT NULL,
                            PRIMARY KEY(wordId AUTOINCREMENT)
                        )
                        """.trimIndent()
                    )
                    db.execSQL("CREATE UNIQUE INDEX index_word_text ON word(text)")
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
    fun migrate_addsPhoneticColumn() {
        db.execSQL("INSERT INTO word (text) VALUES ('apple')")
        WordDrillDatabase.MIGRATION_1_2.migrate(db)

        val columns = mutableListOf<String>()
        db.query("PRAGMA table_info(word)").use { c ->
            while (c.moveToNext()) columns.add(c.getString(1)) // column 1 = name
        }
        assertThat(columns).contains("phonetic")
    }

    @Test
    fun migrate_preservesExistingRows() {
        db.execSQL("INSERT INTO word (text) VALUES ('apple')")
        db.execSQL("INSERT INTO word (text) VALUES ('run')")
        WordDrillDatabase.MIGRATION_1_2.migrate(db)

        val texts = mutableListOf<String>()
        db.query("SELECT text FROM word ORDER BY text").use { c ->
            while (c.moveToNext()) texts.add(c.getString(0))
        }
        assertThat(texts).containsExactly("apple", "run")
    }

    @Test
    fun migrate_newColumnDefaultsNull() {
        db.execSQL("INSERT INTO word (text) VALUES ('apple')")
        WordDrillDatabase.MIGRATION_1_2.migrate(db)

        // ALTER TABLE ADD COLUMN sets no default value; the phonetic of old rows should be NULL
        db.query("SELECT phonetic FROM word WHERE text = 'apple'").use { c ->
            assertThat(c.moveToFirst()).isTrue()
            assertThat(c.isNull(0)).isTrue()
        }
    }

    @Test
    fun migrate_canThenWritePhonetic() {
        // After the upgrade the column must be writable (verifies ALTER added a normal writable column, not a generated one)
        db.execSQL("INSERT INTO word (text) VALUES ('apple')")
        WordDrillDatabase.MIGRATION_1_2.migrate(db)
        db.execSQL("UPDATE word SET phonetic = '/ˈæpl/' WHERE text = 'apple'")

        db.query("SELECT phonetic FROM word WHERE text = 'apple'").use { c ->
            assertThat(c.moveToFirst()).isTrue()
            assertThat(c.getString(0)).isEqualTo("/ˈæpl/")
        }
    }
}
