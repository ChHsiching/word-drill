package com.github.chsiching.worddrill.ui.drill

import androidx.compose.material3.Surface
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.github.chsiching.worddrill.data.local.WordDrillDatabase
import com.github.chsiching.worddrill.data.local.entity.Book
import com.github.chsiching.worddrill.data.local.entity.BookWord
import com.github.chsiching.worddrill.data.local.entity.Sense
import com.github.chsiching.worddrill.data.local.entity.Word
import com.github.chsiching.worddrill.ui.theme.WordDrillTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Main seam of the drill tab: Compose UI integration test + in-memory Room.
 *
 * Verifies the spec acceptance items (user-visible behavior):
 * - The card shows the word (with part of speech) and the Chinese meaning on one screen, in a large font
 * - The current word book name shows at the top (plain text)
 * - At the ends a hint text shows (first card reached / last card reached)
 *
 * [SwipeDirectionTest] (a pure-function JVM unit test) covers the swipe counting decision logic (a right swipe
 * counts 1, a left swipe counts 0, and a swipe at the end counts 0); we verified the end-to-end "swipe then write
 * swipe_log" path by hand on an emulator (MCP adb query on swipe_log: 2 right swipes = 2 rows, left swipes and hitting the end add nothing).
 *
 * The gesture internals of HorizontalPager are not tested (fragile and coupled to the implementation); only user-visible behavior is tested.
 */
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

    /** A word book with 3 cards, to verify the first / middle / last states. */
    private fun seedData() = runBlocking {
        val bookId = db.bookDao().insert(Book(name = "测试词书", isPreset = false))
        val w1 = db.wordDao().insert(Word(text = "apple"))
        val w2 = db.wordDao().insert(Word(text = "balance"))
        val w3 = db.wordDao().insert(Word(text = "circle"))
        // apple has multiple parts of speech: verify one card shows all senses on the same screen
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
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun showsBookNameAndFirstCardContent() {
        composeDrillPager()

        // Word book name at the top
        composeRule.onNodeWithText("测试词书").assertIsDisplayed()
        // First card: the English word plus the pos / Chinese meaning of both parts of speech all on the same screen (split into separate Text nodes since #16)
        composeRule.onNodeWithText("apple").assertIsDisplayed()
        composeRule.onNodeWithText("n.").assertIsDisplayed()
        composeRule.onNodeWithText("苹果").assertIsDisplayed()
        composeRule.onNodeWithText("v.").assertIsDisplayed()
        composeRule.onNodeWithText("囤积").assertIsDisplayed()
        // Top counter (review feedback: with the boundary hints removed, the counter "1 / 3" conveys position)
        composeRule.onNodeWithText("1 / 3").assertIsDisplayed()
    }

    @Test
    fun lastCardCounterShowsLastIndex() {
        composeDrillPager()
        // Swipe forward to the end (leftward gesture): with 3 cards it takes 2 swipes to reach the last one.
        repeat(2) {
            composeRule.onRoot().performTouchInput { swipeLeft() }
            composeRule.waitForIdle()
        }

        composeRule.onNodeWithText("circle").assertIsDisplayed()
        // Counter shows 3 / 3 (replaces the old "last card reached" boundary hint)
        composeRule.onNodeWithText("3 / 3").assertIsDisplayed()
    }

    /**
     * #17 (review feedback #3) + #20: while locked, the skip button still fires its callback on click.
     * Regression: in an early implementation onSkip contained a `!locked` guard, so skipping did nothing once locked. Acceptance requires locking to only hide
     * the navigation bar, not affect skipping. Ticket #20 changed to the callback-style onSkip(page); this test verifies the callback still fires while locked.
     */
    @Test
    fun skipButtonWorksWhileLocked() {
        var skippedPage: Int? = null
        composeDrillPager(locked = true, onSkip = { page -> skippedPage = page })

        // Start: first card (page=0)
        composeRule.onNodeWithText("apple").assertIsDisplayed()
        // Clicking "skip" while locked still triggers the callback
        composeRule.onNodeWithText("跳过").performClick()
        composeRule.waitForIdle()
        assertThat(skippedPage).isEqualTo(0)
    }

    /**
     * Ticket #20: the skip button callback receives the current page number (pagerState.currentPage).
     * This is what ViewModel.skipCurrentWord uses to locate the word to skip. Verified with a captured callback:
     * tap skip on the first card and the callback receives page=0.
     */
    @Test
    fun skipButton_passesCurrentPageToCallback() {
        var skippedPage: Int? = null
        composeDrillPager(onSkip = { page -> skippedPage = page })

        // Tap skip on the first card (page=0)
        composeRule.onNodeWithText("跳过").performClick()
        composeRule.waitForIdle()
        assertThat(skippedPage).isEqualTo(0)
    }

    /**
     * issue #1: in review-book mode a "restore" button shows (replacing "skip"); tapping it triggers onRestore instead of onSkip.
     * Regression: previously the review book also had a "skip" button, and tapping it also marked the word as skipped in the review book, permanently losing the word.
     */
    @Test
    fun reviewBook_showsRestoreButton_notSkip() {
        var skipCalled = false
        var restoreCalled = false
        composeDrillPager(
            isReviewBook = true,
            onSkip = { skipCalled = true },
            onRestore = { restoreCalled = true },
        )

        // Review book shows "restore" and not "skip"
        composeRule.onNodeWithText("恢复").assertIsDisplayed()
        composeRule.onNodeWithText("跳过").assertDoesNotExist()

        // Tapping "restore" triggers onRestore, not onSkip
        composeRule.onNodeWithText("恢复").performClick()
        composeRule.waitForIdle()
        assertThat(restoreCalled).isTrue()
        assertThat(skipCalled).isFalse()
    }

    /**
     * issue #1: a normal book still shows "skip" (regression guard, making sure the isReviewBook branch did not break normal books).
     */
    @Test
    fun normalBook_showsSkipButton_notRestore() {
        composeDrillPager(isReviewBook = false)

        composeRule.onNodeWithText("跳过").assertIsDisplayed()
        composeRule.onNodeWithText("恢复").assertDoesNotExist()
    }

    /**
     * #26: an overly long word book name must not squeeze out the skip / lock buttons on the right.
     *
     * Renders DrillPager with an extra-long word book name (far wider than the screen) and verifies:
     * - the skip button is still visible and clickable
     * - the node for the lock button's contentDescription ("lock") still exists
     * - the counter "1 / 3" still shows
     *
     * We verified the visual wrapping by hand on an emulator (screenshot); here we only verify node existence (not pushed out of the tree).
     */
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
                    )
                }
            }
        }
        composeRule.waitForIdle()

        // Both right-side buttons still visible (skip = Text, lock = Icon contentDescription)
        composeRule.onNodeWithText("跳过").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("锁定").assertIsDisplayed()
        // Counter position unaffected
        composeRule.onNodeWithText("1 / 3").assertIsDisplayed()
        // The full word book name still renders (after wrapping the whole text node still exists, because Compose Text nodes keep the full string)
        composeRule.onNodeWithText(longName).assertIsDisplayed()
    }
}
