package com.github.chsiching.worddrill.data

import androidx.room.withTransaction
import com.github.chsiching.worddrill.data.local.WordDrillDatabase
import com.github.chsiching.worddrill.data.local.dao.BookDao
import com.github.chsiching.worddrill.data.local.dao.REVIEW_BOOK_NAME
import com.github.chsiching.worddrill.data.settings.SettingsRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Progress reset (GLOSSARY.md): returns a book to its starting state — resume
 *  position back to the first card, every skipped word of the book back in the
 *  deck, those words' links removed from the review book (REVIEW_BOOK_NAME).
 *  Swipe logs and cumulative stats (today / all-time) are history and are never
 *  touched. The completion card's restart action reuses this engine. */
@Singleton
class ProgressResetter @Inject constructor(
    private val db: WordDrillDatabase,
    private val bookDao: BookDao,
    private val settings: SettingsRepository,
) {
    private val _resetBookIds = MutableSharedFlow<Long>(extraBufferCapacity = 1)

    /** Emits each book id right after its reset completed. The drill listens so a
     *  retained ViewModel reloads the restored deck instead of showing the stale
     *  shrunken one (the reset usually happens on the word-list page). */
    val resetBookIds: SharedFlow<Long> = _resetBookIds.asSharedFlow()

    suspend fun reset(bookId: Long) {
        // Clear the resume position BEFORE the un-skip transaction commits: the
        // drill observes the book's link count, so a reload triggered by that
        // commit must not read the pre-reset position in between.
        settings.clearResumeWordId(bookId)
        db.withTransaction {
            bookDao.unskipAllInBook(bookId)
            // Un-skip first: the removal keeps links for words still skipped in
            // another book, so it must see this book's flags already cleared.
            bookDao.getByName(REVIEW_BOOK_NAME)?.let { review ->
                bookDao.removeReviewLinksOfBookWords(review.bookId, bookId)
            }
        }
        _resetBookIds.emit(bookId)
    }
}
