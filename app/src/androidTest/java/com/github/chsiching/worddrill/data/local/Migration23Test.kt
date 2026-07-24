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
 * Ticket #19：v2 → v3 migration（新增 dictionary 表）测试。
 *
 * 与 [Migration12Test] 同模式：不引入 room-testing（要 schema 导出，本项目
 * exportSchema=false，路径不通）；直接造一个 v2 形态的 SQLite 库（手工 CREATE TABLE
 * 仿照 Room codegen 生成的 schema），跑 [WordDrillDatabase.MIGRATION_2_3.migrate]，
 * 再用 PRAGMA / sqlite_master 验证：
 *  - dictionary 表已建，schema 与 Room codegen 一致（含 AUTOINCREMENT、NOT NULL、反引号）
 *  - (word, pos) 唯一索引已建
 *  - 老表（word / sense 等）未受影响（既有用户数据保留）
 *
 * 这是 AGENTS.md §3「外科手术式改动」的最小覆盖：只测 migration 这一刀。
 * 全链路（v2 库 → 升级后 Room 正常读写）由 [DictionaryDaoTest] 的内存版 Room 兜住
 * （它在 v3 schema 上直接操作 dictionary 表）。
 */
@RunWith(AndroidJUnit4::class)
class Migration23Test {

    private lateinit var db: SupportSQLiteDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val config = androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(null) // 内存库
            .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(2) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    // v2 形态：word 表（含 phonetic）、sense 表；dictionary 表尚未建。
                    // 只建与本测试断言相关的表，其余略。
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
        // 验证 migration 生成的 CREATE TABLE 与 Room codegen 形态一致。
        // codegen 实际形态（从实机 sqlite_master 抓取）：
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
        // codegen 形态：CREATE UNIQUE INDEX `index_dictionary_word_pos` ON `dictionary` (`word`, `pos`)
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
        // 升级后该表必须可写（首启导入需要 INSERT）
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
