package com.github.chsiching.worddrill.ui.drill

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Test

/** Virtual-time tests for the auto-advance state machine (spec #36 timing matrix). */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class AutoAdvanceTest {

    private fun TestScope.machine(dwellMillis: Long = 5_000L): AutoAdvance =
        AutoAdvance(backgroundScope) { dwellMillis }

    @Test
    fun doubleTap_startsPlaybackFromZero() = runTest {
        val machine = machine()

        machine.toggle(page = 0, deckSize = 3)

        assertThat(machine.state.value.phase).isEqualTo(AutoAdvancePhase.Playing)
        assertThat(machine.state.value.elapsedFraction).isWithin(0.001f).of(0f)
    }

    @Test
    fun pauseFreezesElapsed_resumeContinuesFromFrozenFraction() = runTest {
        val machine = machine()

        machine.toggle(page = 0, deckSize = 3)
        advanceTimeBy(2_000)
        runCurrent()
        machine.toggle(page = 0, deckSize = 3) // pause
        assertThat(machine.state.value.phase).isEqualTo(AutoAdvancePhase.Paused)

        // No time passes for playback while paused: the fraction stays frozen.
        advanceTimeBy(3_000)
        runCurrent()
        assertThat(machine.state.value.elapsedFraction).isWithin(0.001f).of(0.4f)

        // Resuming continues from the frozen fraction, not from zero.
        machine.toggle(page = 0, deckSize = 3)
        assertThat(machine.state.value.phase).isEqualTo(AutoAdvancePhase.Playing)
        advanceTimeBy(1_000)
        runCurrent()
        assertThat(machine.state.value.elapsedFraction).isWithin(0.001f).of(0.6f)
    }

    @Test
    fun emptyDeck_doubleTapIsInert() = runTest {
        val machine = machine()

        machine.toggle(page = 0, deckSize = 0)

        assertThat(machine.state.value.phase).isEqualTo(AutoAdvancePhase.Idle)
    }

    @Test
    fun dwellElapsed_emitsAdvanceToNextPage_andSettleRestartsCountdown() = runTest {
        val machine = machine()

        machine.toggle(page = 0, deckSize = 3)
        advanceTimeBy(5_000)
        runCurrent()

        // The command for the screen: animate-scroll one page forward.
        assertThat(withTimeoutOrNull(1_000) { machine.advance.first() }).isEqualTo(1)
        // Playing continues; the hairline rests full until the flip's settle lands.
        assertThat(machine.state.value.phase).isEqualTo(AutoAdvancePhase.Playing)
        assertThat(machine.state.value.elapsedFraction).isWithin(0.001f).of(1f)

        machine.onSettled(page = 1, deckSize = 3)
        assertThat(machine.state.value.phase).isEqualTo(AutoAdvancePhase.Playing)
        assertThat(machine.state.value.elapsedFraction).isWithin(0.001f).of(0f)
        advanceTimeBy(1_000)
        runCurrent()
        assertThat(machine.state.value.elapsedFraction).isWithin(0.001f).of(0.2f)
    }

    @Test
    fun lastCardDwell_showsCompleteBeat_thenStopsPlayback_noLoop() = runTest {
        val machine = machine()

        machine.toggle(page = 2, deckSize = 3)
        advanceTimeBy(5_000)
        runCurrent()

        // The dwell finished: the full-width frame is on screen for a visible beat,
        // not instantly overwritten by the stop.
        assertThat(machine.state.value.phase).isEqualTo(AutoAdvancePhase.Playing)
        assertThat(machine.state.value.elapsedFraction).isWithin(0.001f).of(1f)

        // After the hold the fill duty is complete, playback stops, the line disappears.
        advanceTimeBy(machine.completeHoldMillis)
        runCurrent()
        assertThat(machine.state.value.phase).isEqualTo(AutoAdvancePhase.Idle)
        assertThat(machine.state.value.elapsedFraction).isWithin(0.001f).of(0f)
        assertThat(withTimeoutOrNull(1_000) { machine.advance.first() }).isNull()
    }

    @Test
    fun singleCardDeck_showsCompleteBeat_thenStopsWhenOnlyCardCompletes() = runTest {
        val machine = machine()

        machine.toggle(page = 0, deckSize = 1)
        advanceTimeBy(5_000)
        runCurrent()

        assertThat(machine.state.value.phase).isEqualTo(AutoAdvancePhase.Playing)
        assertThat(machine.state.value.elapsedFraction).isWithin(0.001f).of(1f)

        advanceTimeBy(machine.completeHoldMillis)
        runCurrent()
        assertThat(machine.state.value.phase).isEqualTo(AutoAdvancePhase.Idle)
        assertThat(withTimeoutOrNull(1_000) { machine.advance.first() }).isNull()
    }

    @Test
    fun manualSwipeWhilePlaying_repositions_resetsCountdown_keepsPlaying() = runTest {
        val machine = machine()

        machine.toggle(page = 0, deckSize = 3)
        advanceTimeBy(3_000)
        runCurrent()
        assertThat(machine.state.value.elapsedFraction).isWithin(0.001f).of(0.6f)

        // A backward manual swipe: the deck repositions without pausing.
        machine.onSettled(page = 0, deckSize = 3)
        assertThat(machine.state.value.phase).isEqualTo(AutoAdvancePhase.Playing)
        assertThat(machine.state.value.elapsedFraction).isWithin(0.001f).of(0f)

        // The next advance starts from the repositioned page.
        advanceTimeBy(5_000)
        runCurrent()
        assertThat(withTimeoutOrNull(1_000) { machine.advance.first() }).isEqualTo(1)
    }

    @Test
    fun manualSwipeWhilePaused_zeroesElapsedForTheNewCard() = runTest {
        val machine = machine()

        machine.toggle(page = 0, deckSize = 3)
        advanceTimeBy(2_000)
        runCurrent()
        machine.toggle(page = 0, deckSize = 3) // pause at 0.4

        machine.onSettled(page = 1, deckSize = 3)
        assertThat(machine.state.value.phase).isEqualTo(AutoAdvancePhase.Paused)
        assertThat(machine.state.value.elapsedFraction).isWithin(0.001f).of(0f)

        machine.toggle(page = 1, deckSize = 3) // resume starts the new card fresh
        advanceTimeBy(2_500)
        runCurrent()
        assertThat(machine.state.value.elapsedFraction).isWithin(0.001f).of(0.5f)
    }

    @Test
    fun deckChangedWhilePlaying_skipKeepsPlayingOnNewCurrentCard() = runTest {
        val machine = machine()

        machine.toggle(page = 0, deckSize = 3)
        advanceTimeBy(2_000)
        runCurrent()

        // Skip shrinks the deck; the next card sits under the same index.
        machine.onDeckChanged(page = 0, deckSize = 2)
        assertThat(machine.state.value.phase).isEqualTo(AutoAdvancePhase.Playing)
        assertThat(machine.state.value.elapsedFraction).isWithin(0.001f).of(0f)

        advanceTimeBy(5_000)
        runCurrent()
        assertThat(withTimeoutOrNull(1_000) { machine.advance.first() }).isEqualTo(1)
    }

    @Test
    fun stop_returnsToIdleAndAFreshStartBeginsAtZero() = runTest {
        val machine = machine()

        machine.toggle(page = 0, deckSize = 3)
        advanceTimeBy(3_000)
        runCurrent()
        machine.stop()
        assertThat(machine.state.value.phase).isEqualTo(AutoAdvancePhase.Idle)

        machine.toggle(page = 1, deckSize = 3)
        advanceTimeBy(1_000)
        runCurrent()
        assertThat(machine.state.value.elapsedFraction).isWithin(0.001f).of(0.2f)
    }

    @Test
    fun stop_discardsAPendingAdvanceCommand() = runTest {
        val machine = machine()

        // The dwell elapsed and the command is buffered but not yet executed when the
        // playback context goes away (e.g. the book switched): the stale command must
        // not flip a fresh pager later.
        machine.toggle(page = 0, deckSize = 3)
        advanceTimeBy(5_000)
        runCurrent()
        machine.stop()

        assertThat(withTimeoutOrNull(1_000) { machine.advance.first() }).isNull()
    }
}
