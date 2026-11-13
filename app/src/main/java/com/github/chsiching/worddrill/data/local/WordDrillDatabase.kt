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
         * Ticket #14: v1 to v2 adds the phonetic column (IPA, nullable) to the word table.
         * ALTER TABLE ADD COLUMN preserves old data on SQLite; the new column defaults to NULL.
         * Room's auto-generated fallbackToDestructiveMigration would lose user books, so it is disabled;
         * this explicit migration keeps all existing word entries/books/swipe records for upgrading users.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE word ADD COLUMN phonetic TEXT")
            }
        }

        /**
         * Ticket #19: v2 to v3 adds the dictionary table (read-only ECDICT dictionary).
         *
         * The table schema matches the [DictionaryEntry] entity: the (word, pos) unique index
         * guards against duplicate imports.
         * After upgrading, dictionary is empty; on first launch
         * [com.github.chsiching.worddrill.data.DictionaryImportOrchestrator]
         * imports it from assets/dictionary.json without touching existing word/sense/book data.
         *
         * Note: the CREATE TABLE statement must exactly match the schema Room codegen generates
         * (including the `PRIMARY KEY AUTOINCREMENT NOT NULL` column constraint + backtick quoting),
         * otherwise Room's startup schema validation throws IllegalStateException. We captured
         * the codegen form from sqlite_master on a real device to confirm the match.
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
         * Ticket #20: v3 to v4 adds the skipped column (skip flag, independent per book) to book_word.
         *
         * ALTER TABLE ADD COLUMN preserves old association rows on SQLite; the new column defaults
         * to 0 (not skipped). Room maps Boolean to INTEGER (0/1); the migration explicitly writes
         * INTEGER NOT NULL DEFAULT 0 to match Room codegen's column definition (otherwise schema
         * validation throws IllegalStateException).
         *
         * Skip semantics: [BookWord.skipped]. Independent per book means each (bookId, wordId)
         * association is flagged separately; skipping in CET-4 does not affect the CET-6 association
         * for the same word. All existing rows get skipped=0 (not skipped), so behavior is unchanged.
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE book_word ADD COLUMN skipped INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        /**
         * Ticket #22: v4 to v5 adds the deleted column (soft-delete flag) to book_word and book.
         *
         * - book_word.deleted: independent per book (each (bookId, wordId) association is flagged
         *   separately); deleting apple in CET-4 does not affect the CET-6 association for the same
         *   word. Same pattern as [MIGRATION_3_4] (which added skipped).
         * - book.deleted: soft-delete flag for a whole book. Deleting a custom book sets
         *   deleted=1 (hidden and moved to the recycle bin); restore sets it back to 0. Parallel
         *   to [BookWord.deleted] but at a different granularity.
         *
         * Exactly the same pattern as [MIGRATION_3_4] (which added skipped): ALTER TABLE ADD COLUMN
         * preserves old data on SQLite; the new column defaults to 0 (not deleted). Room maps
         * Boolean to INTEGER (0/1); the migration explicitly writes INTEGER NOT NULL DEFAULT 0 to
         * match Room codegen's column definition (otherwise schema validation throws
         * IllegalStateException).
         *
         * All existing rows get deleted=0 (not deleted), so behavior is unchanged. Soft-deleted
         * words/books go to the recycle bin and can be restored (set deleted back to 0);
         * permanent deletion is a real DELETE (guarded by deleted=1).
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
