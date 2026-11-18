package com.github.chsiching.worddrill.ui

import com.github.chsiching.worddrill.data.local.WordWithSenses

/** Single source for "the ordered words of a book" (issue #51). Both the drill
 *  deck and the word list go through this seam so the two can never disagree;
 *  deck-ordering modes (issue #54) extend it instead of adding a second sort. */
object WordOrdering {

    /** Case-insensitive alphabetical; equal ignoring case ties break on the raw
     *  text so the output never depends on the input order. */
    fun alphabetical(words: List<WordWithSenses>): List<WordWithSenses> =
        words.sortedWith(compareBy({ it.word.text.lowercase() }, { it.word.text }))
}
