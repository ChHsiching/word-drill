package com.github.chsiching.worddrill.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.github.chsiching.worddrill.data.local.dao.BookDao
import com.github.chsiching.worddrill.data.local.dao.SwipeLogDao
import com.github.chsiching.worddrill.data.local.dao.WordDao
import com.github.chsiching.worddrill.data.local.entity.Book
import com.github.chsiching.worddrill.data.local.entity.BookWord
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
    ],
    version = 2,
    exportSchema = false,
)
abstract class WordDrillDatabase : RoomDatabase() {
    abstract fun wordDao(): WordDao
    abstract fun bookDao(): BookDao
    abstract fun swipeLogDao(): SwipeLogDao

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
    }
}
