package com.github.chsiching.worddrill.data

import com.github.chsiching.worddrill.data.local.dao.BookDao
import com.github.chsiching.worddrill.data.local.entity.Book
import com.github.chsiching.worddrill.data.settings.SettingsRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReviewBookInitializer @Inject constructor(
    private val bookDao: BookDao,
    private val settings: SettingsRepository,
) {
    private val reviewBookName = "复习"

    suspend fun ensureReviewBookCreated() {
        if (settings.reviewBookCreated.first()) return
        if (bookDao.getByName(reviewBookName) == null) {
            bookDao.insert(Book(name = reviewBookName, isPreset = true))
        }
        settings.markReviewBookCreated()
    }
}
