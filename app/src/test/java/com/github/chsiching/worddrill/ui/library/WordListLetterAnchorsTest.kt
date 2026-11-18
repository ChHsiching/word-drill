package com.github.chsiching.worddrill.ui.library

import com.github.chsiching.worddrill.data.local.WordWithSenses
import com.github.chsiching.worddrill.data.local.entity.Word
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** letterAnchors (issue #60): the letter index bar's jump map over the list as
 *  displayed (ordered, search-filtered). Anchors are case-insensitive and skip
 *  words whose initial is not A–Z. */
class WordListLetterAnchorsTest {

    private fun word(text: String) = WordWithSenses(word = Word(text = text), senses = emptyList())

    @Test
    fun anchors_emptyList_empty() {
        assertThat(letterAnchors(emptyList())).isEmpty()
    }

    @Test
    fun anchors_firstWordOfEachLetter_caseInsensitive() {
        val words = listOf(word("Apple"), word("aardvark"), word("banana"), word("Banana"), word("cherry"))
        val anchors = letterAnchors(words)
        assertThat(anchors.keys).containsExactly('A', 'B', 'C').inOrder()
        assertThat(anchors['A']).isEqualTo(0)
        assertThat(anchors['B']).isEqualTo(2)
        assertThat(anchors['C']).isEqualTo(4)
    }

    @Test
    fun anchors_skipNonLetterInitials() {
        val words = listOf(word("7zip"), word("apple"), word("你好"))
        val anchors = letterAnchors(words)
        assertThat(anchors.keys).containsExactly('A')
        assertThat(anchors['A']).isEqualTo(1)
    }
}
