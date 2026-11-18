package com.github.chsiching.worddrill.ui.drill

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.github.chsiching.worddrill.data.ProgressResetter
import com.github.chsiching.worddrill.data.local.WordDrillDatabase
import com.github.chsiching.worddrill.data.local.WordWithSenses
import com.github.chsiching.worddrill.data.local.entity.Book
import com.github.chsiching.worddrill.data.local.dao.BookDao
import com.github.chsiching.worddrill.data.local.dao.REVIEW_BOOK_NAME
import com.github.chsiching.worddrill.data.local.dao.SwipeLogDao
import com.github.chsiching.worddrill.data.local.dao.WordDao
import com.github.chsiching.worddrill.data.local.entity.BookWord
import com.github.chsiching.worddrill.data.local.entity.SwipeLog
import com.github.chsiching.worddrill.data.settings.DwellInterval
import com.github.chsiching.worddrill.data.settings.SettingsRepository
import com.github.chsiching.worddrill.ui.WordOrdering
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface DrillUiState {
    data object Loading : DrillUiState

    /** The deck has no cards. The header still renders (book name, 0 / 0 counter,
     *  action label, lock), so the state carries the book identity. */
    data class Empty(
        val bookName: String = "",
        val isReviewBook: Boolean = false,
    ) : DrillUiState
    data class Ready(
        val bookId: Long,
        val bookName: String,
        val cards: List<WordWithSenses>,
        val isReviewBook: Boolean = false,
        val initialPageIndex: Int = 0,
        /** Bumped only when a progress reset rebuilt this deck. The pager keys on it
         *  so the restored deck starts at the first card instead of restoring the
         *  pre-reset page. */
        val deckGeneration: Int = 0,
    ) : DrillUiState
}

