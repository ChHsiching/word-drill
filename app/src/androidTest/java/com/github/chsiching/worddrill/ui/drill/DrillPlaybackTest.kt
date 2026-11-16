package com.github.chsiching.worddrill.ui.drill

import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.test.doubleClick
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Instrumented playback tests (spec #36 core seam): double-tap toggles, the flip at
 *  a 1 s dwell executes through the pager's existing settle path, and the hairline
 *  follows the playback phase. */
@RunWith(AndroidJUnit4::class)
class DrillPlaybackTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var db: WordDrillDatabase
    private lateinit var machine: AutoAdvance
    private val machineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /** Settle events seen by the pager callback, mirroring the settle log the
     *  production ViewModel writes from the same events. */
    private val settleLog = mutableListOf<Pair<Int, Int>>()

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, WordDrillDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        seedData()
    }

    @After
    fun tearDown() = runBlocking {
        machineScope.cancel()
        // Drain in-flight Room queries before closing (the 8c3567a convention): the
        // composition is released only after this method, so recompositions may still
        // hold cards() queries against this database, and a close that races them
        // throws "connection pool has been closed" into whatever test runs next.
        delay(200)
        db.close()
        Unit
    }

    private fun seedData() = runBlocking {
        val bookId = db.bookDao().insert(Book(name = "测试词书", isPreset = false))
        val w1 = db.wordDao().insert(Word(text = "apple"))
        val w2 = db.wordDao().insert(Word(text = "balance"))
        val w3 = db.wordDao().insert(Word(text = "circle"))
        db.wordDao().insertSense(Sense(wordId = w1, pos = "n.", meaning = "苹果"))
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

    /** Composes the pager wired to a real AutoAdvance machine exactly the way the
     *  ViewModel wires it: double-tap toggles, the advance flow drives animated
     *  scrolls, and every settle feeds the machine back. */
    private fun composePlaybackPager(dwellMillis: Long = 1_000L) {
        machine = AutoAdvance(machineScope) { dwellMillis }
        val deckSize = cards().size
        composeRule.setContent {
            WordDrillTheme {
                Surface {
                    val playback by machine.state.collectAsState()
                    DrillPager(
                        bookName = "测试词书",
                        cards = cards(),
                        onPageSettled = { previous, current ->
                            settleLog += previous to current
                            machine.onSettled(current, deckSize)
                        },
                        onSkip = {},
                        isReviewBook = false,
                        onRestore = {},
                        locked = false,
                        onToggleLock = {},
                        hidePhonetic = false,
                        onToggleAutoAdvance = { page -> machine.toggle(page, deckSize) },
                        playbackState = playback,
                        advance = machine.advance,
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun doubleTap_startsThenPausesPlayback_hairlineFollowsPhase() {
        composePlaybackPager()

        composeRule.onNodeWithTag(AutoAdvanceHairlineTag).assertDoesNotExist()

        composeRule.onNodeWithText("apple").performTouchInput { doubleClick() }
        composeRule.waitForIdle()
        assertThat(machine.state.value.phase).isEqualTo(AutoAdvancePhase.Playing)
        composeRule.onNodeWithTag(AutoAdvanceHairlineTag).assertIsDisplayed()

        composeRule.onNodeWithText("apple").performTouchInput { doubleClick() }
        composeRule.waitForIdle()
        assertThat(machine.state.value.phase).isEqualTo(AutoAdvancePhase.Paused)
        // Paused keeps the hairline, frozen in place.
        composeRule.onNodeWithTag(AutoAdvanceHairlineTag).assertIsDisplayed()
    }

    @Test
    fun dwellElapsed_flipsToNextCard_throughExistingSettlePath() {
        composePlaybackPager(dwellMillis = 1_000L)

        composeRule.onNodeWithText("apple").performTouchInput { doubleClick() }
        composeRule.waitForIdle()

        // After the 1 s dwell the deck flips by itself, the counter follows, and the
        // flip settled through the same settle callback a manual swipe uses.
        composeRule.waitUntil(10_000L) {
            composeRule.onAllNodesWithText("2 / 3").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("balance").assertIsDisplayed()
        assertThat(settleLog).contains(0 to 1)
        assertThat(machine.state.value.phase).isEqualTo(AutoAdvancePhase.Playing)
    }

    @Test
    fun lastCardDwellEndsPlayback_hairlineDisappears_noLoop() {
        composePlaybackPager(dwellMillis = 1_000L)

        // Move to the last card manually; still no hairline while idle.
        repeat(2) {
            composeRule.onRoot().performTouchInput { swipeLeft() }
            composeRule.waitForIdle()
        }
        composeRule.onNodeWithText("3 / 3").assertIsDisplayed()
        composeRule.onNodeWithTag(AutoAdvanceHairlineTag).assertDoesNotExist()

        composeRule.onNodeWithText("circle").performTouchInput { doubleClick() }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(AutoAdvanceHairlineTag).assertIsDisplayed()

        // The last card's dwell completes: playback stops and the line disappears.
        composeRule.waitUntil(10_000L) {
            composeRule.onAllNodesWithTag(AutoAdvanceHairlineTag).fetchSemanticsNodes().isEmpty()
        }
        assertThat(machine.state.value.phase).isEqualTo(AutoAdvancePhase.Idle)
        composeRule.onNodeWithText("3 / 3").assertIsDisplayed()
    }

    @Test
    fun singleTap_doesNotStartPlayback() {
        composePlaybackPager()

        composeRule.onNodeWithText("apple").performTouchInput { click() }
        composeRule.waitForIdle()

        assertThat(machine.state.value.phase).isEqualTo(AutoAdvancePhase.Idle)
        composeRule.onNodeWithTag(AutoAdvanceHairlineTag).assertDoesNotExist()
        composeRule.onNodeWithText("1 / 3").assertIsDisplayed()
    }

    @Test
    fun manualSwipeWhilePlaying_repositions_keepsPlaying_advancesFromNewPage() {
        composePlaybackPager(dwellMillis = 1_000L)

        composeRule.onNodeWithText("apple").performTouchInput { doubleClick() }
        composeRule.waitForIdle()
        assertThat(machine.state.value.phase).isEqualTo(AutoAdvancePhase.Playing)

        // A manual swipe within the dwell repositions without pausing; the countdown
        // restarts on the new card and auto-advances from there.
        composeRule.onRoot().performTouchInput { swipeLeft() }
        composeRule.waitForIdle()
        assertThat(machine.state.value.phase).isEqualTo(AutoAdvancePhase.Playing)
        composeRule.onNodeWithTag(AutoAdvanceHairlineTag).assertIsDisplayed()

        composeRule.waitUntil(10_000L) {
            composeRule.onAllNodesWithText("3 / 3").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("circle").assertIsDisplayed()
    }
}
