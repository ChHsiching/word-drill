package com.github.chsiching.worddrill.ui.drill

import androidx.compose.material3.Surface
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.github.chsiching.worddrill.data.local.WordDrillDatabase
import com.github.chsiching.worddrill.data.local.entity.Book
import com.github.chsiching.worddrill.data.local.entity.BookWord
import com.github.chsiching.worddrill.data.local.entity.Sense
import com.github.chsiching.worddrill.data.local.entity.Word
import com.github.chsiching.worddrill.data.settings.SettingsRepository
import com.github.chsiching.worddrill.ui.theme.WordDrillTheme
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DrillScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var db: WordDrillDatabase

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, WordDrillDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        seedData()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun seedData() = runBlocking {
        val bookId = db.bookDao().insert(Book(name = "测试词书", isPreset = false))
        val w1 = db.wordDao().insert(Word(text = "apple"))
        val w2 = db.wordDao().insert(Word(text = "balance"))
        val w3 = db.wordDao().insert(Word(text = "circle"))
        db.wordDao().insertSense(Sense(wordId = w1, pos = "n.", meaning = "苹果"))
        db.wordDao().insertSense(Sense(wordId = w1, pos = "v.", meaning = "囤积"))
        db.wordDao().insertSense(Sense(wordId = w2, pos = "n.", meaning = "平衡"))
        db.wordDao().insertSense(Sense(wordId = w3, pos = "n.", meaning = "圆圈"))
        db.bookDao().linkBookWord(BookWord(bookId, w1))
        db.bookDao().linkBookWord(BookWord(bookId, w2))
        db.bookDao().linkBookWord(BookWord(bookId, w3))
        Unit
    }

    private fun cards() = runBlocking {
        val bookId = db.bookDao().getByName("测试词书")!!.bookId
        db.wordDao().getWordsWithSensesByBook(bookId)
    }

    private fun composeDrillPager(
        locked: Boolean = false,
        onSkip: (Int) -> Unit = {},
        isReviewBook: Boolean = false,
        onRestore: (Int) -> Unit = {},
    ) {
        composeRule.setContent {
            WordDrillTheme {
                Surface {
                    DrillPager(
                        bookName = if (isReviewBook) "复习" else "测试词书",
                        cards = cards(),
                        onPageSettled = { _, _ -> },
                        onSkip = onSkip,
                        isReviewBook = isReviewBook,
                        onRestore = onRestore,
                        locked = locked,
                        onToggleLock = {},
                        hidePhonetic = false,
                        onToggleAutoAdvance = { },
                        playbackState = AutoAdvanceState(),
                        advance = emptyFlow(),
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun showsBookNameAndFirstCardContent() {
        composeDrillPager()

        composeRule.onNodeWithText("测试词书").assertIsDisplayed()
        composeRule.onNodeWithText("apple").assertIsDisplayed()
        composeRule.onNodeWithText("n.").assertIsDisplayed()
        composeRule.onNodeWithText("苹果").assertIsDisplayed()
        composeRule.onNodeWithText("v.").assertIsDisplayed()
        composeRule.onNodeWithText("囤积").assertIsDisplayed()
        composeRule.onNodeWithText("1 / 3").assertIsDisplayed()
    }

    @Test
    fun lastCardCounterShowsLastIndex() {
        composeDrillPager()
        repeat(2) {
            composeRule.onRoot().performTouchInput { swipeLeft() }
            composeRule.waitForIdle()
        }

        composeRule.onNodeWithText("circle").assertIsDisplayed()
        composeRule.onNodeWithText("3 / 3").assertIsDisplayed()
    }

    @Test
    fun skipButtonWorksWhileLocked() {
        var skippedPage: Int? = null
        composeDrillPager(locked = true, onSkip = { page -> skippedPage = page })

        composeRule.onNodeWithText("apple").assertIsDisplayed()
        composeRule.onNodeWithText("跳过").performClick()
        composeRule.waitForIdle()
        assertThat(skippedPage).isEqualTo(0)
    }

    @Test
    fun skipButton_passesCurrentPageToCallback() {
        var skippedPage: Int? = null
        composeDrillPager(onSkip = { page -> skippedPage = page })

        composeRule.onNodeWithText("跳过").performClick()
        composeRule.waitForIdle()
        assertThat(skippedPage).isEqualTo(0)
    }

    @Test
    fun reviewBook_showsRestoreButton_notSkip() {
        var skipCalled = false
        var restoreCalled = false
        composeDrillPager(
            isReviewBook = true,
            onSkip = { skipCalled = true },
            onRestore = { restoreCalled = true },
        )

        composeRule.onNodeWithText("恢复").assertIsDisplayed()
        composeRule.onNodeWithText("跳过").assertDoesNotExist()

        composeRule.onNodeWithText("恢复").performClick()
        composeRule.waitForIdle()
        assertThat(restoreCalled).isTrue()
        assertThat(skipCalled).isFalse()
    }

    @Test
    fun normalBook_showsSkipButton_notRestore() {
        composeDrillPager(isReviewBook = false)

        composeRule.onNodeWithText("跳过").assertIsDisplayed()
        composeRule.onNodeWithText("恢复").assertDoesNotExist()
    }

    @Test
    fun longBookName_doesNotPushOutRightSideButtons() {
        val longName = "我的超级长的自定义考研英语复习词书".repeat(3)
        composeRule.setContent {
            WordDrillTheme {
                Surface {
                    DrillPager(
                        bookName = longName,
                        cards = cards(),
                        onPageSettled = { _, _ -> },
                        onSkip = { },
                        isReviewBook = false,
                        onRestore = { },
                        locked = false,
                        onToggleLock = {},
                        hidePhonetic = false,
                        onToggleAutoAdvance = { },
                        playbackState = AutoAdvanceState(),
                        advance = emptyFlow(),
                    )
                }
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("跳过").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("锁定").assertIsDisplayed()
        composeRule.onNodeWithText("1 / 3").assertIsDisplayed()
        composeRule.onNodeWithText(longName).assertIsDisplayed()
    }

    @Test
    fun restoredResumePosition_pagerVisiblyLandsOnSavedCard() {
        // The pager must open on the book's resume position once cards load, not just
        // carry it in the data layer: with "balance" saved, the visible counter and
        // card are the second ones from the first frame.
        val vm = runBlocking { seedResumedBookAndCreateViewModel() }
        try {
            composeRule.setContent {
                WordDrillTheme {
                    Surface {
                        DrillScreen(viewModel = vm)
                    }
                }
            }
            composeRule.waitUntil(10_000L) {
                composeRule.onAllNodesWithText("2 / 3").fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithText("2 / 3").assertIsDisplayed()
            composeRule.onNodeWithText("balance").assertIsDisplayed()
        } finally {
            vm.viewModelScope.cancel()
        }
    }

    /** A book whose saved resume position is its second card ("balance"). The book id
     *  is explicit and in this test's own range: resume keys live in the process-wide
     *  DataStore while the in-memory database (and its ids) reset per test. */
    private suspend fun seedResumedBookAndCreateViewModel(): DrillViewModel {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val settings = SettingsRepository(context)
        val bookId = 934_501L
        val book = db.bookDao().insert(Book(bookId = bookId, name = "恢复词书", isPreset = false))
        listOf("apple", "balance", "circle").forEach {
            val wordId = db.wordDao().findOrInsertWord(it, null)
            db.bookDao().linkBookWord(BookWord(bookId = book, wordId = wordId))
        }
        settings.setResumeWordId(bookId, db.wordDao().findIdByText("balance")!!)
        settings.setCurrentBookId(bookId)
        return DrillViewModel(
            db = db,
            bookDao = db.bookDao(),
            wordDao = db.wordDao(),
            swipeLogDao = db.swipeLogDao(),
            settings = settings,
        )
    }

    @Test
    fun emptyDeck_keepsTopBarWithBookNameCounterActionAndEmptyMessage() {
        // An emptied deck (empty custom book or emptied review book) must keep the
        // drill header — book name, "0 / 0" counter, action label, lock — instead of
        // replacing the whole screen with the centered empty message.
        val vm = runBlocking { seedEmptyBookAndCreateViewModel() }
        try {
            composeRule.setContent {
                WordDrillTheme {
                    Surface {
                        DrillScreen(viewModel = vm)
                    }
                }
            }
            composeRule.waitUntil(10_000L) {
                composeRule.onAllNodesWithText("0 / 0").fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithText("空词书").assertIsDisplayed()
            composeRule.onNodeWithText("0 / 0").assertIsDisplayed()
            composeRule.onNodeWithText("跳过").assertIsDisplayed()
            composeRule.onNodeWithContentDescription("锁定").assertIsDisplayed()
            composeRule.onNodeWithText("这个词书是空的").assertIsDisplayed()

            // Skip is guarded on an empty deck: tapping it keeps the empty view.
            composeRule.onNodeWithText("跳过").performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithText("这个词书是空的").assertIsDisplayed()
        } finally {
            vm.viewModelScope.cancel()
        }
    }

    /** The selected book with no words, so the drill lands on the Empty state. The
     *  book id is explicit and in this test's own range: the current-book key lives in
     *  the process-wide DataStore while the in-memory database resets per test. */
    private suspend fun seedEmptyBookAndCreateViewModel(): DrillViewModel {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val settings = SettingsRepository(context)
        val bookId = 934_502L
        db.bookDao().insert(Book(bookId = bookId, name = "空词书", isPreset = false))
        settings.setCurrentBookId(bookId)
        return DrillViewModel(
            db = db,
            bookDao = db.bookDao(),
            wordDao = db.wordDao(),
            swipeLogDao = db.swipeLogDao(),
            settings = settings,
        )
    }
}
