package com.github.chsiching.worddrill.data

import com.github.chsiching.worddrill.data.local.dao.BookDao
import com.github.chsiching.worddrill.data.local.entity.Book
import com.github.chsiching.worddrill.data.settings.SettingsRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Ticket #20: idempotent creation of the preset "Review" book.
 *
 * Skipped words flow into this book ([com.github.chsiching.worddrill.ui.drill.DrillViewModel.skipWord]
 * sets the current book_word.skipped to 1 + attaches the word to the review book). Like CET-4/CET-6,
 * the review book is a preset book (isPreset=true, cannot be renamed/deleted), but it starts empty
 * and is filled by the user's skip behavior.
 *
 * Idempotency: the DataStore flag `review_book_created` guarantees it is created only once;
 * BookDao.getByName acts as a fallback, so even if the flag is missing (reinstall/cache cleared)
 * two books with the same name will not be created. Same pattern as [PresetImportOrchestrator]:
 * the flag check comes first, and data-layer deduplication is the second guard.
 */
@Singleton
class ReviewBookInitializer @Inject constructor(
    private val bookDao: BookDao,
    private val settings: SettingsRepository,
) {
    /** Name of the "Review" book. Preset books cannot be renamed, so name is stable as an idempotency key. */
    // Consistent with CET-4 / CET-6 / kaoyan coming from assets data: book names are data values,
    // not R.string resources.
    private val reviewBookName = "复习"

    /** Creates an empty preset book if the review book does not exist yet; skips if already created. */
    suspend fun ensureReviewBookCreated() {
        if (settings.reviewBookCreated.first()) return
        if (bookDao.getByName(reviewBookName) == null) {
            bookDao.insert(Book(name = reviewBookName, isPreset = true))
        }
        settings.markReviewBookCreated()
    }
}
