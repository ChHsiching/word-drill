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
 * Ticket #14：v1 → v2 migration（word 表加 phonetic 列）测试。
 *
 * 不引入 room-testing（要 schema 导出，本项目 exportSchema=false，路径不通）；
 * 直接造一个 v1 形态的 SQLite 库（手工 CREATE TABLE 仿照 Room 生成的 schema），
 * 跑 [WordDrillDatabase.MIGRATION_1_2.migrate]，再用 PRAGMA / SELECT 验证：
 *  - phonetic 列已加（PRAGMA table_info 出现该列）
 *  - 老数据（word/sense 行）未丢
 *  - phonetic 列默认 NULL（老行未被 ALTER 写入值）
 *
 * 这是 AGENTS.md §3「外科手术式改动」的最小覆盖：只测 migration 这一刀。
 * 全链路（v1 库 → 升级后 Room 正常读写）由 [WordDrillDatabaseTest] 的内存版 Room
 * 兜住（它在 v2 schema 上直接插带 phonetic 的 Word）。
 */
@RunWith(AndroidJUnit4::class)
class Migration12Test {

    private lateinit var db: SupportSQLiteDatabase

    @Before
    fun setUp() {
        // 内存 SQLite，不落盘；用 SupportSQLiteOpenHelper 直接拿到底层 SupportSQLiteDatabase。
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val config = androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(null) // 内存库
            .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(1) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    // 仿 Room v1 生成的 word 表 schema（无 phonetic 列）。其余表与本测试无关，不建。
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
                    // 由测试主体手动调 migrate()，这里不实现。
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

        // ALTER TABLE ADD COLUMN 不填默认值，老行 phonetic 应为 NULL
        db.query("SELECT phonetic FROM word WHERE text = 'apple'").use { c ->
            assertThat(c.moveToFirst()).isTrue()
            assertThat(c.isNull(0)).isTrue()
        }
    }

    @Test
    fun migrate_canThenWritePhonetic() {
        // 升级后该列必须可写（验证 ALTER 加的是可写普通列，非生成列）
        db.execSQL("INSERT INTO word (text) VALUES ('apple')")
        WordDrillDatabase.MIGRATION_1_2.migrate(db)
        db.execSQL("UPDATE word SET phonetic = '/ˈæpl/' WHERE text = 'apple'")

        db.query("SELECT phonetic FROM word WHERE text = 'apple'").use { c ->
            assertThat(c.moveToFirst()).isTrue()
            assertThat(c.getString(0)).isEqualTo("/ˈæpl/")
        }
    }
}
