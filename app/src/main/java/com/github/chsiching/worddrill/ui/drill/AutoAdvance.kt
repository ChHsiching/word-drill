package com.github.chsiching.worddrill.ui.drill

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/** Phase of the auto-advance playback mode (GLOSSARY.md, Auto-advance). */
enum class AutoAdvancePhase { Idle, Playing, Paused }

/**
 * Playback state surfaced to the drill screen: the phase decides whether the bottom
 * hairline exists, and [elapsedFraction] (0..1) is how far the current card's dwell has
 * progressed — the hairline's fill width.
 */
data class AutoAdvanceState(
    val phase: AutoAdvancePhase = AutoAdvancePhase.Idle,
    val elapsedFraction: Float = 0f,
)

/**
 * Auto-advance state machine, owned and exposed by [DrillViewModel]. It never touches
 * the pager: when a card's dwell completes it emits the next page on [advance], and the
 * screen executes that with an animated scroll whose settle flows through the existing
 * [DrillViewModel.onPageSettled] path. The countdown runs in the given scope as
 * delay-based steps so virtual-time JVM tests (runTest) can drive the whole matrix.
 *
 * Elapsed time belongs to the current card: pausing freezes it, resuming continues it,
 * and any reposition (manual swipe settle, skip/restore reload) restarts it at zero
 * without leaving the Playing phase.
 */
internal class AutoAdvance(
    private val scope: CoroutineScope,
    private val dwellMillis: suspend () -> Long,
) {

    /** Countdown granularity. Dwell values are 0.5 s steps, so every dwell is a multiple. */
    private val tickMillis = 50L

    private val _state = MutableStateFlow(AutoAdvanceState())
    val state: StateFlow<AutoAdvanceState> = _state.asStateFlow()

    private val _advance = Channel<Int>(Channel.UNLIMITED)

    /** Pages the screen should animate to; each one's settle closes the loop via [onSettled]. */
    val advance: Flow<Int> = _advance.receiveAsFlow()

    private var page = 0
    private var deckSize = 0
    private var elapsedMillis = 0L
    private var cardDwellMillis = 0L
    private var countdown: Job? = null

    /**
     * Double-tap: Idle starts a fresh countdown, Playing freezes it (Paused), Paused
     * resumes it from the frozen elapsed time. Inert on an empty deck.
     */
    fun toggle(page: Int, deckSize: Int) {
        this.page = page
        this.deckSize = deckSize
        when (_state.value.phase) {
            AutoAdvancePhase.Idle -> {
                if (deckSize <= 0) return
                elapsedMillis = 0
                startCountdown()
            }
            AutoAdvancePhase.Playing -> {
                countdown?.cancel()
                countdown = null
                _state.value = _state.value.copy(phase = AutoAdvancePhase.Paused)
            }
            AutoAdvancePhase.Paused -> startCountdown()
        }
    }

    /**
     * A page settled (manual swipe or an auto-advance flip): repositions playback on
     * that page. While playing the countdown restarts; playback never pauses.
     */
    fun onSettled(page: Int, deckSize: Int) {
        reposition(page, deckSize)
    }

    /**
     * The deck reloaded under an unchanged pager index (skip/restore): the word under
     * the index changed, so the countdown restarts on the new current card.
     */
    fun onDeckChanged(page: Int, deckSize: Int) {
        reposition(page, deckSize)
    }

    /** Returns to Idle with no elapsed time (deck end, deck gone, or leaving playback). */
    fun stop() {
        countdown?.cancel()
        countdown = null
        elapsedMillis = 0
        cardDwellMillis = 0
        _state.value = AutoAdvanceState()
    }

    private fun reposition(page: Int, deckSize: Int) {
        this.page = page
        this.deckSize = deckSize
        elapsedMillis = 0
        when (_state.value.phase) {
            AutoAdvancePhase.Playing -> startCountdown()
            AutoAdvancePhase.Paused ->
                _state.value = _state.value.copy(elapsedFraction = 0f)
            AutoAdvancePhase.Idle -> Unit
        }
    }

    /** Starts (or resumes) the countdown; already-elapsed time is preserved. */
    private fun startCountdown() {
        countdown?.cancel()
        _state.value = _state.value.copy(phase = AutoAdvancePhase.Playing)
        // Emit the starting fraction now, not at the coroutine's first tick, so a
        // reposition resets the hairline to zero the moment it happens.
        emitFraction()
        countdown = scope.launch {
            cardDwellMillis = dwellMillis()
            emitFraction()
            while (true) {
                delay(tickMillis)
                val next = elapsedMillis + tickMillis
                if (next >= cardDwellMillis) {
                    elapsedMillis = cardDwellMillis
                    emitFraction()
                    onDwellComplete()
                    return@launch
                }
                elapsedMillis = next
                emitFraction()
            }
        }
    }

    private fun onDwellComplete() {
        if (page < deckSize - 1) {
            _advance.trySend(page + 1)
            // The countdown for the next card starts when the screen reports the
            // flip's settle through onSettled; until then the hairline rests full.
        } else {
            // Deck end: the fill duty is complete, playback stops, the line disappears.
            stop()
        }
    }

    private fun emitFraction() {
        val fraction =
            if (cardDwellMillis <= 0) 0f
            else (elapsedMillis.toFloat() / cardDwellMillis).coerceIn(0f, 1f)
        _state.value = _state.value.copy(elapsedFraction = fraction)
    }
}
