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
class Migration12Test {

    private lateinit var db: SupportSQLiteDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val config = androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(null)
            .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(1) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    // Fixture SQL mimics the Room codegen schema shape
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

        db.query("SELECT phonetic FROM word WHERE text = 'apple'").use { c ->
            assertThat(c.moveToFirst()).isTrue()
            assertThat(c.isNull(0)).isTrue()
        }
    }

    @Test
    fun migrate_canThenWritePhonetic() {
        db.execSQL("INSERT INTO word (text) VALUES ('apple')")
        WordDrillDatabase.MIGRATION_1_2.migrate(db)
        db.execSQL("UPDATE word SET phonetic = '/ˈæpl/' WHERE text = 'apple'")

        db.query("SELECT phonetic FROM word WHERE text = 'apple'").use { c ->
            assertThat(c.moveToFirst()).isTrue()
            assertThat(c.getString(0)).isEqualTo("/ˈæpl/")
        }
    }
}
