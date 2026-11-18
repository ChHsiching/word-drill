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
            .addMigrations(
                WordDrillDatabase.MIGRATION_1_2,
                WordDrillDatabase.MIGRATION_2_3,
                WordDrillDatabase.MIGRATION_3_4,
                WordDrillDatabase.MIGRATION_4_5,
                WordDrillDatabase.MIGRATION_5_6,
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
