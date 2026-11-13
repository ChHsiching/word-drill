package com.github.chsiching.worddrill.ui.drill

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Unit tests for the pure function behind right-swipe (forward) counting. Spec acceptance:
 * - Swiping right to the next card counts +1 (a swipe_log row must be recorded)
 * - Swiping left to the previous card does not count
 * - Reaching the edge (already parked at a boundary, page unchanged) does not count
 *
 * The logic is a pure function so JVM tests can cover it, with no Android dependency and no Pager gesture coupling.
 * Pages are 0-based: the page index increases on a right (forward) swipe.
 */
class SwipeDirectionTest {

    // ---- Right swipe (forward): page increases, count it ----

    @Test
    fun forwardSwipe_increasesPage_logsSwipe() {
        // 0 to 1: right swipe to the next card, must count
        assertThat(shouldLogSwipe(previousPage = 0, currentPage = 1)).isTrue()
    }

    @Test
    fun forwardSwipe_multipleSteps_logsSwipe() {
        // Skipping pages forward also counts (an extreme fast fling). Any page increase is the forward direction.
        assertThat(shouldLogSwipe(previousPage = 2, currentPage = 5)).isTrue()
    }

    // ---- Left swipe (looking back): page decreases, do not count ----

    @Test
    fun backwardSwipe_decreasesPage_doesNotLog() {
        // 2 to 1: left swipe back to the previous card, do not count
        assertThat(shouldLogSwipe(previousPage = 2, currentPage = 1)).isFalse()
    }

    @Test
    fun backwardSwipe_toStart_doesNotLog() {
        // 1 to 0: left swipe to the first card, still the looking-back direction, do not count
        assertThat(shouldLogSwipe(previousPage = 1, currentPage = 0)).isFalse()
    }

    // ---- At the edge / no movement: page unchanged, do not count (includes swiping again while parked on the first or last card) ----

    @Test
    fun noMovement_samePage_doesNotLog() {
        // Page unchanged (still swiping at a boundary, or repeated settled callbacks): do not count
        assertThat(shouldLogSwipe(previousPage = 0, currentPage = 0)).isFalse()
    }

    @Test
    fun stuckAtFirst_samePage_doesNotLog() {
        // Already on the first card (0) and swiping forward, the Pager blocks it and the page stays unchanged: do not count
        assertThat(shouldLogSwipe(previousPage = 0, currentPage = 0)).isFalse()
    }

    @Test
    fun stuckAtLast_samePage_doesNotLog() {
        // Already on the last card (N) and swiping on, the Pager blocks it and the page stays unchanged: do not count
        assertThat(shouldLogSwipe(previousPage = 9, currentPage = 9)).isFalse()
    }
}
