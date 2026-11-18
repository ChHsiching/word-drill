package com.github.chsiching.worddrill.ui.library

import androidx.compose.material3.Surface
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.chsiching.worddrill.data.ProgressResetter
import com.github.chsiching.worddrill.data.local.WordDrillDatabase
import com.github.chsiching.worddrill.data.local.dao.REVIEW_BOOK_NAME
import com.github.chsiching.worddrill.data.local.entity.Book
import com.github.chsiching.worddrill.data.local.entity.BookWord
import com.github.chsiching.worddrill.data.local.entity.Sense
import com.github.chsiching.worddrill.data.local.entity.SwipeLog
import com.github.chsiching.worddrill.data.local.entity.Word
import com.github.chsiching.worddrill.data.settings.SettingsRepository
import com.github.chsiching.worddrill.ui.DeckSequenceState
import com.github.chsiching.worddrill.ui.drill.AutoAdvanceOverlayState
import com.github.chsiching.worddrill.ui.drill.DrillUiState
import com.github.chsiching.worddrill.ui.drill.DrillViewModel
import com.github.chsiching.worddrill.ui.theme.WordDrillTheme
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Progress reset (#52): the word-list entry point and the full reset outcome on a
 *  partially drilled book with skips. Book ids are explicit and unique per test —
 *  resume keys live in the process-wide DataStore while the in-memory database
 *  restarts per test. */
@RunWith(AndroidJUnit4::class)
class ProgressResetFlowTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context: android.content.Context
        get() = ApplicationProvider.getApplicationContext()

    private lateinit var db: WordDrillDatabase
    private lateinit var settings: SettingsRepository
    private lateinit var resetter: ProgressResetter

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
        // Drain in-flight Room flow queries before the pool closes (8c3567a convention).
        delay(200)
        db.close()
        Unit
    }

    private suspend fun seedBook(bookId: Long, name: String, isPreset: Boolean, vararg wordTexts: String) {
        db.bookDao().insert(Book(bookId = bookId, name = name, isPreset = isPreset))
        wordTexts.forEach { text ->
            val wordId = db.wordDao().findOrInsertWord(text, null)
            db.wordDao().insertSense(Sense(wordId = wordId, pos = "n.", meaning = "测试释义"))
            db.bookDao().linkBookWord(BookWord(bookId = bookId, wordId = wordId))
        }
    }

    private fun setContentFor(bookId: Long) {
        val vm = WordListViewModel(
            savedStateHandle = androidx.lifecycle.SavedStateHandle(
                mapOf("bookId" to bookId.toString())
            ),
            db = db,
            wordDao = db.wordDao(),
            bookDao = db.bookDao(),
            dictionaryDao = db.dictionaryDao(),
            progressResetter = resetter,
            deckSequence = DeckSequenceState(),
        )
        composeRule.setContent {
            WordDrillTheme {
                Surface {
                    WordListScreen(onBack = {}, viewModel = vm)
                }
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun resetAction_offeredForCustomBook() = runBlocking {
        seedBook(801_101L, "流程词书", isPreset = false, "apple")
        setContentFor(801_101L)
        composeRule.onNodeWithContentDescription("重置进度").assertIsDisplayed()
        Unit
    }

    @Test
    fun resetAction_offeredForPresetBook() = runBlocking {
        seedBook(801_102L, "CET-4", isPreset = true, "balance")
        setContentFor(801_102L)
        composeRule.onNodeWithContentDescription("重置进度").assertIsDisplayed()
        Unit
    }

    @Test
    fun resetAction_hiddenForReviewBook() = runBlocking {
        // Explicit review id: an auto id right after a large explicit book id lands
        // on the adjacent number and any later use of it would REPLACE the row.
        seedBook(801_103L, "CET-4", isPreset = true, "circle")
        val reviewId = db.bookDao().insert(Book(bookId = 801_903L, name = REVIEW_BOOK_NAME, isPreset = true))
        setContentFor(reviewId)
        composeRule.onNodeWithContentDescription("重置进度").assertDoesNotExist()
        Unit
    }

    @Test
    fun reset_confirmDialog_showsAndResetOnConfirm() = runBlocking {
        val bookId = 801_201L
        seedBook(bookId, "确认词书", isPreset = false, "apple", "balance", "circle")
        val reviewId = db.bookDao().insert(Book(bookId = 801_901L, name = REVIEW_BOOK_NAME, isPreset = true))
        val skipped = db.wordDao().findIdByText("apple")!!
        db.bookDao().setSkipped(bookId, skipped, skipped = true)
        db.bookDao().linkBookWord(BookWord(bookId = reviewId, wordId = skipped))
        settings.setResumeWordId(bookId, db.wordDao().findIdByText("circle")!!)

        setContentFor(bookId)
        composeRule.onNodeWithContentDescription("重置进度").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("重置进度", substring = false).assertIsDisplayed()
        composeRule.onNodeWithText("确定重置「确认词书」的进度？将回到第一张卡片，跳过的单词全部回到牌组并从复习移除；刷卡记录和统计保留。")
            .assertIsDisplayed()

        // Cancel first: nothing changes.
        composeRule.onNodeWithText("取消").performClick()
        composeRule.waitForIdle()
        assertThat(db.bookDao().getSkipped(bookId, skipped)).isTrue()

        composeRule.onNodeWithContentDescription("重置进度").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("重置", substring = false).performClick()
        composeRule.waitForIdle()

        assertThat(db.bookDao().getSkipped(bookId, skipped)).isFalse()
        assertThat(db.wordDao().getWordsWithSensesByBook(reviewId)).isEmpty()
        assertThat(settings.resumeWordId(bookId)).isNull()
        Unit
    }

    /** The ticket's instrumented scenario: a partially drilled book with skips is
     *  reset from the word list; afterwards the book opens at its first card with
     *  the full deck, the review book no longer lists the words, and stats and
     *  swipe logs are untouched. */
    @Test
    fun reset_partiallyDrilledBookWithSkips_producesStartingState() = runBlocking {
        val bookId = 801_301L
        seedBook(bookId, "半程词书", isPreset = false, "apple", "balance", "circle", "duty")
        val reviewId = db.bookDao().insert(Book(bookId = 801_902L, name = REVIEW_BOOK_NAME, isPreset = true))
        val apple = db.wordDao().findIdByText("apple")!!
        val circle = db.wordDao().findIdByText("circle")!!
        listOf(apple, circle).forEach { wordId ->
            db.bookDao().setSkipped(bookId, wordId, skipped = true)
            db.bookDao().linkBookWord(BookWord(bookId = reviewId, wordId = wordId))
        }
        settings.setResumeWordId(bookId, db.wordDao().findIdByText("duty")!!)
        val logDao = db.swipeLogDao()
        logDao.insert(SwipeLog(bookId = bookId, wordId = apple, timestamp = 1_000L))
        logDao.insert(SwipeLog(bookId = bookId, wordId = db.wordDao().findIdByText("balance")!!, timestamp = 2_000L))
        val logsBefore = logDao.getAll()

        setContentFor(bookId)
        composeRule.onNodeWithContentDescription("重置进度").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("重置", substring = false).performClick()
        composeRule.waitForIdle()
        composeRule.waitUntil(10_000L) {
            runBlocking { settings.resumeWordId(bookId) == null }
        }

        // Deck complete again: every word back, none skipped.
        assertThat(db.wordDao().getWordsWithSensesByBook(bookId).map { it.word.text })
            .containsExactly("apple", "balance", "circle", "duty").inOrder()
        // Review book no longer lists them.
        assertThat(db.wordDao().getWordsWithSensesByBook(reviewId)).isEmpty()
        // Stats and swipe logs unchanged.
        assertThat(logDao.getAll()).isEqualTo(logsBefore)
        assertThat(logDao.totalCount()).isEqualTo(2)
        assertThat(logDao.countSince(0L)).isEqualTo(2)

        // Opening the book lands on the first card (fresh drill, like a restart).
        settings.setCurrentBookId(bookId)
        val drill = DrillViewModel(
            db = db,
            bookDao = db.bookDao(),
            wordDao = db.wordDao(),
            swipeLogDao = db.swipeLogDao(),
            settings = settings,
            overlayState = AutoAdvanceOverlayState(),
            progressResetter = resetter,
            deckSequence = DeckSequenceState(),
        )
        try {
            val ready = drill.uiState.first { it is DrillUiState.Ready } as DrillUiState.Ready
            assertThat(ready.initialPageIndex).isEqualTo(0)
            assertThat(ready.cards.first().word.text).isEqualTo("apple")
            assertThat(ready.cards).hasSize(4)
        } finally {
            drill.viewModelScope.cancel()
        }
        Unit
    }
}
