package com.github.chsiching.worddrill.ui.drill

import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.doubleClick
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.github.chsiching.worddrill.data.ProgressResetter
import com.github.chsiching.worddrill.data.local.WordDrillDatabase
import com.github.chsiching.worddrill.data.local.entity.Book
import com.github.chsiching.worddrill.data.local.entity.BookWord
import com.github.chsiching.worddrill.data.local.entity.Sense
import com.github.chsiching.worddrill.data.settings.SettingsRepository
import com.github.chsiching.worddrill.ui.DeckSequenceState
import com.github.chsiching.worddrill.ui.theme.WordDrillTheme
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Instrumented lifecycle tests for auto-advance (#39): playback stops when the drill
 *  route is left or the app reaches ON_STOP, re-entering starts idle, the
 *  keep-screen-on window flag is held only while playing, edge decks behave, and the
 *  lock toggle is orthogonal to playback. */
@RunWith(AndroidJUnit4::class)
class DrillLifecycleTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val context: android.content.Context
        get() = ApplicationProvider.getApplicationContext()

    private lateinit var db: WordDrillDatabase
    private lateinit var settings: SettingsRepository

    /** ViewModels built for this test; no ViewModelStore ever clears them, so their
     *  scopes must be cancelled before the database closes. */
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
        viewModels.forEach { it.viewModelScope.cancel() }
        // Drain in-flight Room queries before the pool closes (the 8c3567a convention),
        // and restore the dwell default so later classes in this process see 5 s.
        delay(200)
        db.close()
        settings.setDwellInterval(5f)
        Unit
    }

    private fun keepScreenOnHeld(): Boolean =
        (composeRule.activity.window.attributes.flags and
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) != 0

    /** Seeds [bookId] as the current book with [wordTexts] and returns its ViewModel.
     *  Resume keys and the current-book id live in the process-wide DataStore while
     *  in-memory database ids restart per test, so every test owns a unique book-id
     *  range (order-independent). */
    private suspend fun seedViewModel(bookId: Long, name: String, vararg wordTexts: String): DrillViewModel {
        db.bookDao().insert(Book(bookId = bookId, name = name, isPreset = false))
        wordTexts.forEach { text ->
            val wordId = db.wordDao().findOrInsertWord(text, null)
            db.wordDao().insertSense(Sense(wordId = wordId, pos = "n.", meaning = "测试释义"))
            db.bookDao().linkBookWord(BookWord(bookId = bookId, wordId = wordId))
        }
        settings.setCurrentBookId(bookId)
        return DrillViewModel(
            db = db,
            bookDao = db.bookDao(),
            wordDao = db.wordDao(),
            swipeLogDao = db.swipeLogDao(),
            settings = settings,
            overlayState = AutoAdvanceOverlayState(),
            progressResetter = ProgressResetter(db, db.bookDao(), settings),
            deckSequence = DeckSequenceState(),
        ).also { viewModels.add(it) }
    }

    /** Hosts [content] under the dwell hairline bound to the ViewModel's playback
     *  state, mirroring the navigation root's overlay drawing: the drill screen no
     *  longer renders the line itself. */
    @Composable
    private fun WithHairlineOverlay(vm: DrillViewModel, content: @Composable () -> Unit) {
        Box {
            content()
            val playback by vm.playback.collectAsState()
            if (playback.phase != AutoAdvancePhase.Idle) {
                AutoAdvanceHairline(
                    elapsedFraction = playback.elapsedFraction,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }

    @Test
    fun leavingTheDrillRoute_stopsPlayback_reenteringStartsIdle() {
        val vm = runBlocking { seedViewModel(939_101L, "生命周期词书", "apple", "balance") }

        // A tab switch disposes the drill destination's composition while the nav
        // backstack retains its ViewModel; a hoisted flag reproduces exactly that
        // within a single setContent.
        val showDrill = mutableStateOf(true)
        composeRule.setContent {
            WordDrillTheme {
                Surface {
                    WithHairlineOverlay(vm) {
                        if (showDrill.value) DrillScreen(onSwitchBook = {}, viewModel = vm)
                    }
                }
            }
        }
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithText("apple").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithText("apple").performTouchInput { doubleClick() }
        composeRule.waitForIdle()
        assertThat(vm.playback.value.phase).isEqualTo(AutoAdvancePhase.Playing)
        composeRule.onNodeWithTag(AutoAdvanceHairlineTag).assertIsDisplayed()

        // Leaving the drill route mid-playback: the retained ViewModel stops.
        composeRule.runOnUiThread { showDrill.value = false }
        composeRule.waitForIdle()
        assertThat(vm.playback.value.phase).isEqualTo(AutoAdvancePhase.Idle)

        // Returning always starts idle: no hairline, playback off.
        composeRule.runOnUiThread { showDrill.value = true }
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithText("apple").fetchSemanticsNodes().isNotEmpty()
        }
        assertThat(vm.playback.value.phase).isEqualTo(AutoAdvancePhase.Idle)
        composeRule.onNodeWithTag(AutoAdvanceHairlineTag).assertDoesNotExist()
    }

    @Test
    fun keepScreenOn_heldWhilePlaying_releasedOnPauseAndLeave() {
        val vm = runBlocking { seedViewModel(939_103L, "常亮词书", "apple", "balance") }
        composeRule.setContent {
            WordDrillTheme {
                Surface {
                    WithHairlineOverlay(vm) {
                        DrillScreen(onSwitchBook = {}, viewModel = vm)
                    }
                }
            }
        }
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithText("apple").fetchSemanticsNodes().isNotEmpty()
        }
        assertThat(keepScreenOnHeld()).isFalse()

        // Playing holds the window flag: a hands-free session doesn't black out.
        composeRule.onNodeWithText("apple").performTouchInput { doubleClick() }
        composeRule.waitForIdle()
        assertThat(vm.playback.value.phase).isEqualTo(AutoAdvancePhase.Playing)
        assertThat(keepScreenOnHeld()).isTrue()

        // Pausing releases it even though the frozen hairline stays visible.
        composeRule.onNodeWithText("apple").performTouchInput { doubleClick() }
        composeRule.waitForIdle()
        assertThat(vm.playback.value.phase).isEqualTo(AutoAdvancePhase.Paused)
        assertThat(keepScreenOnHeld()).isFalse()
        composeRule.onNodeWithTag(AutoAdvanceHairlineTag).assertIsDisplayed()
    }

    @Test
    fun appOnStop_stopsPlayback_foregroundingDoesNotResume() {
        val vm = runBlocking { seedViewModel(939_102L, "后台词书", "apple", "balance") }
        composeRule.setContent {
            WordDrillTheme {
                Surface {
                    WithHairlineOverlay(vm) {
                        DrillScreen(onSwitchBook = {}, viewModel = vm)
                    }
                }
            }
        }
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithText("apple").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithText("apple").performTouchInput { doubleClick() }
        composeRule.waitForIdle()
        assertThat(vm.playback.value.phase).isEqualTo(AutoAdvancePhase.Playing)
        assertThat(keepScreenOnHeld()).isTrue()

        // Backgrounding the app (ON_STOP, activity still alive): cards must not flip
        // unattended, and the keep-awake flag goes with the playback that held it.
        composeRule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        assertThat(vm.playback.value.phase).isEqualTo(AutoAdvancePhase.Idle)
        assertThat(keepScreenOnHeld()).isFalse()

        // Foregrounding never resumes playback; the screen returns idle.
        composeRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithText("apple").fetchSemanticsNodes().isNotEmpty()
        }
        assertThat(vm.playback.value.phase).isEqualTo(AutoAdvancePhase.Idle)
        composeRule.onNodeWithTag(AutoAdvanceHairlineTag).assertDoesNotExist()
    }

    @Test
    fun emptyDeck_doubleTapIsInert_noHairlineEver() {
        // A book with no cards: the mode can't start meaninglessly.
        val vm = runBlocking { seedViewModel(939_104L, "空生命周期书") }
        composeRule.setContent {
            WordDrillTheme {
                Surface {
                    WithHairlineOverlay(vm) {
                        DrillScreen(onSwitchBook = {}, viewModel = vm)
                    }
                }
            }
        }
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithText("这个词书是空的").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithText("这个词书是空的").performTouchInput { doubleClick() }
        composeRule.waitForIdle()

        assertThat(vm.playback.value.phase).isEqualTo(AutoAdvancePhase.Idle)
        composeRule.onNodeWithTag(AutoAdvanceHairlineTag).assertDoesNotExist()
    }

    @Test
    fun singleCardDeck_startsThenStopsWhenItsDwellCompletes() {
        runBlocking { settings.setDwellInterval(1f) }
        val vm = runBlocking { seedViewModel(939_105L, "单卡词书", "apple") }
        composeRule.setContent {
            WordDrillTheme {
                Surface {
                    WithHairlineOverlay(vm) {
                        DrillScreen(onSwitchBook = {}, viewModel = vm)
                    }
                }
            }
        }
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithText("1 / 1").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithText("apple").performTouchInput { doubleClick() }
        composeRule.waitForIdle()
        assertThat(vm.playback.value.phase).isEqualTo(AutoAdvancePhase.Playing)
        composeRule.onNodeWithTag(AutoAdvanceHairlineTag).assertIsDisplayed()

        // The only card's dwell completes and playback stops: no next page to flip
        // to, no timer left spinning.
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithTag(AutoAdvanceHairlineTag).fetchSemanticsNodes().isEmpty()
        }
        assertThat(vm.playback.value.phase).isEqualTo(AutoAdvancePhase.Idle)
        composeRule.onNodeWithText("1 / 1").assertIsDisplayed()
        assertThat(keepScreenOnHeld()).isFalse()
    }

    @Test
    fun lockingDuringPlayback_changesNothingAboutPlaybackHairlineOrDoubleTap() {
        val vm = runBlocking { seedViewModel(939_106L, "锁定词书", "apple", "balance", "circle") }
        val locked = mutableStateOf(false)
        composeRule.setContent {
            WordDrillTheme {
                Surface {
                    WithHairlineOverlay(vm) {
                        DrillScreen(
                            locked = locked.value,
                            onToggleLock = { locked.value = !locked.value },
                            onSwitchBook = {},
                            viewModel = vm,
                        )
                    }
                }
            }
        }
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithText("apple").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithText("apple").performTouchInput { doubleClick() }
        composeRule.waitForIdle()
        assertThat(vm.playback.value.phase).isEqualTo(AutoAdvancePhase.Playing)

        // Hiding the navigation mid-playback is a pure immersion toggle: playback
        // and the hairline are untouched.
        composeRule.onNodeWithContentDescription("锁定").performClick()
        composeRule.waitForIdle()
        assertThat(vm.playback.value.phase).isEqualTo(AutoAdvancePhase.Playing)
        composeRule.onNodeWithTag(AutoAdvanceHairlineTag).assertIsDisplayed()

        // The double-tap gesture still works with the navigation hidden.
        composeRule.onNodeWithText("apple").performTouchInput { doubleClick() }
        composeRule.waitForIdle()
        assertThat(vm.playback.value.phase).isEqualTo(AutoAdvancePhase.Paused)
        composeRule.onNodeWithTag(AutoAdvanceHairlineTag).assertIsDisplayed()

        // Unlocking changes nothing about playback either.
        composeRule.onNodeWithContentDescription("解锁").performClick()
        composeRule.waitForIdle()
        assertThat(vm.playback.value.phase).isEqualTo(AutoAdvancePhase.Paused)
        composeRule.onNodeWithTag(AutoAdvanceHairlineTag).assertIsDisplayed()
    }
}
