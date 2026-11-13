package com.github.chsiching.worddrill.di

import android.content.Context
import androidx.room.Room
import com.github.chsiching.worddrill.data.local.WordDrillDatabase
import com.github.chsiching.worddrill.data.local.dao.BookDao
import com.github.chsiching.worddrill.data.local.dao.DictionaryDao
import com.github.chsiching.worddrill.data.local.dao.SwipeLogDao
import com.github.chsiching.worddrill.data.local.dao.WordDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Exposes the Room database and its DAOs as singletons via Hilt.
 * The Application is already @HiltAndroidApp, so ViewModels can @Inject DAOs directly.
 *
 * The database file name worddrill.db aligns with export/import logic (later ticket).
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): WordDrillDatabase =
        Room.databaseBuilder(
            context,
            WordDrillDatabase::class.java,
            "worddrill.db"
        )
            // Register migrations explicitly: fallbackToDestructiveMigration would lose user
            // books/swipe records, so it is disabled.
            // - v1→v2 (Ticket #14): add phonetic column to word table
            // - v2→v3 (Ticket #19): add dictionary table (read-only ECDICT dictionary)
            // - v3→v4 (Ticket #20): add skipped column to book_word table (skip flag, per-book independent)
            // - v4→v5 (Ticket #22): add deleted column to book_word table (soft-delete flag,
            //   per-book independent, goes to recycle bin)
            .addMigrations(
                WordDrillDatabase.MIGRATION_1_2,
                WordDrillDatabase.MIGRATION_2_3,
                WordDrillDatabase.MIGRATION_3_4,
                WordDrillDatabase.MIGRATION_4_5,
            )
            .build()

    @Provides
    fun provideWordDao(db: WordDrillDatabase): WordDao = db.wordDao()

    @Provides
    fun provideBookDao(db: WordDrillDatabase): BookDao = db.bookDao()

    @Provides
    fun provideSwipeLogDao(db: WordDrillDatabase): SwipeLogDao = db.swipeLogDao()

    @Provides
    fun provideDictionaryDao(db: WordDrillDatabase): DictionaryDao = db.dictionaryDao()
}
