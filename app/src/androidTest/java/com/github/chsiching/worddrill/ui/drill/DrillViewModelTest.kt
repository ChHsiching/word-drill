package com.github.chsiching.worddrill.ui.drill

import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.github.chsiching.worddrill.data.ProgressResetter
import com.github.chsiching.worddrill.data.local.WordDrillDatabase
import com.github.chsiching.worddrill.data.local.dao.REVIEW_BOOK_NAME
import com.github.chsiching.worddrill.data.local.entity.Book
import com.github.chsiching.worddrill.data.local.entity.BookWord
import com.github.chsiching.worddrill.data.local.entity.Sense
import com.github.chsiching.worddrill.data.local.entity.Word
import com.github.chsiching.worddrill.data.settings.DeckOrdering
import com.github.chsiching.worddrill.data.settings.DwellInterval
import com.github.chsiching.worddrill.data.settings.SettingsRepository
import com.github.chsiching.worddrill.ui.DealtDeck
import com.github.chsiching.worddrill.ui.DeckSequenceState
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DrillViewModelTest {

    private val context: android.content.Context
        get() = ApplicationProvider.getApplicationContext()

    private lateinit var db: WordDrillDatabase
    private lateinit var settings: SettingsRepository
    private lateinit var resetter: ProgressResetter
    private val deckSequence = DeckSequenceState()
    private val viewModels = mutableListOf<DrillViewModel>()

    @Before
    fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(context, WordDrillDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        settings = SettingsRepository(context)
        resetter = ProgressResetter(db, db.bookDao(), settings)
        Unit
    }

    @After
    fun tearDown() = runBlocking {
        // Each ViewModel's init collector stays subscribed to the shared DataStore for
        // as long as its scope lives; without cancelling it, a later DataStore write in
        // the same process would resume the collector against the closed database.
        // The pause lets in-flight Room flow queries (the loader combines observeAll)
        // drain before the pool closes, or close() races them and throws
        // "connection pool has been closed" into whatever test runs next.
        viewModels.forEach { it.viewModelScope.cancel() }
        delay(200)
        db.close()
        // Deck ordering is process-global; every other test in this process assumes
        // the 按字母 default, so restore it even when a shuffle test failed midway.
        settings.setDeckOrdering(DeckOrdering.ALPHABETICAL)
        Unit
    }

    private fun newViewModel(): DrillViewModel =
        DrillViewModel(
            db = db,
            bookDao = db.bookDao(),
            wordDao = db.wordDao(),
            swipeLogDao = db.swipeLogDao(),
            settings = settings,
            overlayState = AutoAdvanceOverlayState(),
            progressResetter = resetter,
            deckSequence = deckSequence,
        ).also { viewModels.add(it) }

    private suspend fun insertBookWithWord(name: String, isPreset: Boolean, wordText: String): Long {
        val bookId = db.bookDao().insert(Book(name = name, isPreset = isPreset))
        val wordId = db.wordDao().insert(Word(text = wordText))
        db.wordDao().insertSense(Sense(wordId = wordId, pos = "n.", meaning = "测试释义"))
        db.bookDao().linkBookWord(BookWord(bookId = bookId, wordId = wordId))
        return bookId
    }

    /** Seeds a book under an explicit id. Resume keys are derived from the book id and
     *  this process's DataStore survives across tests while in-memory ids restart at 1,
     *  so each test owns a unique id range to stay order-independent. Returns the deck's
     *  word ids in deck order (alphabetical). Word texts are unique in the schema and
     *  shared across books, like the real importers do. */
    private suspend fun insertBookWithWords(
        bookId: Long,
        name: String,
        isPreset: Boolean,
        vararg wordTexts: String,
    ): List<Long> {
        db.bookDao().insert(Book(bookId = bookId, name = name, isPreset = isPreset))
        val wordIds = wordTexts.map { db.wordDao().findOrInsertWord(it, null) }
        wordIds.forEach {
            db.wordDao().insertSense(Sense(wordId = it, pos = "n.", meaning = "测试释义"))
            db.bookDao().linkBookWord(BookWord(bookId = bookId, wordId = it))
        }
        return wordIds
    }

    /** The resume word saved for [bookId] within [timeoutMs], or null if none arrived.
     *  The VM writes it from its own scope, so the test polls instead of assuming. */
    private suspend fun awaitSavedResumeWordId(bookId: Long, timeoutMs: Long = 5_000): Long? =
        withTimeoutOrNull(timeoutMs) {
            while (settings.resumeWordId(bookId) == null) delay(50)
            settings.resumeWordId(bookId)
        }

    /**
     * Tests in this process share the app's DataStore, and a written current_book_id
     * cannot be removed, so "no recorded book" is emulated with an id that matches no
     * row: the loader falls through to default selection, the same path a fresh
     * install takes.
     */
    private suspend fun recordNoBook() {
        settings.setCurrentBookId(NO_BOOK_ID)
    }

    /** The state the loader settled on within [timeoutMs], or null while it is still waiting. */
    private suspend fun statePastLoadingOrNull(vm: DrillViewModel, timeoutMs: Long): DrillUiState? =
        withTimeoutOrNull(timeoutMs) { vm.uiState.first { it != DrillUiState.Loading } }

    @Test
    fun freshInstall_waitsForPresetBooks_thenSelectsFirstNonReview() = runBlocking {
        recordNoBook()
        val vm = newViewModel()

        // Issue #30: on an empty book table the loader must wait for the first-launch
        // import instead of settling on the empty state after one sample.
        assertThat(statePastLoadingOrNull(vm, 1_000)).isNull()

        // The review book is created early by its own initializer; it must not win
        // the default slot.
        db.bookDao().insert(Book(name = REVIEW_BOOK_NAME, isPreset = true))
        assertThat(statePastLoadingOrNull(vm, 1_000)).isNull()

        // Both presets in one transaction, like the real import: the library ordering
        // decides and CET-4 is first.
        val cet4 = db.withTransaction {
            db.bookDao().insert(Book(name = "CET-6", isPreset = true))
            insertBookWithWord(name = "CET-4", isPreset = true, wordText = "apple")
        }
        val ready = vm.uiState.first { it is DrillUiState.Ready } as DrillUiState.Ready
        assertThat(ready.bookName).isEqualTo("CET-4")
        assertThat(ready.cards).hasSize(1)

        // The default book is recorded, so the next launch resolves it immediately.
        assertThat(settings.currentBookId.first { it == cet4 }).isEqualTo(cet4)
        Unit
    }

    @Test
    fun recordedCurrentBook_winsImmediately_overDefaultBook() = runBlocking {
        val mine = insertBookWithWord(name = "我的词书", isPreset = false, wordText = "apple")
        db.bookDao().insert(Book(name = "CET-4", isPreset = true))
        settings.setCurrentBookId(mine)
        val vm = newViewModel()

        val ready = vm.uiState.first { it is DrillUiState.Ready } as DrillUiState.Ready
        assertThat(ready.bookName).isEqualTo("我的词书")
        assertThat(settings.currentBookId.first { it == mine }).isEqualTo(mine)
        Unit
    }

    @Test
    fun drilledWord_savedOnSettle_restoredAfterRestart() = runBlocking {
        val bookId = 934_101L
        val wordIds = insertBookWithWords(bookId, "恢复词书A", isPreset = false, "apple", "balance", "circle")
        settings.setCurrentBookId(bookId)
        val first = newViewModel()
        first.uiState.first { it is DrillUiState.Ready }

        // Drill forward: the pager settles on the second card, which becomes the
        // book's resume position.
        first.onPageSettled(previousPage = 0, currentPage = 1)
        assertThat(awaitSavedResumeWordId(bookId)).isEqualTo(wordIds[1])

        // Restarting (a new ViewModel) lands on the settled word, not the first card.
        val restarted = newViewModel()
        val ready = restarted.uiState.first { it is DrillUiState.Ready } as DrillUiState.Ready
        assertThat(ready.initialPageIndex).isEqualTo(1)
        assertThat(ready.cards[ready.initialPageIndex].word.text).isEqualTo("balance")
        Unit
    }

    @Test
    fun skippingCurrentCard_makesNextCardTheResumePoint() = runBlocking {
        val bookId = 934_104L
        val wordIds = insertBookWithWords(bookId, "跳过词书", isPreset = false, "apple", "balance", "circle")
        settings.setCurrentBookId(bookId)
        val vm = newViewModel()
        vm.uiState.first { it is DrillUiState.Ready }

        // Skipping the middle card shrinks the deck; the third card takes its index
        // and becomes the resume point, so a restart never lands on a skipped word.
        vm.skipCurrentWord(currentPage = 1)
        val afterSkip = vm.uiState
            .first { it is DrillUiState.Ready && it.cards.size == 2 } as DrillUiState.Ready
        assertThat(afterSkip.initialPageIndex).isEqualTo(1)
        assertThat(awaitSavedResumeWordId(bookId)).isEqualTo(wordIds[2])
        Unit
    }

    @Test
    fun switchingBooks_resumesEachBookAtItsOwnWord() = runBlocking {
        val bookA = 934_102L
        val bookB = 934_103L
        val wordsA = insertBookWithWords(bookA, "词书甲", isPreset = false, "apple", "balance", "circle")
        val wordsB = insertBookWithWords(bookB, "词书乙", isPreset = false, "apple", "balance", "circle")
        settings.setCurrentBookId(bookA)
        val vm = newViewModel()
        vm.uiState.first { it is DrillUiState.Ready }

        // Drill A to its third card, switch to B and drill it to its second card.
        vm.onPageSettled(previousPage = 0, currentPage = 2)
        assertThat(awaitSavedResumeWordId(bookA)).isEqualTo(wordsA[2])
        settings.setCurrentBookId(bookB)
        vm.uiState.first { it is DrillUiState.Ready && it.bookName == "词书乙" }
        vm.onPageSettled(previousPage = 0, currentPage = 1)
        assertThat(awaitSavedResumeWordId(bookB)).isEqualTo(wordsB[1])

        // Switching back and forth keeps each book at its own resume position.
        settings.setCurrentBookId(bookA)
        val backInA = vm.uiState
            .first { it is DrillUiState.Ready && it.bookName == "词书甲" } as DrillUiState.Ready
        assertThat(backInA.initialPageIndex).isEqualTo(2)
        settings.setCurrentBookId(bookB)
        val backInB = vm.uiState
            .first { it is DrillUiState.Ready && it.bookName == "词书乙" } as DrillUiState.Ready
        assertThat(backInB.initialPageIndex).isEqualTo(1)
        Unit
    }

    @Test
    fun savedWordOutOfDeck_fallsBackToFirstCard() = runBlocking {
        val bookId = 934_105L
        insertBookWithWords(bookId, "失效词书", isPreset = false, "apple", "balance", "circle")
        // A word that exists but is not in this deck, like one restored away.
        val unlinked = db.wordDao().insert(Word(text = "absent"))
        settings.setResumeWordId(bookId, unlinked)
        settings.setCurrentBookId(bookId)
        val vm = newViewModel()

        val ready = vm.uiState.first { it is DrillUiState.Ready } as DrillUiState.Ready
        assertThat(ready.initialPageIndex).isEqualTo(0)
        Unit
    }

    @Test
    fun reviewBook_resumesLikeAnyOtherBook() = runBlocking {
        val sourceBookId = 934_106L
        val reviewBookId = 934_107L
        val wordIds = insertBookWithWords(sourceBookId, "来源词书", isPreset = false, "apple", "balance")
        db.bookDao().insert(Book(bookId = reviewBookId, name = REVIEW_BOOK_NAME, isPreset = true))
        wordIds.forEach {
            db.bookDao().setSkipped(sourceBookId, it, skipped = true)
            db.bookDao().linkBookWord(BookWord(bookId = reviewBookId, wordId = it))
        }
        settings.setResumeWordId(reviewBookId, wordIds[1])
        settings.setCurrentBookId(reviewBookId)
        val vm = newViewModel()

        val ready = vm.uiState.first { it is DrillUiState.Ready } as DrillUiState.Ready
        assertThat(ready.isReviewBook).isTrue()
        assertThat(ready.initialPageIndex).isEqualTo(1)
        assertThat(ready.cards[ready.initialPageIndex].word.text).isEqualTo("balance")
        Unit
    }

    @Test
    fun freshInstall_noSavedPosition_startsAtFirstCard() = runBlocking {
        val bookId = 934_108L
        insertBookWithWords(bookId, "全新词书", isPreset = false, "apple", "balance", "circle")
        settings.setCurrentBookId(bookId)
        val vm = newViewModel()

        val ready = vm.uiState.first { it is DrillUiState.Ready } as DrillUiState.Ready
        assertThat(ready.initialPageIndex).isEqualTo(0)
        Unit
    }

    @Test
    fun autoAdvance_togglesThroughViewModel_advancesAndContinuesOnSettle() = runBlocking {
        val bookId = 934_110L
        insertBookWithWords(bookId, "连播词书", isPreset = false, "apple", "balance", "circle")
        settings.setCurrentBookId(bookId)
        val vm = newViewModel()
        vm.uiState.first { it is DrillUiState.Ready }
        try {
            // A 1 s dwell keeps this device-paced test fast; restored afterwards so
            // later classes in the shared process see the default again.
            settings.setDwellInterval(1f)

            vm.toggleAutoAdvance(currentPage = 0)
            assertThat(vm.playback.value.phase).isEqualTo(AutoAdvancePhase.Playing)

            vm.toggleAutoAdvance(currentPage = 0) // pause freezes
            assertThat(vm.playback.value.phase).isEqualTo(AutoAdvancePhase.Paused)

            vm.toggleAutoAdvance(currentPage = 0) // resume continues
            assertThat(vm.playback.value.phase).isEqualTo(AutoAdvancePhase.Playing)

            // The dwell elapses and the ViewModel emits the flip command.
            val target = withTimeoutOrNull(5_000) { vm.advance.first() }
            assertThat(target).isEqualTo(1)

            // The screen executes the flip; its settle flows through the existing
            // onPageSettled, which repositions playback on the new card.
            vm.onPageSettled(previousPage = 0, currentPage = 1)
            assertThat(vm.playback.value.phase).isEqualTo(AutoAdvancePhase.Playing)
        } finally {
            settings.setDwellInterval(DwellInterval.DEFAULT_SECONDS)
        }
        Unit
    }

    @Test
    fun stopAutoAdvance_stopsOutright_lateSettleAndFurtherDwellStayIdle() = runBlocking {
        val bookId = 934_111L
        insertBookWithWords(bookId, "离场词书", isPreset = false, "apple", "balance", "circle")
        settings.setCurrentBookId(bookId)
        val vm = newViewModel()
        vm.uiState.first { it is DrillUiState.Ready }
        try {
            settings.setDwellInterval(1f)
            vm.toggleAutoAdvance(currentPage = 0)
            assertThat(vm.playback.value.phase).isEqualTo(AutoAdvancePhase.Playing)

            // The leave-route / ON_STOP seam: playback stops outright, never pauses.
            vm.stopAutoAdvance()
            assertThat(vm.playback.value.phase).isEqualTo(AutoAdvancePhase.Idle)

            // A flip whose animation was mid-flight when the user left settles late on
            // the restored screen: playback must stay idle, not resurrect from it.
            vm.onPageSettled(previousPage = 0, currentPage = 1)
            assertThat(vm.playback.value.phase).isEqualTo(AutoAdvancePhase.Idle)

            // The stopped countdown emits nothing further.
            assertThat(withTimeoutOrNull(1_500) { vm.advance.first() }).isNull()
        } finally {
            settings.setDwellInterval(DwellInterval.DEFAULT_SECONDS)
        }
        Unit
    }

    @Test
    fun softDeletedRecordedBook_fallsBackToDefaultBook() = runBlocking {
        insertBookWithWord("CET-4", isPreset = true, "alpha")
        val customId = insertBookWithWord("临别礼物", isPreset = false, "beta")
        settings.setCurrentBookId(customId)
        db.bookDao().deleteCustom(customId)
        val vm = newViewModel()

        // The recorded book is soft-deleted to the recycle bin; resolving it as the
        // current book strands the drill tab on the empty state (blind e2e finding).
        val ready = vm.uiState.first { it is DrillUiState.Ready } as DrillUiState.Ready
        assertThat(ready.bookName).isEqualTo("CET-4")
        Unit
    }

    @Test
    fun deletingCurrentBookWhileAlive_fallsBackToDefaultBook() = runBlocking {
        insertBookWithWord("CET-4", isPreset = true, "alpha")
        val customId = insertBookWithWord("漂流书", isPreset = false, "beta")
        settings.setCurrentBookId(customId)
        val vm = newViewModel()
        vm.uiState.first { it is DrillUiState.Ready }

        // The ViewModel stays alive while the user deletes the current book in the
        // Library; the recorded id never changes, so the loader must also react to
        // the book list itself (blind e2e finding, second half).
        db.bookDao().deleteCustom(customId)
        val ready = vm.uiState.first { it is DrillUiState.Ready && it.bookName == "CET-4" } as DrillUiState.Ready
        assertThat(ready.bookName).isEqualTo("CET-4")
        assertThat(settings.currentBookId.first()).isEqualTo(ready.bookId)
        Unit
    }

    @Test
    fun resetWhileBookCurrent_reloadsFullDeckAtFirstCard() = runBlocking {
        val bookId = 934_601L
        insertBookWithWords(bookId, "重置进度书", isPreset = false, "apple", "balance", "circle")
        settings.setCurrentBookId(bookId)
        val vm = newViewModel()
        val before = vm.uiState.first { it is DrillUiState.Ready } as DrillUiState.Ready
        assertThat(before.deckGeneration).isEqualTo(0)

        // Drill into the book and skip the middle card: the deck shrinks and the
        // resume position moves off the first card.
        vm.skipCurrentWord(currentPage = 1)
        vm.uiState.first { it is DrillUiState.Ready && it.cards.size == 2 }
        vm.onPageSettled(previousPage = 1, currentPage = 1)
        assertThat(awaitSavedResumeWordId(bookId)).isNotNull()

        // The resetter runs on the word-list page; the retained drill ViewModel must
        // reload the restored deck and land on the first card again.
        resetter.reset(bookId)
        val after = vm.uiState
            .first { it is DrillUiState.Ready && it.deckGeneration > before.deckGeneration }
            as DrillUiState.Ready
        assertThat(after.cards.map { it.word.text }).containsExactly("apple", "balance", "circle").inOrder()
        assertThat(after.initialPageIndex).isEqualTo(0)
        assertThat(settings.resumeWordId(bookId)).isNull()
        Unit
    }

    @Test
    fun deck_ordersMixedCase_caseInsensitive() = runBlocking {
        // The deck consumes the shared ordering seam (issue #51): lowercase words
        // interleave with uppercase ones, "core" landing in the C section.
        val bookId = 934_107L
        insertBookWithWords(bookId, "大小写词书", isPreset = false, "Zebra", "core", "Apple")
        settings.setCurrentBookId(bookId)
        val vm = newViewModel()

        val ready = vm.uiState.first { it is DrillUiState.Ready } as DrillUiState.Ready
        assertThat(ready.cards.map { it.word.text }).containsExactly("Apple", "core", "Zebra").inOrder()
        Unit
    }

    /** Reopens [bookId] through [detourBook] and returns the freshly dealt deck. */
    private suspend fun reopenDeck(vm: DrillViewModel, bookId: Long, detourBook: Long): List<String> {
        settings.setCurrentBookId(detourBook)
        vm.uiState.first { it is DrillUiState.Ready && it.bookId == detourBook }
        settings.setCurrentBookId(bookId)
        val ready = vm.uiState.first { it is DrillUiState.Ready && it.bookId == bookId } as DrillUiState.Ready
        return ready.cards.map { it.word.text }
    }

    @Test
    fun shuffleMode_reRandomizesTheDeckOnEveryBookOpen() = runBlocking {
        val bookId = 934_701L
        val detourBook = 934_702L
        insertBookWithWords(
            bookId, "乱序词书", isPreset = false,
            "w01", "w02", "w03", "w04", "w05", "w06",
            "w07", "w08", "w09", "w10", "w11", "w12",
        )
        insertBookWithWords(detourBook, "换乘词书", isPreset = false, "apple")
        settings.setCurrentBookId(bookId)
        val vm = newViewModel()
        try {
            settings.setDeckOrdering(DeckOrdering.SHUFFLED)
            val first = reopenDeck(vm, bookId, detourBook)
            val second = reopenDeck(vm, bookId, detourBook)
            // Two opens of the same book must deal different orders (issue #54: no
            // fixed pattern to memorize). With 12 cards the collision chance is 1/12!.
            assertThat(first).isNotEqualTo(second)
        } finally {
            settings.setDeckOrdering(DeckOrdering.ALPHABETICAL)
        }
        Unit
    }

    @Test
    fun shuffleMode_resumeLandsOnSameWord_afterReshuffle() = runBlocking {
        val bookId = 934_703L
        insertBookWithWords(
            bookId, "乱序恢复词书", isPreset = false,
            "w01", "w02", "w03", "w04", "w05", "w06",
            "w07", "w08", "w09", "w10", "w11", "w12",
        )
        settings.setCurrentBookId(bookId)
        settings.setDeckOrdering(DeckOrdering.SHUFFLED)
        val first = newViewModel()
        try {
            val firstReady = first.uiState.first { it is DrillUiState.Ready } as DrillUiState.Ready
            // Drill into the book: the settled card becomes the resume position.
            first.onPageSettled(previousPage = 0, currentPage = 5)
            val drilledWordId = firstReady.cards[5].word.wordId
            assertThat(awaitSavedResumeWordId(bookId)).isEqualTo(drilledWordId)

            // Reopening re-deals the deck; the resume key is the word, so the
            // reopened book lands on that same word even though its index moved.
            val reopened = newViewModel()
            val reopenedReady = reopened.uiState.first { it is DrillUiState.Ready } as DrillUiState.Ready
            assertThat(reopenedReady.cards[reopenedReady.initialPageIndex].word.wordId)
                .isEqualTo(drilledWordId)
        } finally {
            settings.setDeckOrdering(DeckOrdering.ALPHABETICAL)
        }
        Unit
    }

    @Test
    fun bookOpen_publishesDealtSequenceForTheWordList() = runBlocking {
        val bookId = 934_704L
        insertBookWithWords(bookId, "发布词书", isPreset = false, "Zebra", "apple", "core")
        settings.setCurrentBookId(bookId)
        val vm = newViewModel()

        val ready = vm.uiState.first { it is DrillUiState.Ready } as DrillUiState.Ready
        // The word list consumes exactly this sequence (issue #54: one ordering
        // source for both surfaces), dealt in the sampled ordering (#60: the
        // letter bar follows it).
        assertThat(deckSequence.sequences.value[bookId])
            .isEqualTo(DealtDeck(ready.cards.map { it.word.wordId }, DeckOrdering.ALPHABETICAL))
        Unit
    }

    // --- Completion card and completion count (#57) ---

    /** The persisted count once it reaches [expected], or -1 on timeout. The settle
     *  handler writes it from the ViewModel's own scope, so the test polls. */
    private suspend fun awaitDbCompletionCount(bookId: Long, expected: Int, timeoutMs: Long = 5_000): Int {
        var count = -1
        withTimeoutOrNull(timeoutMs) {
            while (true) {
                count = db.bookDao().getById(bookId)?.completionCount ?: -1
                if (count == expected) break
                delay(50)
            }
        }
        return count
    }

    @Test
    fun settlingOnCompletionPage_incrementsCountOncePerAppearance() = runBlocking {
        val bookId = 934_610L
        val wordIds = insertBookWithWords(bookId, "完成词书", isPreset = false, "apple", "balance", "circle")
        settings.setCurrentBookId(bookId)
        val vm = newViewModel()
        val ready0 = vm.uiState.first { it is DrillUiState.Ready } as DrillUiState.Ready
        assertThat(ready0.completionCount).isEqualTo(0)

        // Drill to the last card, then one more settle onto the virtual completion
        // page (index == deck size): one finished pass.
        vm.onPageSettled(previousPage = 0, currentPage = 2)
        assertThat(awaitSavedResumeWordId(bookId)).isEqualTo(wordIds[2])
        vm.onPageSettled(previousPage = 2, currentPage = 3)
        val ready1 = vm.uiState.value as DrillUiState.Ready
        assertThat(ready1.completionCount).isEqualTo(1)
        assertThat(awaitDbCompletionCount(bookId, 1)).isEqualTo(1)

        // The completion page is not a word: the resume position stays on the last
        // card, and a re-emitted settle of the same page must not double-increment.
        assertThat(settings.resumeWordId(bookId)).isEqualTo(wordIds[2])
        vm.onPageSettled(previousPage = 3, currentPage = 3)
        assertThat((vm.uiState.value as DrillUiState.Ready).completionCount).isEqualTo(1)
        assertThat(awaitDbCompletionCount(bookId, 1)).isEqualTo(1)

        // Bouncing back and returning is a second appearance: pass two.
        vm.onPageSettled(previousPage = 3, currentPage = 2)
        vm.onPageSettled(previousPage = 2, currentPage = 3)
        assertThat((vm.uiState.value as DrillUiState.Ready).completionCount).isEqualTo(2)
        assertThat(awaitDbCompletionCount(bookId, 2)).isEqualTo(2)
        Unit
    }

    @Test
    fun completionCount_persistsAcrossViewModelRecreation() = runBlocking {
        val bookId = 934_611L
        insertBookWithWords(bookId, "重启词书", isPreset = false, "apple", "balance", "circle")
        settings.setCurrentBookId(bookId)
        val first = newViewModel()
        first.uiState.first { it is DrillUiState.Ready }
        first.onPageSettled(previousPage = 2, currentPage = 3)
        assertThat(awaitDbCompletionCount(bookId, 1)).isEqualTo(1)

        // A relaunch (new ViewModel over the same database) reloads the book with its
        // accumulated count.
        val restarted = newViewModel()
        val ready = restarted.uiState.first { it is DrillUiState.Ready } as DrillUiState.Ready
        assertThat(ready.completionCount).isEqualTo(1)
        Unit
    }

    @Test
    fun restartFromCompletion_resetsDeck_landsOnFirstCard_keepsCount() = runBlocking {
        val bookId = 934_612L
        insertBookWithWords(bookId, "重刷词书", isPreset = false, "apple", "balance", "circle")
        settings.setCurrentBookId(bookId)
        val vm = newViewModel()
        vm.uiState.first { it is DrillUiState.Ready }

        // Skip the middle card and finish the shrunken deck: the completion card
        // appears with pass one.
        vm.skipCurrentWord(currentPage = 1)
        vm.uiState.first { it is DrillUiState.Ready && it.cards.size == 2 }
        vm.onPageSettled(previousPage = 1, currentPage = 2)
        assertThat((vm.uiState.value as DrillUiState.Ready).completionCount).isEqualTo(1)

        // 重新刷: the shared reset engine restores the full deck at the first card;
        // the completion count is cumulative history and survives.
        vm.restartBook()
        val after = vm.uiState
            .first { it is DrillUiState.Ready && it.deckGeneration > 0 } as DrillUiState.Ready
        assertThat(after.cards.map { it.word.text }).containsExactly("apple", "balance", "circle").inOrder()
        assertThat(after.initialPageIndex).isEqualTo(0)
        assertThat(after.completionCount).isEqualTo(1)
        assertThat(settings.resumeWordId(bookId)).isNull()
        assertThat(awaitDbCompletionCount(bookId, 1)).isEqualTo(1)
        Unit
    }

    @Test
    fun reviewBook_settlingOnCompletionPage_incrementsCount() = runBlocking {
        // The review book is drilled like any other book (GLOSSARY.md): its deck also
        // ends in a completion card, and its passes count like any book's.
        val sourceBookId = 934_613L
        val reviewBookId = 934_614L
        val wordIds = insertBookWithWords(sourceBookId, "复习来源词书", isPreset = false, "apple", "balance")
        db.bookDao().insert(Book(bookId = reviewBookId, name = REVIEW_BOOK_NAME, isPreset = true))
        wordIds.forEach {
            db.bookDao().setSkipped(sourceBookId, it, skipped = true)
            db.bookDao().linkBookWord(BookWord(bookId = reviewBookId, wordId = it))
        }
        settings.setCurrentBookId(reviewBookId)
        val vm = newViewModel()
        val ready = vm.uiState.first { it is DrillUiState.Ready } as DrillUiState.Ready
        assertThat(ready.isReviewBook).isTrue()

        vm.onPageSettled(previousPage = 0, currentPage = 2)
        assertThat((vm.uiState.value as DrillUiState.Ready).completionCount).isEqualTo(1)
        assertThat(awaitDbCompletionCount(reviewBookId, 1)).isEqualTo(1)
        Unit
    }

    private companion object {
        const val NO_BOOK_ID = 999_999L
    }
}
