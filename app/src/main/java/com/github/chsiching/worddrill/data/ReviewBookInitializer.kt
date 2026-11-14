package com.github.chsiching.worddrill.data

import com.github.chsiching.worddrill.data.local.dao.BookDao
import com.github.chsiching.worddrill.data.local.dao.REVIEW_BOOK_NAME
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
    suspend fun ensureReviewBookCreated() {
        if (settings.reviewBookCreated.first()) return
        if (bookDao.getByName(REVIEW_BOOK_NAME) == null) {
            bookDao.insert(Book(name = REVIEW_BOOK_NAME, isPreset = true))
        }
        settings.markReviewBookCreated()
    }
}
