package com.github.chsiching.worddrill.di

import android.content.Context
import androidx.room.Room
import com.github.chsiching.worddrill.data.local.WordDrillDatabase
import com.github.chsiching.worddrill.data.local.dao.BookDao
import com.github.chsiching.worddrill.data.local.dao.SwipeLogDao
import com.github.chsiching.worddrill.data.local.dao.WordDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 把 Room 数据库与各 DAO 通过 Hilt 暴露为单例。
 * Application 已是 @HiltAndroidApp，ViewModel 可直接 @Inject 拿到 DAO。
 *
 * 数据库文件名 worddrill.db 与导出/导入逻辑对齐（后续 ticket）。
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
            // Ticket #14：显式注册 v1→v2，避免 fallbackToDestructiveMigration 清掉用户词书/刷卡记录。
            .addMigrations(WordDrillDatabase.MIGRATION_1_2)
            .build()

    @Provides
    fun provideWordDao(db: WordDrillDatabase): WordDao = db.wordDao()

    @Provides
    fun provideBookDao(db: WordDrillDatabase): BookDao = db.bookDao()

    @Provides
    fun provideSwipeLogDao(db: WordDrillDatabase): SwipeLogDao = db.swipeLogDao()
}
