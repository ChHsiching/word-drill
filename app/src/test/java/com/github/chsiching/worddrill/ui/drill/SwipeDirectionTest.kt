package com.github.chsiching.worddrill.ui.drill

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SwipeDirectionTest {

    @Test
    fun forwardSwipe_increasesPage_logsSwipe() {
        assertThat(shouldLogSwipe(previousPage = 0, currentPage = 1)).isTrue()
    }

    @Test
    fun forwardSwipe_multipleSteps_logsSwipe() {
        assertThat(shouldLogSwipe(previousPage = 2, currentPage = 5)).isTrue()
    }

    @Test
    fun backwardSwipe_decreasesPage_doesNotLog() {
        assertThat(shouldLogSwipe(previousPage = 2, currentPage = 1)).isFalse()
    }

    @Test
    fun backwardSwipe_toStart_doesNotLog() {
        assertThat(shouldLogSwipe(previousPage = 1, currentPage = 0)).isFalse()
    }

    @Test
    fun noMovement_samePage_doesNotLog() {
        assertThat(shouldLogSwipe(previousPage = 0, currentPage = 0)).isFalse()
    }

    @Test
    fun stuckAtFirst_samePage_doesNotLog() {
        assertThat(shouldLogSwipe(previousPage = 0, currentPage = 0)).isFalse()
    }

    @Test
    fun stuckAtLast_samePage_doesNotLog() {
        assertThat(shouldLogSwipe(previousPage = 9, currentPage = 9)).isFalse()
    }
}