@HiltViewModel
class DrillViewModel @Inject constructor(
    private val db: WordDrillDatabase,
    private val bookDao: BookDao,
    private val wordDao: WordDao,
    private val swipeLogDao: SwipeLogDao,
    private val settings: SettingsRepository,
    private val overlayState: AutoAdvanceOverlayState,
    private val progressResetter: ProgressResetter,
) : ViewModel() {

    private val _uiState = MutableStateFlow<DrillUiState>(DrillUiState.Loading)
    val uiState: StateFlow<DrillUiState> = _uiState.asStateFlow()

    /** Auto-advance playback machine (GLOSSARY.md): the countdown lives here, owned by
     *  the ViewModel, so virtual-time JVM tests can drive it; the screen only executes
     *  advance commands, and the hairline is drawn by the navigation root from the
     *  mirrored overlay state. */
    private val autoAdvance = AutoAdvance(viewModelScope) {
        (DwellInterval.normalize(settings.dwellInterval.first()) * 1000).toLong()
    }

    /** Playback phase plus the current card's elapsed fraction for the hairline. */
    val playback: StateFlow<AutoAdvanceState> = autoAdvance.state

    /** Pages the screen should animate to; each flip's settle flows through
     *  [onPageSettled] unchanged, so swipe logging and resume writes are shared. */
    val advance: Flow<Int> = autoAdvance.advance

    private var currentBookId: Long? = null

    /** Monotonic counter handed to Ready.deckGeneration; only progress resets
     *  advance it (skip/restore reloads deliberately keep the pager's page). */
    private var resetGeneration = 0

    init {
        // Mirror playback into the app-wide overlay state so the navigation root
        // can draw the dwell hairline above both nav modes; the drill screen
        // itself no longer renders the line.
        viewModelScope.launch {
            autoAdvance.state.collect { overlayState.update(it) }
        }
        viewModelScope.launch {
            // Reload on either input. Deleting the current book in the Library does
            // not change the recorded id, so observing the id alone would strand the
            // drill tab on the deleted book's last state.
            // distinctUntilChanged per source, never after the combine: DataStore's
            // .data re-emits on every edit of any key, so without it each unrelated
            // write (dwell, and every settle's resume write) would re-fire loadBookFor,
            // whose setReady stops playback and drains pending advance commands —
            // auto-advance would die after its first flip. After the combine a real
            // book-table change must still reload even when the recorded id is
            // unchanged, so only the sources are de-duplicated.
            combine(
                settings.currentBookId.distinctUntilChanged(),
                bookDao.observeAll().distinctUntilChanged(),
            ) { recorded, _ -> recorded }
                .collect { recorded -> loadBookFor(recorded) }
        }
        // A progress reset (#52) runs on the word-list page while this retained
        // ViewModel sits in the nav backstack; without this reload the drill would
        // keep showing the shrunken pre-reset deck at the pre-reset page. Resets of
        // other books need nothing: switching back to them reloads via currentBookId.
        viewModelScope.launch {
            progressResetter.resetBookIds.collect { resetBookId ->
                val bookId = currentBookId
                if (resetBookId == bookId && bookId != null) {
                    val book = bookDao.getById(bookId)?.takeIf { !it.deleted } ?: return@collect
                    setReady(book.bookId, book.name, deckGeneration = ++resetGeneration)
                }
            }
        }
    }

    private suspend fun loadBookFor(recorded: Long?) {
        val book = recorded?.let { bookDao.getById(it) }?.takeIf { !it.deleted }
            ?: defaultBookOrNull()?.also { settings.setCurrentBookId(it.bookId) }

        if (book == null) {
            _uiState.value = DrillUiState.Empty()
            return
        }
        setReady(book.bookId, book.name)
    }

    // The default book is the first preset, non-review book in the library ordering
    // (GLOSSARY.md). Waiting on the flow fixes the fresh-install race (#30): sampling
    // the table once read it before the first-launch import had written any books, and
    // nothing retried. The review book is created by its own initializer before the
    // presets land, so it must not win the default slot; a recorded current book never
    // reaches this fallback.
    private suspend fun defaultBookOrNull(): Book? =
        bookDao.observeAll()
            .map { books -> books.firstOrNull { it.isPreset && it.name != REVIEW_BOOK_NAME } }
            .firstOrNull { it != null }

    private suspend fun setReady(bookId: Long, bookName: String, deckGeneration: Int = 0) {
        // Loading a (possibly different) book always starts playback idle: the deck
        // context the countdown belonged to is gone.
        autoAdvance.stop()
        currentBookId = bookId
        val cards = WordOrdering.alphabetical(wordDao.getWordsWithSensesByBook(bookId))
        if (cards.isEmpty()) {
            _uiState.value = DrillUiState.Empty(
                bookName = bookName,
                isReviewBook = bookName == REVIEW_BOOK_NAME,
            )
            return
        }
        // Resolve the saved word id to a deck index here, never at save time: skipping
        // shifts indices, and a word that left the deck (skipped, deleted, restored
        // away) falls back to the first card (GLOSSARY.md, resume position).
        val savedWordId = settings.resumeWordId(bookId)
        val initialPageIndex =
            cards.indexOfFirst { it.word.wordId == savedWordId }.takeIf { it >= 0 } ?: 0
        _uiState.value = DrillUiState.Ready(
            bookId = bookId,
            bookName = bookName,
            cards = cards,
            isReviewBook = bookName == REVIEW_BOOK_NAME,
            initialPageIndex = initialPageIndex,
            deckGeneration = deckGeneration,
        )
    }

    /** Double-tap on the card area: start auto-advance, pause it, or resume it. */
    fun toggleAutoAdvance(currentPage: Int) {
        val ready = _uiState.value as? DrillUiState.Ready ?: return
        autoAdvance.toggle(currentPage, ready.cards.size)
    }

    /** Stops playback outright (never pauses): the drill screen calls this when its
     *  composition goes away (leaving the drill route — the backstack retains this
     *  ViewModel across tab switches, so disposal is the leave signal) and on app
     *  ON_STOP. Re-entering the drill screen always starts idle. */
    fun stopAutoAdvance() {
        autoAdvance.stop()
    }

    fun onPageSettled(previousPage: Int, currentPage: Int) {
        val bookId = currentBookId ?: return
        val ready = _uiState.value as? DrillUiState.Ready ?: return
        // Every settle repositions playback (manual swipe or auto flip) without
        // pausing; the logging and resume writes below are the untouched settle path.
        autoAdvance.reposition(currentPage, ready.cards.size)
        // The settled card is this book's resume position, saved on every settle; the
        // pager's settledPage flow already de-duplicates emissions.
        val settledWordId = ready.cards.getOrNull(currentPage)?.word?.wordId
        if (settledWordId != null) {
            viewModelScope.launch { settings.setResumeWordId(bookId, settledWordId) }
        }
        if (!shouldLogSwipe(previousPage, currentPage)) return
        val wordId = ready.cards.getOrNull(previousPage)?.word?.wordId ?: return
        viewModelScope.launch {
            swipeLogDao.insert(
                SwipeLog(bookId = bookId, wordId = wordId, timestamp = System.currentTimeMillis())
            )
        }
    }

    fun skipCurrentWord(currentPage: Int) {
        val bookId = currentBookId ?: return
        val ready = _uiState.value as? DrillUiState.Ready ?: return
        if (ready.isReviewBook) return
        val wordId = ready.cards.getOrNull(currentPage)?.word?.wordId ?: return
        viewModelScope.launch {
            db.withTransaction {
                bookDao.setSkipped(bookId, wordId, skipped = true)
                val reviewBookId = (bookDao.getByName(REVIEW_BOOK_NAME)?.bookId
                    ?: bookDao.insert(Book(name = REVIEW_BOOK_NAME, isPreset = true)))
                bookDao.linkBookWord(BookWord(bookId = reviewBookId, wordId = wordId))
            }
            reloadCards(bookId, ready, currentPage)
        }
    }

    fun restoreCurrentWord(currentPage: Int) {
        val bookId = currentBookId ?: return
        val ready = _uiState.value as? DrillUiState.Ready ?: return
        if (!ready.isReviewBook) return
        val wordId = ready.cards.getOrNull(currentPage)?.word?.wordId ?: return
        viewModelScope.launch {
            db.withTransaction {
                bookDao.unskipWordEverywhere(wordId)
                bookDao.unlinkBookWord(bookId, wordId)
            }
            reloadCards(bookId, ready, currentPage)
        }
    }

    private suspend fun reloadCards(bookId: Long, ready: DrillUiState.Ready, currentPage: Int) {
        val cards = WordOrdering.alphabetical(wordDao.getWordsWithSensesByBook(bookId))
        if (cards.isEmpty()) {
            autoAdvance.stop()
            _uiState.value = DrillUiState.Empty(
                bookName = ready.bookName,
                isReviewBook = ready.isReviewBook,
            )
        } else {
            // Skip/restore swapped the word under the pager: playback continues on the
            // new current card with a fresh countdown, never pausing.
            val page = currentPage.coerceAtMost(cards.size - 1)
            autoAdvance.reposition(page, cards.size)
            // The acted-on word left the deck, so the card now at the clamped page is
            // the new current card and the resume point. Saving it here (not via a
            // settle event) is deliberate: a middle-page skip shrinks the deck without
            // moving the pager, so no settle fires.
            _uiState.value = ready.copy(cards = cards, initialPageIndex = page)
            settings.setResumeWordId(bookId, cards[page].word.wordId)
        }
    }
}
