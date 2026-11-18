package com.github.chsiching.worddrill.ui

import com.github.chsiching.worddrill.data.local.WordWithSenses
import com.github.chsiching.worddrill.data.settings.DeckOrdering
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.random.Random

/** Single source for "the ordered words of a book" (issue #51). Both the drill
 *  deck and the word list go through this seam so the two can never disagree;
 *  deck-ordering modes (issue #54) extend it instead of adding a second sort. */
object WordOrdering {

    /** Case-insensitive alphabetical; equal ignoring case ties break on the raw
     *  text so the output never depends on the input order. */
    fun alphabetical(words: List<WordWithSenses>): List<WordWithSenses> =
        words.sortedWith(compareBy({ it.word.text.lowercase() }, { it.word.text }))

    /** Shuffled deck ordering (issue #54): Fisher-Yates driven by [random]. The
     *  caller draws a fresh Random per book open so no fixed pattern can be
     *  memorized; tests inject a seeded Random for deterministic shuffles. */
    fun shuffled(words: List<WordWithSenses>, random: Random = Random.Default): List<WordWithSenses> {
        val deck = words.toMutableList()
        for (i in deck.size - 1 downTo 1) {
            val j = random.nextInt(i + 1)
            deck[i] = deck[j].also { deck[j] = deck[i] }
        }
        return deck
    }

    /** Resume position (GLOSSARY.md) resolved against a freshly dealt deck: the
     *  index of the word last drilled, or 0 when it left the deck. Keyed by word
     *  id, never by index — a reshuffle or a skip moves indices freely. */
    fun resumeIndex(savedWordId: Long?, cards: List<WordWithSenses>): Int =
        savedWordId
            ?.let { id -> cards.indexOfFirst { it.word.wordId == id } }
            ?.takeIf { it >= 0 }
            ?: 0

    /** Word-list ordering (issue #54): the list mirrors the deck's card sequence
     *  rather than re-sorting on its own, so the two surfaces can never disagree.
     *  Words missing from the sequence (added since the book was last opened, or
     *  no sequence published yet) follow at the end, alphabetically. */
    fun byDeckSequence(words: List<WordWithSenses>, deckSequence: List<Long>?): List<WordWithSenses> {
        if (deckSequence == null) return alphabetical(words)
        val rank = HashMap<Long, Int>(deckSequence.size)
        deckSequence.forEachIndexed { index, wordId -> rank[wordId] = index }
        val decked = words.filter { rank.containsKey(it.word.wordId) }
            .sortedBy { rank.getValue(it.word.wordId) }
        val notDealtYet = words.filterNot { rank.containsKey(it.word.wordId) }
        return decked + alphabetical(notDealtYet)
    }
}

/** The deck sequences published this session, keyed by book id: the single
 *  ordering source both the DrillViewModel deck (publisher) and the
 *  WordListViewModel list (consumer) share (issue #54). In-memory only — a
 *  shuffle sequence is never persisted (ADR 0002), so it dies with the process
 *  and every fresh open re-deals. */
@Singleton
class DeckSequenceState @Inject constructor() {

    private val _sequences = MutableStateFlow<Map<Long, DealtDeck>>(emptyMap())

    /** Dealt decks keyed by book id, in deck order. */
    val sequences: StateFlow<Map<Long, DealtDeck>> = _sequences.asStateFlow()

    fun publish(bookId: Long, wordIds: List<Long>, ordering: DeckOrdering) {
        _sequences.value = _sequences.value +
            (bookId to DealtDeck(wordIds = wordIds, ordering = ordering))
    }
}

/** One book's dealt deck as published by the drill: the card sequence plus the
 *  deck ordering that dealt it (issue #60). The word list's letter bar follows
 *  this dealt mode, not the live setting — the setting only takes effect on the
 *  next book open (#54), so between a setting change and the next open the bar
 *  must keep matching the list the user actually sees. */
data class DealtDeck(
    val wordIds: List<Long>,
    val ordering: DeckOrdering,
)
