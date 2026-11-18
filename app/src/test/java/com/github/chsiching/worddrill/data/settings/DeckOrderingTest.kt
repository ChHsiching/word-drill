package com.github.chsiching.worddrill.data.settings

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DeckOrderingTest {

    @Test
    fun fromStorageName_returnsAlphabetical_whenNameIsAlphabetical() {
        assertThat(DeckOrdering.fromStorageName("ALPHABETICAL"))
            .isEqualTo(DeckOrdering.ALPHABETICAL)
    }

    @Test
    fun fromStorageName_returnsShuffled_whenNameIsShuffled() {
        assertThat(DeckOrdering.fromStorageName("SHUFFLED")).isEqualTo(DeckOrdering.SHUFFLED)
    }

    @Test
    fun fromStorageName_defaultsToAlphabetical_whenNull() {
        assertThat(DeckOrdering.fromStorageName(null)).isEqualTo(DeckOrdering.ALPHABETICAL)
    }

    @Test
    fun fromStorageName_defaultsToAlphabetical_whenUnknown() {
        assertThat(DeckOrdering.fromStorageName("garbage")).isEqualTo(DeckOrdering.ALPHABETICAL)
        assertThat(DeckOrdering.fromStorageName("")).isEqualTo(DeckOrdering.ALPHABETICAL)
        assertThat(DeckOrdering.fromStorageName("alphabetical")).isEqualTo(DeckOrdering.ALPHABETICAL)
    }
}
