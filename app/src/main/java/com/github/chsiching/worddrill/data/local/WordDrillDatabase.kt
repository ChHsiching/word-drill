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
    version = 7,
    exportSchema = false,
)
abstract class WordDrillDatabase : RoomDatabase() {
    abstract fun wordDao(): WordDao
    abstract fun bookDao(): BookDao
    abstract fun swipeLogDao(): SwipeLogDao
    abstract fun dictionaryDao(): DictionaryDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE word ADD COLUMN phonetic TEXT")
            }
        }

        // Handwritten CREATE TABLE must exactly match the schema Room's codegen expects
        // (column constraints + backtick quoting), or Room's startup schema validation
        // throws IllegalStateException.
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

        // INTEGER NOT NULL DEFAULT 0 must match Room codegen's column definition for a
        // non-null Boolean, or schema validation throws IllegalStateException.
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE book_word ADD COLUMN skipped INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        // Same Room schema-match constraint as MIGRATION_3_4: INTEGER NOT NULL DEFAULT 0
        // must match codegen for a non-null Boolean.
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

        // Same Room schema-match constraint: INTEGER NOT NULL DEFAULT 0 must match
        // codegen for a non-null Int.
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE book ADD COLUMN completionCount INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        // Nullable column, so a plain ALTER TABLE suffices; existing rows keep NULL.
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE dictionary ADD COLUMN phoneticUs TEXT")
            }
        }
    }
}
