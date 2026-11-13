package com.github.chsiching.worddrill.ui.drill

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.github.chsiching.worddrill.data.local.WordDrillDatabase
import com.github.chsiching.worddrill.data.local.WordWithSenses
import com.github.chsiching.worddrill.data.local.entity.Book
import com.github.chsiching.worddrill.data.local.dao.BookDao
import com.github.chsiching.worddrill.data.local.dao.SwipeLogDao
import com.github.chsiching.worddrill.data.local.dao.WordDao
import com.github.chsiching.worddrill.data.local.entity.BookWord
import com.github.chsiching.worddrill.data.local.entity.SwipeLog
import com.github.chsiching.worddrill.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * UI state of the "Drill" tab. Three states: loading / empty book / ready.
 */
sealed interface DrillUiState {
    data object Loading : DrillUiState
    data object Empty : DrillUiState
    data class Ready(
        /** Current book name, shown as plain text at the top (not interactive). */
        val bookName: String,
        /** Card list: one card = all senses of one word. */
        val cards: List<WordWithSenses>,
        /**
         * Whether the current book is the "Review" book (issue #1).
         * When true, the top button shows "Restore" and calls [DrillViewModel.restoreCurrentWord];
         * when false, it shows "Skip" and calls [DrillViewModel.skipCurrentWord].
         */
        val isReviewBook: Boolean = false,
    ) : DrillUiState
}

/**
 * ViewModel of the "Drill" tab.
 *
 * Observes [SettingsRepository.currentBookId] in DataStore: after the book is switched on
 * the "Library" tab, this tab automatically reloads the new book's card list (spec
 * acceptance: the "Drill" tab's card content switches immediately).
 *
 * bookId resolution: null (first launch) or a stale recorded value (book deleted) falls
 * back to the first book (preset CET-4). A book with no cards also shows the Empty state.
 *
 * Counting logic: the UI listens for pager page-settled events and calls [onPageSettled];
 * whether to write swipe_log is decided by the pure function [shouldLogSwipe], keeping it
 * unit-testable.
 *
 * Ticket #20 skip: [skipCurrentWord] sets the current book_word.skipped to 1 and adds the
 * word to the "Review" book (if absent) in one transaction, then reloads the card list
 * (the skipped word disappears because [WordDao.getWordsWithSensesByBook] filters
 * skipped=0).
 */
