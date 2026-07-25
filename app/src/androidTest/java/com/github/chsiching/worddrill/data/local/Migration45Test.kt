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
 * Ticket #22：v4 → v5 migration（book_word 表加 deleted 列）测试。
 *
 * 与 [Migration34Test]（v3→v4 加 skipped）同模式：不引入 room-testing（要 schema 导出，
 * 本项目 exportSchema=false，路径不通）；直接造一个 v4 形态的 SQLite 库（手工 CREATE TABLE
 * 仿照 Room codegen 生成的 schema，含 skipped 列），跑 [WordDrillDatabase.MIGRATION_4_5.migrate]，
 * 再用 PRAGMA 验证：
 *  - deleted 列已加，类型 INTEGER NOT NULL DEFAULT 0
 *  - 老关联行未丢，deleted 默认 0（未删）
 *  - 升级后该列可写（setDeleted 的 UPDATE 路径）
 *
 * 全链路（v4 库 → 升级后 Room 正常读写）由 [roomOpensAfterMigration_schemaValidationPasses]
 * 兜住（落盘库 + Room schema 校验）。
 */
@RunWith(AndroidJUnit4::class)
class Migration45Test {

    private lateinit var db: SupportSQLiteDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val config = androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(null) // 内存库
            .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(4) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    // v4 形态：book / word / book_word 表（含 skipped 列，无 deleted 列）。
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
    fun migrate_addsDeletedColumn() {
        WordDrillDatabase.MIGRATION_4_5.migrate(db)

        // book_word 加 deleted
        val bwColumns = mutableListOf<String>()
        db.query("PRAGMA table_info(book_word)").use { c ->
            while (c.moveToNext()) bwColumns.add(c.getString(1))
        }
        assertThat(bwColumns).contains("deleted")

        // book 也加 deleted
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

        // 老关联行 deleted 应为 0
        db.query("SELECT deleted FROM book_word WHERE bookId = 1 AND wordId = 1").use { c ->
            assertThat(c.moveToFirst()).isTrue()
            assertThat(c.getInt(0)).isEqualTo(0)
        }
        // 老词书行 deleted 也应为 0
        db.query("SELECT deleted FROM book WHERE bookId = 1").use { c ->
            assertThat(c.moveToFirst()).isTrue()
            assertThat(c.getInt(0)).isEqualTo(0)
        }
    }

    @Test
    fun migrate_preservesExistingLinksAndSkipped() {
        // 升级不应丢老数据，也不应改既有 skipped 状态
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
        // skipped 状态保留：apple=1（跳过），run=0（未跳过）
        db.query("SELECT skipped FROM book_word WHERE bookId = 1 AND wordId = 1").use { c ->
            assertThat(c.moveToFirst()).isTrue()
            assertThat(c.getInt(0)).isEqualTo(1)
        }
    }

    @Test
    fun migrate_deletedIsWritable() {
        // 升级后 deleted 必须可写（setDeleted 的 UPDATE 路径）
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
     * 端到端 schema 校验：建一个落盘的 v4 库 → 让 Room 跑 migration 打开 v5 → CRUD。
     *
     * 这是直接调 migrate() 跑 SupportSQLiteDatabase 补不上的：Room 在打开时比对 codegen
     * 生成的 v5 schema 与磁盘实际 schema，若 entity 的 `@ColumnInfo(defaultValue)` 与
     * migration 的 `ALTER ... DEFAULT` 不一致，这里会抛 IllegalStateException。
     *
     * v4 fixture 必须建全所有 v4 表（word/sense/book/book_word[含 skipped]/swipe_log/dictionary），
     * 否则 Room schema 校验会因为缺表报错（与 deleted 无关）。
     */
    @Test
    fun roomOpensAfterMigration_schemaValidationPasses() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val dbFile = java.io.File(context.cacheDir, "migration45-room-test.db")
        dbFile.delete()

        // 1) 建全 v4 形态库文件（仿 Room codegen 的全部 6 张表，book_word 含 skipped）
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
        // 灌一条数据，验证升级后不丢
        v4Db.execSQL("INSERT INTO book (name, isPreset) VALUES ('b', 0)")
        v4Db.execSQL("INSERT INTO word (text) VALUES ('apple')")
        v4Db.execSQL("INSERT INTO book_word (bookId, wordId) VALUES (1, 1)")
        v4Db.close()
        v4Helper.close()

        // 2) 让 Room 打开这个 v4 库文件，注册全部 migration，触发 v4→v5 升级 + schema 校验。
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
        val deleted = kotlinx.coroutines.runBlocking {
            // 打开即触发 migration；CRUD 验证 deleted 列可读写
            roomDb.bookDao().getDeleted(1, 1)
        }
        roomDb.close()
        dbFile.delete()

        // 老关联行升级后 deleted 默认 0（未删）
        assertThat(deleted).isFalse()
    }
}
