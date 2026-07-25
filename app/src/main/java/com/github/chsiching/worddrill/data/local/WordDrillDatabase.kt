package com.github.chsiching.worddrill.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.github.chsiching.worddrill.data.local.dao.BookDao
import com.github.chsiching.worddrill.data.local.dao.DictionaryDao
import com.github.chsiching.worddrill.data.local.dao.SwipeLogDao
import com.github.chsiching.worddrill.data.local.dao.WordDao
import com.github.chsiching.worddrill.data.local.entity.Book
import com.github.chsiching.worddrill.data.local.entity.BookWord
import com.github.chsiching.worddrill.data.local.entity.DictionaryEntry
import com.github.chsiching.worddrill.data.local.entity.Sense
import com.github.chsiching.worddrill.data.local.entity.SwipeLog
import com.github.chsiching.worddrill.data.local.entity.Word

@Database(
    entities = [
        Word::class,
        Sense::class,
        Book::class,
        BookWord::class,
        SwipeLog::class,
        DictionaryEntry::class,
    ],
    version = 5,
    exportSchema = false,
)
abstract class WordDrillDatabase : RoomDatabase() {
    abstract fun wordDao(): WordDao
    abstract fun bookDao(): BookDao
    abstract fun swipeLogDao(): SwipeLogDao
    abstract fun dictionaryDao(): DictionaryDao

    companion object {
        /**
         * Ticket #14：v1 → v2 给 word 表加 phonetic 列（IPA 音标，可空）。
         * ALTER TABLE ADD COLUMN 在 SQLite 上保留旧数据，新列默认 NULL。
         * Room 自动生成的 fallbackToDestructiveMigration 会丢用户词书，禁用；
         * 这里走显式 migration，老用户升级后既有词条/词书/刷卡记录全部保留。
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE word ADD COLUMN phonetic TEXT")
            }
        }

        /**
         * Ticket #19：v2 → v3 新增 dictionary 表（只读 ECDICT 词典）。
         *
         * 表 schema 与 [DictionaryEntry] 实体对应：(word, pos) 唯一索引兜底重复导入。
         * 老用户升级后 dictionary 为空，首启由 [com.github.chsiching.worddrill.data.DictionaryImportOrchestrator]
         * 从 assets/dictionary.json 导入；不破坏既有 word/sense/book 数据。
         *
         * 注意：CREATE TABLE 语句必须与 Room codegen 生成的 schema 完全一致（含
         * `PRIMARY KEY AUTOINCREMENT NOT NULL` 列约束 + 反引号 quoting），
         * 否则 Room 在启动时做 schema 校验会抛 IllegalStateException。_codegen
         * 形态从实机 sqlite_master 抓取确认过。
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
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
        }

        /**
         * Ticket #20：v3 → v4 给 book_word 表加 skipped 列（跳过标记，词书级独立）。
         *
         * ALTER TABLE ADD COLUMN 在 SQLite 上保留旧关联行，新列默认 0（未跳过）。
         * Room Boolean 映射 INTEGER（0/1），migration 显式写 INTEGER NOT NULL DEFAULT 0
         * 与 Room codegen 的列定义一致（否则 schema 校验会抛 IllegalStateException）。
         *
         * 跳过语义：[BookWord.skipped]。词书级独立 = 每条 (bookId, wordId) 关联单独标记，
         * CET-4 跳过不影响 CET-6 同词的关联。老数据全部 skipped=0（未跳过），行为不变。
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE book_word ADD COLUMN skipped INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        /**
         * Ticket #22：v4 → v5 给 book_word 和 book 表加 deleted 列（软删除标记）。
         *
         * - book_word.deleted：词书级独立（每条 (bookId, wordId) 关联单独标记），
         *   CET-4 删 apple 不影响 CET-6 同词关联。与 [MIGRATION_3_4]（加 skipped）同模式。
         * - book.deleted：整本词书的软删标记。删除自定义词书 = deleted=1（隐藏 + 进回收站），
         *   恢复 = deleted=0。与 [BookWord.deleted] 平行但粒度不同。
         *
         * 与 [MIGRATION_3_4]（加 skipped）完全同模式：ALTER TABLE ADD COLUMN 在 SQLite
         * 上保留旧数据，新列默认 0（未删）。Room Boolean 映射 INTEGER（0/1），
         * migration 显式写 INTEGER NOT NULL DEFAULT 0 与 Room codegen 的列定义一致
         * （否则 schema 校验会抛 IllegalStateException）。
         *
         * 老数据全部 deleted=0（未删），行为不变。软删的词/词书进回收站，可恢复（deleted 改回 0）；
         * 永久删除走真 DELETE（带 deleted=1 兜底）。
         */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE book_word ADD COLUMN deleted INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    "ALTER TABLE book ADD COLUMN deleted INTEGER NOT NULL DEFAULT 0"
                )
            }
        }
    }
}