@HiltViewModel
class DrillViewModel @Inject constructor(
    private val db: WordDrillDatabase,
    private val bookDao: BookDao,
    private val wordDao: WordDao,
    private val swipeLogDao: SwipeLogDao,
    private val settings: SettingsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<DrillUiState>(DrillUiState.Loading)
    val uiState: StateFlow<DrillUiState> = _uiState.asStateFlow()

    /** bookId of the current Ready state; null while loading or empty. Used by [onPageSettled] to write logs. */
    private var currentBookId: Long? = null

    init {
        // Observe currentBookId changes (triggered both by first launch and by switching
        // books on the "Library" tab): distinctUntilChanged avoids reloading on the same
        // bookId (see handoff-chain pitfall A).
        viewModelScope.launch {
            settings.currentBookId
                .distinctUntilChanged()
                .collect { recorded -> loadBookFor(recorded) }
        }
    }

    /**
     * Resolves a bookId into a UI state:
     * - Recorded value valid: load that book directly
     * - Recorded value stale (deleted) / not recorded (null): fall back to the first book
     *   and write it back to DataStore to stay consistent
     */
    private suspend fun loadBookFor(recorded: Long?) {
        val book = recorded?.let { bookDao.getById(it) }
            ?: firstBookOrNull()?.also { settings.setCurrentBookId(it.bookId) }

        if (book == null) {
            _uiState.value = DrillUiState.Empty
            return
        }
        setReady(book.bookId, book.name)
    }

    /** First book (presets first, ascending by name); returns null when no book exists (Empty state). */
    private suspend fun firstBookOrNull(): Book? =
        bookDao.observeAll().first().firstOrNull()

    private suspend fun setReady(bookId: Long, bookName: String) {
        currentBookId = bookId
        val cards = wordDao.getWordsWithSensesByBook(bookId)
        if (cards.isEmpty()) {
            _uiState.value = DrillUiState.Empty
            return
        }
        _uiState.value = DrillUiState.Ready(
            bookName = bookName,
            cards = cards,
            isReviewBook = bookName == REVIEW_BOOK_NAME,
        )
    }

    /**
     * Called by the UI when a pager page settles. A right swipe (page number increasing)
     * writes one swipe_log; a left swipe / hitting the end (page number unchanged) writes
     * nothing. Skips safely when bookId is missing (loading/empty state).
     */
    fun onPageSettled(previousPage: Int, currentPage: Int) {
        if (!shouldLogSwipe(previousPage, currentPage)) return
        val bookId = currentBookId ?: return
        val ready = _uiState.value as? DrillUiState.Ready ?: return
        val wordId = ready.cards.getOrNull(previousPage)?.word?.wordId ?: return
        viewModelScope.launch {
            swipeLogDao.insert(
                SwipeLog(bookId = bookId, wordId = wordId, timestamp = System.currentTimeMillis())
            )
        }
    }

    /**
     * Ticket #20: skip the current word.
     *
     * In one transaction:
     * 1. Set the current book_word's skipped to 1 (book-level: only this book's link row
     *    changes)
     * 2. Add the word to the "Review" book (if absent; linkBookWord IGNORE is the fallback)
     * Then reload the card list: the skipped word disappears because
     * [WordDao.getWordsWithSensesByBook] filters skipped=0, and the UI keeps the pager at
     * the same index (pointing at the next unskipped card).
     *
     * Review book lazy-creation fallback: on first launch
     * [com.github.chsiching.worddrill.data.ReviewBookInitializer] creates the book in
     * parallel; if it is not ready yet when skipping, getByName returns null here and one
     * is created on the spot so the skipped word is never lost.
     *
     * @param currentPage current pager page, used to locate the word to skip.
     * @return true when the skip succeeds; false when not in the Ready state or the page is
     * out of bounds (the UI does not flip the page).
     */
    fun skipCurrentWord(currentPage: Int) {
        val bookId = currentBookId ?: return
        val ready = _uiState.value as? DrillUiState.Ready ?: return
        // The review book does not skip (the UI should not show the skip button; this is a
        // guard against accidental taps). Words in the review book are restored via
        // restoreCurrentWord, not skipped again.
        if (ready.isReviewBook) return
        val wordId = ready.cards.getOrNull(currentPage)?.word?.wordId ?: return
        viewModelScope.launch {
            db.withTransaction {
                // 1) Mark this word as skipped within the current book (book-level; does not affect the same word's links in other books)
                bookDao.setSkipped(bookId, wordId, skipped = true)
                // 2) Add to the review book (lazy-creation fallback; linkBookWord IGNORE handles the already-in-review-book case)
                val reviewBookId = (bookDao.getByName(REVIEW_BOOK_NAME)?.bookId
                    ?: bookDao.insert(Book(name = REVIEW_BOOK_NAME, isPreset = true)))
                bookDao.linkBookWord(BookWord(bookId = reviewBookId, wordId = wordId))
            }
            // Reload cards: the skipped word disappears from the list; an empty list switches to the Empty state
            reloadCards(bookId, ready)
        }
    }

    /**
     * The review book's "restore" action (issue #1).
     *
     * In one transaction:
     * 1. [BookDao.unskipWordEverywhere]: set skipped back to 0 in all original books, so
     *    the word returns to them for drilling
     * 2. [BookDao.unlinkBookWord]: remove the link from the review book (the review book is
     *    a dynamic set of "currently skipped" words)
     * Then reload the review book's cards (the restored word disappears from the review
     * book; the pager stays at the same index pointing at the next card).
     *
     * @param currentPage current pager page, used to locate the word to restore
     */
    fun restoreCurrentWord(currentPage: Int) {
        val bookId = currentBookId ?: return
        val ready = _uiState.value as? DrillUiState.Ready ?: return
        // Only respond to restore in the review book (regular books have no restore entry; guard against accidental taps)
        if (!ready.isReviewBook) return
        val wordId = ready.cards.getOrNull(currentPage)?.word?.wordId ?: return
        viewModelScope.launch {
            db.withTransaction {
                // 1) Restore the word to unskipped in all original books
                bookDao.unskipWordEverywhere(wordId)
                // 2) Remove from the review book (the review book keeps only currently skipped words)
                bookDao.unlinkBookWord(bookId, wordId)
            }
            // Reload the review book's cards: the restored word disappears from the list
            reloadCards(bookId, ready)
        }
    }

    /** Reloads the current book's card list after skip/restore (the skipped=0 filter applies). Keeps the isReviewBook flag. */
    private suspend fun reloadCards(bookId: Long, ready: DrillUiState.Ready) {
        val cards = wordDao.getWordsWithSensesByBook(bookId)
        if (cards.isEmpty()) {
            _uiState.value = DrillUiState.Empty
        } else {
            _uiState.value = ready.copy(cards = cards)
        }
    }

    private companion object {
        /** Name of the "Review" book. Must match [com.github.chsiching.worddrill.data.ReviewBookInitializer]. */
        const val REVIEW_BOOK_NAME = "复习"
    }
}
