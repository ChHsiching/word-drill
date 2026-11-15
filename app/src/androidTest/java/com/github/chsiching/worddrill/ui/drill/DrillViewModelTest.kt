package com.github.chsiching.worddrill.ui.drill

import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.github.chsiching.worddrill.data.local.WordDrillDatabase
import com.github.chsiching.worddrill.data.local.dao.REVIEW_BOOK_NAME
import com.github.chsiching.worddrill.data.local.entity.Book
import com.github.chsiching.worddrill.data.local.entity.BookWord
import com.github.chsiching.worddrill.data.local.entity.Sense
import com.github.chsiching.worddrill.data.local.entity.Word
import com.github.chsiching.worddrill.data.settings.SettingsRepository
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
    private val viewModels = mutableListOf<DrillViewModel>()

    @Before
    fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(context, WordDrillDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        settings = SettingsRepository(context)
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
        Unit
    }

    private fun newViewModel(): DrillViewModel =
        DrillViewModel(
            db = db,
            bookDao = db.bookDao(),
            wordDao = db.wordDao(),
            swipeLogDao = db.swipeLogDao(),
            settings = settings,
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

    private companion object {
        const val NO_BOOK_ID = 999_999L
    }
}
