package com.github.chsiching.worddrill.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
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
    version = 1,
    exportSchema = false,
)
abstract class WordDrillDatabase : RoomDatabase() {
    abstract fun wordDao(): WordDao
    abstract fun bookDao(): BookDao
    abstract fun swipeLogDao(): SwipeLogDao
}
