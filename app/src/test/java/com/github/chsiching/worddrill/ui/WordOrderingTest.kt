package com.github.chsiching.worddrill.ui

import com.github.chsiching.worddrill.data.local.WordWithSenses
import com.github.chsiching.worddrill.data.local.entity.Word
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class WordOrderingTest {

    private fun word(text: String) = WordWithSenses(word = Word(text = text), senses = emptyList())

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
}
