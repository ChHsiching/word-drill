package com.github.chsiching.worddrill.ui.library

import com.github.chsiching.worddrill.data.local.WordWithSenses
import com.github.chsiching.worddrill.data.local.entity.Word
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class WordListFilterTest {

    private fun word(text: String) = WordWithSenses(word = Word(text = text), senses = emptyList())

    private val words = listOf(word("Apple"), word("core"), word("Zebra"))

    @Test
    fun filter_matchesSubstrings_caseInsensitively() {
        val kept = filterWords(words, "COR")
        assertThat(kept.map { it.word.text }).containsExactly("core")
    }

    @Test
    fun filter_emptyQuery_keepsEverything() {
        assertThat(filterWords(words, "")).hasSize(3)
    }

    @Test
    fun filter_noMatch_returnsEmpty() {
        assertThat(filterWords(words, "xyz")).isEmpty()
    }
}
