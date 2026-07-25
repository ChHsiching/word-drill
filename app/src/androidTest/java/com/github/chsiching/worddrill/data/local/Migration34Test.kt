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
 * Ticket #20：v3 → v4 migration（book_word 表加 skipped 列）测试。
 *
 * 与 [Migration23Test] 同模式：不引入 room-testing（要 schema 导出，本项目
 * exportSchema=false，路径不通）；直接造一个 v3 形态的 SQLite 库（手工 CREATE TABLE
 * 仿照 Room codegen 生成的 schema），跑 [WordDrillDatabase.MIGRATION_3_4.migrate]，
 * 再用 PRAGMA 验证：
 *  - skipped 列已加，类型 INTEGER NOT NULL DEFAULT 0
 *  - 老关联行未丢，skipped 默认 0（未跳过）
 *  - 升级后该列可写（setSkipped 的 UPDATE 路径）
 *
 * 全链路（v3 库 → 升级后 Room 正常读写）由 [WordDrillDatabaseTest] 的内存版 Room
 * 兜住（它在 v4 schema 上直接操作 book_word.skipped）。
 */
@RunWith(AndroidJUnit4::class)
class Migration34Test {

    private lateinit var db: SupportSQLiteDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val config = androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(null) // 内存库
            .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(3) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    // v3 形态：book / word / book_word 表（无 skipped 列）。
                    // 仿 Room codegen 的 schema 形态。
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

        // 老关联行 skipped 应为 0（ALTER ADD COLUMN DEFAULT 0 填回旧行）
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
        // 升级后 skipped 必须可写（setSkipped 的 UPDATE 路径）
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
     * 端到端 schema 校验：建一个落盘的 v3 库 → 让 Room 跑 migration 打开 v4 → CRUD。
     *
     * 这是 [Migration34Test] 其余用例（直接调 migrate() 跑 SupportSQLiteDatabase）补不上的：
     * Room 在 `databaseBuilder.openDatabase()` 时会比对 codegen 生成的 v4 schema 与磁盘实际 schema，
     * 若 entity 的 `@ColumnInfo(defaultValue)` 与 migration 的 `ALTER ... DEFAULT` 不一致，
     * 这里会抛 IllegalStateException（"Migration didn't properly handle: skipped"）。
     *
     * 用落盘库（非内存）：内存库 Room 不会走 version/ migration 路径，直接 onCreate 建 v4。
     * v3 fixture 必须建全所有 v3 表（word/sense/book/book_word/swipe_log/dictionary），
     * 否则 Room schema 校验会因为缺表报错（与 skipped 无关）。
     */
    @Test
    fun roomOpensAfterMigration_schemaValidationPasses() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val dbFile = java.io.File(context.cacheDir, "migration34-room-test.db")
        dbFile.delete()

        // 1) 建全 v3 形态库文件（仿 Room codegen 的全部 6 张表）
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
        // 灌一条数据，验证升级后不丢
        v3Db.execSQL("INSERT INTO book (name, isPreset) VALUES ('b', 0)")
        v3Db.execSQL("INSERT INTO word (text) VALUES ('apple')")
        v3Db.execSQL("INSERT INTO book_word (bookId, wordId) VALUES (1, 1)")
        v3Db.close()
        v3Helper.close()

        // 2) 让 Room 打开这个 v3 库文件，注册全部 migration，触发 v3→v4 升级 + schema 校验。
        //    若 entity defaultValue 与 migration DEFAULT 不一致 → 这里抛 IllegalStateException。
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
            // 打开即触发 migration；CRUD 验证 skipped 列可读写
            roomDb.bookDao().getSkipped(1, 1)
        }
        roomDb.close()
        dbFile.delete()

        // 老关联行升级后 skipped 默认 0（未跳过）
        assertThat(skipped).isFalse()
    }
}
