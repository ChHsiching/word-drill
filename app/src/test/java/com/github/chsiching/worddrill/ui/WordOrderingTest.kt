package com.github.chsiching.worddrill.ui

import com.github.chsiching.worddrill.data.local.WordWithSenses
import com.github.chsiching.worddrill.data.local.entity.Word
import com.google.common.truth.Truth.assertThat
import kotlin.random.Random
import org.junit.Test

class WordOrderingTest {

    private fun word(text: String) = WordWithSenses(word = Word(text = text), senses = emptyList())

    private fun wordWithId(id: Long, text: String) =
        WordWithSenses(word = Word(wordId = id, text = text), senses = emptyList())

    @Test
    fun alphabetical_interleavesMixedCase() {
        val ordered = WordOrdering.alphabetical(
            listOf(word("Zebra"), word("core"), word("Apple")),
        )
        assertThat(ordered.map { it.word.text }).containsExactly("Apple", "core", "Zebra").inOrder()
    }

    @Test
    fun alphabetical_caseTiesBreakOnRawText_regardlessOfInputOrder() {
        val forward = WordOrdering.alphabetical(listOf(word("Core"), word("core"), word("core")))
        val backward = WordOrdering.alphabetical(listOf(word("core"), word("core"), word("Core")))
        assertThat(forward.map { it.word.text }).containsExactly("Core", "core", "core").inOrder()
        assertThat(backward.map { it.word.text }).containsExactly("Core", "core", "core").inOrder()
    }

    @Test
    fun shuffled_sameSeed_isDeterministic() {
        val words = ('a'..'l').map { word(it.toString()) }
        val first = WordOrdering.shuffled(words, Random(42))
        val second = WordOrdering.shuffled(words, Random(42))
        assertThat(first.map { it.word.text }).isEqualTo(second.map { it.word.text })
    }

    @Test
    fun shuffled_differentSeed_reordersTheDeck() {
        val words = ('a'..'l').map { word(it.toString()) }
        val first = WordOrdering.shuffled(words, Random(1))
        val second = WordOrdering.shuffled(words, Random(2))
        assertThat(first.map { it.word.text }).isNotEqualTo(second.map { it.word.text })
    }

    @Test
    fun shuffled_permutesTheSameElements() {
        val words = listOf(word("apple"), word("balance"), word("circle"), word("delta"))
        val shuffled = WordOrdering.shuffled(words, Random(7))
        // A permutation keeps every card exactly once (deck ordering never adds,
        // drops or duplicates words).
        assertThat(shuffled.map { it.word.text })
            .containsExactly("apple", "balance", "circle", "delta")
    }

    @Test
    fun resumeIndex_landsOnTheSameWord_afterReshuffleMovesIndices() {
        val words = (1L..12L).map { wordWithId(it, "w$it") }
        val firstOpen = WordOrdering.shuffled(words, Random(3))
        val reshuffled = WordOrdering.shuffled(words, Random(9))
        val drilledAt = 4
        val savedWordId = firstOpen[drilledAt].word.wordId
        // Precondition: the reshuffle really moved the drilled word (issue #54,
        // resume stays keyed by word, never by index).
        val movedTo = reshuffled.indexOfFirst { it.word.wordId == savedWordId }
        assertThat(movedTo).isNotEqualTo(drilledAt)

        val resumedIndex = WordOrdering.resumeIndex(savedWordId, reshuffled)
        assertThat(resumedIndex).isEqualTo(movedTo)
        assertThat(reshuffled[resumedIndex].word.wordId).isEqualTo(savedWordId)
    }

    @Test
    fun resumeIndex_wordLeftTheDeck_fallsBackToFirstCard() {
        val words = listOf(wordWithId(1L, "apple"), wordWithId(2L, "balance"), wordWithId(3L, "circle"))
        assertThat(WordOrdering.resumeIndex(99L, words)).isEqualTo(0)
        assertThat(WordOrdering.resumeIndex(null, words)).isEqualTo(0)
    }

    @Test
    fun byDeckSequence_followsTheDecksCardSequence() {
        val words = listOf(
            wordWithId(1L, "apple"), wordWithId(2L, "balance"), wordWithId(3L, "circle"),
        )
        // The deck dealt circle first, then apple, then balance.
        val ordered = WordOrdering.byDeckSequence(words, listOf(3L, 1L, 2L))
        assertThat(ordered.map { it.word.text }).containsExactly("circle", "apple", "balance").inOrder()
    }

    @Test
    fun byDeckSequence_withoutASequence_fallsBackToAlphabetical() {
        val words = listOf(wordWithId(1L, "Zebra"), wordWithId(2L, "apple"), wordWithId(3L, "Bear"))
        val ordered = WordOrdering.byDeckSequence(words, null)
        assertThat(ordered.map { it.word.text }).containsExactly("apple", "Bear", "Zebra").inOrder()
    }

    @Test
    fun byDeckSequence_wordNotInDeckYet_goesToTheEnd() {
        val words = listOf(
            wordWithId(1L, "apple"), wordWithId(2L, "balance"),
            wordWithId(3L, "circle"), wordWithId(4L, "delta"),
        )
        // delta was added after the book was opened, so the deck has not dealt it;
        // it shows at the end, after the decked words, without disturbing them.
        val ordered = WordOrdering.byDeckSequence(words, listOf(2L, 1L, 3L))
        assertThat(ordered.map { it.word.text })
            .containsExactly("balance", "apple", "circle", "delta")
            .inOrder()
    }

    @Test
    fun byDeckSequence_staleSequenceIds_areIgnored() {
        val words = listOf(wordWithId(1L, "apple"), wordWithId(2L, "balance"))
        // 9L left the deck (skipped) after the sequence was published.
        val ordered = WordOrdering.byDeckSequence(words, listOf(9L, 2L, 1L))
        assertThat(ordered.map { it.word.text }).containsExactly("balance", "apple").inOrder()
    }
}
