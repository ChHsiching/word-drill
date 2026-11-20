package com.github.chsiching.worddrill.ui.drill

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.github.chsiching.worddrill.data.ProgressResetter
import com.github.chsiching.worddrill.data.local.WordDrillDatabase
import com.github.chsiching.worddrill.data.local.WordPhonetics
import com.github.chsiching.worddrill.data.local.WordWithSenses
import com.github.chsiching.worddrill.data.local.entity.Book
import com.github.chsiching.worddrill.data.local.dao.BookDao
import com.github.chsiching.worddrill.data.local.dao.REVIEW_BOOK_NAME
import com.github.chsiching.worddrill.data.local.dao.SwipeLogDao
import com.github.chsiching.worddrill.data.local.dao.WordDao
import com.github.chsiching.worddrill.data.local.entity.BookWord
import com.github.chsiching.worddrill.data.local.entity.SwipeLog
import com.github.chsiching.worddrill.data.settings.DwellInterval
import com.github.chsiching.worddrill.data.settings.DeckOrdering
import com.github.chsiching.worddrill.data.settings.SettingsRepository
import com.github.chsiching.worddrill.ui.DeckSequenceState
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
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
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
        /** Phonetic variants per card word (issue #58), resolved from the dictionary
         *  at book open; the card picks UK/US/both per the preference at render
         *  time. Skip/restore only shrink the deck, so the map outlives reloads. */
        val phonetics: Map<Long, WordPhonetics> = emptyMap(),
        /** Deck snapshot identity. The pager keys on it (with the book id) and
         *  restores its remembered page when the key is unchanged, so it is bumped
         *  whenever the deck must not inherit an earlier deck's page: a progress
         *  reset, switching to a different book, an out-of-band deck edit. */
        val deckGeneration: Int = 0,
        /** Completed passes of this book (GLOSSARY.md, completion count), bumped by one
         *  each time the completion card appears. Progress resets never clear it. */
        val completionCount: Int = 0,
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
    private val deckSequence: DeckSequenceState,
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

    /** Monotonic counter handed to Ready.deckGeneration. Advanced whenever the deck
     *  must not inherit a pager page remembered from an earlier deck: a progress
     *  reset, switching to a different book, an out-of-band deck edit. Skip/restore
     *  reloads deliberately keep the pager's page and the current generation. */
    private var resetGeneration = 0

    /** deckGeneration the CURRENT book's pager was built with. A book-table reload
     *  of the same book — a skip inserting the review book, a completion-count
     *  increment — must reuse it, or the pager key (bookId, deckGeneration) would
     *  change and rebuild the pager mid-session, throwing away the user's page. */
    private var currentDeckGeneration = 0

    /** Ordering the CURRENT deck was dealt in. Skip/restore re-publishes the
     *  shrunken sequence in this same ordering (issue #60: the word list's letter
     *  bar follows the dealt mode until the next book open). */
    private var currentDeckOrdering = DeckOrdering.ALPHABETICAL

    /** Deck size (bookId to unskipped, undeleted link count) the loaded state was
     *  built with. The out-of-band observer below reloads only when the observed
     *  size of the SAME book drifts from this; the skip/restore paths set it
     *  BEFORE their writes so their own, page-preserving reloads never trip the
     *  observer, and a size recorded for another book never matches (the loader
     *  owns book switches). */
    private var expectedDeckSize: Pair<Long, Int>? = null

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
        // other books need nothing here: the generation bump in loadBookFor makes
        // switching back to them rebuild the pager at the fresh position.
        viewModelScope.launch {
            progressResetter.resetBookIds.collect { resetBookId ->
                val bookId = currentBookId
                if (resetBookId == bookId && bookId != null) {
                    val book = bookDao.getById(bookId)?.takeIf { !it.deleted } ?: return@collect
                    setReady(book, deckGeneration = ++resetGeneration)
                }
            }
        }
        // Out-of-band deck edits (word added or deleted on the word-list page) change
        // the current book's link set without touching the book table, so nothing
        // above fires and the retained ViewModel would keep a stale deck — or a
        // stale Empty — until relaunch (e2e defect). Observe the current book's deck
        // size and reload whenever it drifts from the loaded deck's size.
        viewModelScope.launch {
            observeOutOfBandDeckChanges()
        }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private suspend fun observeOutOfBandDeckChanges() {
        settings.currentBookId
            .flatMapLatest { id ->
                if (id == null) flowOf(null)
                else bookDao.observeWordCountInBook(id)
                    .distinctUntilChanged()
                    .map { size -> id to size }
            }
            // DataStore's .data re-emits on every unrelated key edit; the collector
            // writes (setReady), so de-duplicate before it.
            .distinctUntilChanged()
            .collect { observed ->
                // Only act when the drift is against a size recorded for the SAME
                // book: during a book switch no expectation exists for the incoming
                // book yet and the loader owns the reload.
                val (id, observedSize) = observed ?: return@collect
                val expected = expectedDeckSize
                if (id != currentBookId || expected == null || expected.first != id) return@collect
                if (expected.second == observedSize) return@collect
                val book = bookDao.getById(id)?.takeIf { !it.deleted } ?: return@collect
                setReady(book, deckGeneration = ++resetGeneration)
            }
    }

    private suspend fun loadBookFor(recorded: Long?) {
        val book = recorded?.let { bookDao.getById(it) }?.takeIf { !it.deleted }
            ?: defaultBookOrNull()?.also { settings.setCurrentBookId(it.bookId) }

        if (book == null) {
            _uiState.value = DrillUiState.Empty()
            return
        }
        // A different book always gets a FRESH generation. The pager keys on
        // (bookId, deckGeneration) and restores its remembered page on tab
        // switches; returning to a book with the generation of an earlier visit
        // would restore a pager saved before an out-of-band change (e.g. a reset
        // done while another book was current), landing on the stale page.
        setReady(book, deckGeneration = if (book.bookId == currentBookId) currentDeckGeneration else ++resetGeneration)
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

    private suspend fun setReady(book: Book, deckGeneration: Int = 0) {
        // Loading a (possibly different) book always starts playback idle: the deck
        // context the countdown belonged to is gone.
        autoAdvance.stop()
        currentBookId = book.bookId
        currentDeckGeneration = deckGeneration
        // Deck ordering (issue #54) is sampled once per book open, not observed:
        // changing the setting must leave the running session alone and take
        // effect on the next open. SHUFFLED draws a fresh Random here, so every
        // open re-deals and no pattern can be memorized.
        val words = wordDao.getWordsWithSensesByBook(book.bookId)
        currentDeckOrdering = settings.deckOrdering.first()
        val cards = when (currentDeckOrdering) {
            DeckOrdering.ALPHABETICAL -> WordOrdering.alphabetical(words)
            DeckOrdering.SHUFFLED -> WordOrdering.shuffled(words)
        }
        // The dealt sequence is the one ordering source the word list consumes.
        deckSequence.publish(book.bookId, cards.map { it.word.wordId }, currentDeckOrdering)
        expectedDeckSize = book.bookId to cards.size
        if (cards.isEmpty()) {
            _uiState.value = DrillUiState.Empty(
                bookName = book.name,
                isReviewBook = book.name == REVIEW_BOOK_NAME,
            )
            return
        }
        // Resolve the saved word id to a deck index here, never at save time: skipping
        // and reshuffles shift indices, and a word that left the deck (skipped,
        // deleted, restored away) falls back to the first card (GLOSSARY.md, resume
        // position).
        val savedWordId = settings.resumeWordId(book.bookId)
        val initialPageIndex = WordOrdering.resumeIndex(savedWordId, cards)
        _uiState.value = DrillUiState.Ready(
            bookId = book.bookId,
            bookName = book.name,
            cards = cards,
            isReviewBook = book.name == REVIEW_BOOK_NAME,
            initialPageIndex = initialPageIndex,
            deckGeneration = deckGeneration,
            completionCount = book.completionCount,
            phonetics = wordDao.getPhoneticsByBook(book.bookId).associateBy { it.wordId },
        )
    }

    /** Double-tap on the card area: start auto-advance, pause it, or resume it.
     *  Inert on the completion card: playback never starts there. */
    fun toggleAutoAdvance(currentPage: Int) {
        val ready = _uiState.value as? DrillUiState.Ready ?: return
        if (currentPage >= ready.cards.size) return
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
        // The pager's settledPage flow already de-duplicates, but this is the write
        // path: a settle that did not move pages must not double-write (resume word,
        // swipe log, completion count).
        if (currentPage == previousPage) return
        val bookId = currentBookId ?: return
        val ready = _uiState.value as? DrillUiState.Ready ?: return
        // Settling on the virtual completion page (index == deck size) records one
        // finished pass. Playback stops — the deck's end is reached — and no resume
        // word exists under this page, so the card under the last real card stays the
        // resume position.
        if (currentPage == ready.cards.size) {
            autoAdvance.stop()
            _uiState.value = ready.copy(completionCount = ready.completionCount + 1)
            viewModelScope.launch { bookDao.incrementCompletionCount(bookId) }
            return
        }
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

    /** Restart on the completion card (R.string.drill_completion_restart): the
     *  shared reset engine (GLOSSARY.md, progress reset) returns the book to its
     *  first card with every skipped word back in the deck. The reset listener
     *  above reloads the deck (deckGeneration bump), landing on the first card;
     *  the completion count is history and survives. */
    fun restartBook() {
        val bookId = currentBookId ?: return
        viewModelScope.launch { progressResetter.reset(bookId) }
    }

    fun skipCurrentWord(currentPage: Int) {
        val bookId = currentBookId ?: return
        val ready = _uiState.value as? DrillUiState.Ready ?: return
        if (ready.isReviewBook) return
        val wordId = ready.cards.getOrNull(currentPage)?.word?.wordId ?: return
        // Publish the post-skip deck size before the write: the out-of-band observer
        // must stay quiet about this deck's own edit.
        expectedDeckSize = bookId to (ready.cards.size - 1)
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
        // See skipCurrentWord: keep the out-of-band observer quiet for this edit.
        expectedDeckSize = bookId to (ready.cards.size - 1)
        viewModelScope.launch {
            db.withTransaction {
                bookDao.unskipWordEverywhere(wordId)
                bookDao.unlinkBookWord(bookId, wordId)
            }
            reloadCards(bookId, ready, currentPage)
        }
    }

    private suspend fun reloadCards(bookId: Long, ready: DrillUiState.Ready, currentPage: Int) {
        // Skip/restore is not a book open: the remaining cards keep the sequence
        // the deck was dealt in (a mid-session reshuffle would jump every card),
        // so the fresh query is aligned onto the previous deck, which only ever
        // loses the acted-on word here.
        val freshById = wordDao.getWordsWithSensesByBook(bookId)
            .associateBy { it.word.wordId }
        val cards = ready.cards.mapNotNull { freshById[it.word.wordId] }
        deckSequence.publish(bookId, cards.map { it.word.wordId }, currentDeckOrdering)
        expectedDeckSize = bookId to cards.size
        if (cards.isEmpty()) {
            autoAdvance.stop()
            _uiState.value = DrillUiState.Empty(
                bookName = ready.bookName,
                isReviewBook = ready.isReviewBook,
            )
        } else {
            val page = currentPage.coerceAtMost(cards.size - 1)
            if (currentPage == cards.size) {
                // Acting on the deck's LAST card finishes the pass: pageCount shrank
                // under the pager's unchanged page, so the completion card now shows
                // there without a settle event ever firing (e2e defect). Record the
                // finished pass here, exactly as the settle path would.
                autoAdvance.stop()
                _uiState.value = ready.copy(
                    cards = cards,
                    initialPageIndex = page,
                    completionCount = ready.completionCount + 1,
                )
                bookDao.incrementCompletionCount(bookId)
            } else {
                // Skip/restore swapped the word under the pager: playback continues on
                // the new current card with a fresh countdown, never pausing.
                autoAdvance.reposition(page, cards.size)
                _uiState.value = ready.copy(cards = cards, initialPageIndex = page)
            }
            // The acted-on word left the deck, so the card now at the clamped page is
            // the new current card and the resume point. Saving it here (not via a
            // settle event) is deliberate: a middle-page skip shrinks the deck without
            // moving the pager, so no settle fires.
            settings.setResumeWordId(bookId, cards[page].word.wordId)
        }
    }
}
