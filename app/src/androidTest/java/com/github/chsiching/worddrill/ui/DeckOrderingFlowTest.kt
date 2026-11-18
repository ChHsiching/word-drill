package com.github.chsiching.worddrill.ui

import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.chsiching.worddrill.data.ProgressResetter
import com.github.chsiching.worddrill.data.local.WordDrillDatabase
import com.github.chsiching.worddrill.data.local.entity.Book
import com.github.chsiching.worddrill.data.local.entity.BookWord
import com.github.chsiching.worddrill.data.local.entity.Sense
import com.github.chsiching.worddrill.data.settings.DeckOrdering
import com.github.chsiching.worddrill.data.settings.SettingsRepository
import com.github.chsiching.worddrill.ui.drill.AutoAdvanceOverlayState
import com.github.chsiching.worddrill.ui.drill.DrillUiState
import com.github.chsiching.worddrill.ui.drill.DrillViewModel
import com.github.chsiching.worddrill.ui.library.WordListUiState
import com.github.chsiching.worddrill.ui.library.WordListViewModel
import com.google.common.truth.Truth.assertThat
import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Deck ordering end to end (issue #54): the word list mirrors the deck's dealt
 *  sequence through the shared DeckSequenceState, and changing the global setting
 *  re-deals both surfaces on the next book open. One DrillViewModel and one
 *  WordListViewModel share one DeckSequenceState, as Hilt would in the app. */
@RunWith(AndroidJUnit4::class)
class DeckOrderingFlowTest {

    private val context: android.content.Context
        get() = ApplicationProvider.getApplicationContext()

    private lateinit var db: WordDrillDatabase
    private lateinit var settings: SettingsRepository
    private lateinit var resetter: ProgressResetter
    private val deckSequence = DeckSequenceState()

    private val bookId = 934_801L
    private val detourBookId = 934_802L
    private val words = listOf("w01", "w02", "w03", "w04", "w05", "w06", "w07", "w08", "w09", "w10", "w11", "w12")
    private val alphabetical = words.sorted()

    private var drill: DrillViewModel? = null
    private var wordList: WordListViewModel? = null

    @Before
    fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(context, WordDrillDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        settings = SettingsRepository(context)
        resetter = ProgressResetter(db, db.bookDao(), settings)
        listOf(bookId to "乱序总览词书", detourBookId to "换乘词书").forEach { (id, name) ->
            db.bookDao().insert(Book(bookId = id, name = name, isPreset = false))
        }
        words.forEach { text ->
            val wordId = db.wordDao().findOrInsertWord(text, null)
            db.wordDao().insertSense(Sense(wordId = wordId, pos = "n.", meaning = "测试释义"))
            db.bookDao().linkBookWord(BookWord(bookId = bookId, wordId = wordId))
        }
        db.wordDao().findOrInsertWord("apple", null).let {
            db.bookDao().linkBookWord(BookWord(bookId = detourBookId, wordId = it))
        }
        Unit
    }

    @After
    fun tearDown() = runBlocking {
        // Shared-process discipline (DrillViewModelTest convention): cancel the
        // ViewModels, let in-flight queries drain, then close; restore the
        // process-global deck ordering to its default.
        listOfNotNull(drill, wordList).forEach { it.viewModelScope.cancel() }
        delay(200)
        db.close()
        settings.setDeckOrdering(DeckOrdering.ALPHABETICAL)
        Unit
    }

    private fun newDrill(): DrillViewModel =
        DrillViewModel(
            db = db,
            bookDao = db.bookDao(),
            wordDao = db.wordDao(),
            swipeLogDao = db.swipeLogDao(),
            settings = settings,
            overlayState = AutoAdvanceOverlayState(),
            progressResetter = resetter,
            deckSequence = deckSequence,
        ).also { drill = it }

    private fun newWordList(): WordListViewModel =
        WordListViewModel(
            savedStateHandle = SavedStateHandle(mapOf("bookId" to bookId.toString())),
            db = db,
            wordDao = db.wordDao(),
            bookDao = db.bookDao(),
            dictionaryDao = db.dictionaryDao(),
            progressResetter = resetter,
            deckSequence = deckSequence,
        ).also { wordList = it }

    private suspend fun deckOrder(vm: DrillViewModel): List<String> =
        (vm.uiState.first { it is DrillUiState.Ready && it.bookId == bookId } as DrillUiState.Ready)
            .cards.map { it.word.text }

    private suspend fun listOrder(vm: WordListViewModel): List<String> =
        vm.uiState.first { state -> state.words.size == words.size }
            .words.map { it.word.text }

    /** The list's first order that differs from [previous]: the state the word list
     *  settled on after the deck re-dealt. */
    private suspend fun listOrderAfterChange(vm: WordListViewModel, previous: List<String>): List<String> =
        vm.uiState.first { state ->
            state.words.size == words.size && state.words.map { it.word.text } != previous
        }.words.map { it.word.text }

    @Test
    fun alphabeticalByDefault_deckAndWordListShareOneOrder() = runBlocking {
        settings.setCurrentBookId(bookId)
        val drill = newDrill()
        val wordList = newWordList()

        assertThat(deckOrder(drill)).isEqualTo(alphabetical)
        assertThat(listOrder(wordList)).isEqualTo(alphabetical)
        Unit
    }

    @Test
    fun settingChange_reordersDeckAndWordList_onNextBookOpen() = runBlocking {
        settings.setCurrentBookId(bookId)
        val drill = newDrill()
        val wordList = newWordList()
        try {
            val deckBefore = deckOrder(drill)
            assertThat(deckBefore).isEqualTo(alphabetical)

            settings.setDeckOrdering(DeckOrdering.SHUFFLED)
            // Next book open: switch away and back so the drill re-deals.
            settings.setCurrentBookId(detourBookId)
            drill.uiState.first { it is DrillUiState.Ready && it.bookId == detourBookId }
            settings.setCurrentBookId(bookId)
            val deckAfter = deckOrder(drill)
            assertThat(deckAfter).isNotEqualTo(alphabetical)
            assertThat(deckAfter.sorted()).isEqualTo(alphabetical)

            // The word list shows the same dealt sequence (12 cards: the chance of
            // an accidental match with the previous order is 1/12!).
            assertThat(listOrderAfterChange(wordList, deckBefore)).isEqualTo(deckAfter)
        } finally {
            settings.setDeckOrdering(DeckOrdering.ALPHABETICAL)
        }
        Unit
    }

    @Test
    fun shuffleChangeBackToAlphabetical_reordersBoth_onNextBookOpen() = runBlocking {
        settings.setDeckOrdering(DeckOrdering.SHUFFLED)
        settings.setCurrentBookId(bookId)
        val drill = newDrill()
        val wordList = newWordList()
        try {
            assertThat(deckOrder(drill)).isNotEqualTo(alphabetical)
            assertThat(listOrder(wordList)).isNotEqualTo(alphabetical)

            settings.setDeckOrdering(DeckOrdering.ALPHABETICAL)
            settings.setCurrentBookId(detourBookId)
            drill.uiState.first { it is DrillUiState.Ready && it.bookId == detourBookId }
            settings.setCurrentBookId(bookId)

            assertThat(deckOrder(drill)).isEqualTo(alphabetical)
            // The list's pre-change state was the shuffled deal, so the first
            // alphabetical 12-word emission is the post-reopen one.
            assertThat(
                wordList.uiState.first { state ->
                    state.words.size == words.size &&
                        state.words.map { it.word.text } == alphabetical
                }.words.map { it.word.text },
            ).isEqualTo(alphabetical)
        } finally {
            settings.setDeckOrdering(DeckOrdering.ALPHABETICAL)
        }
        Unit
    }
}
