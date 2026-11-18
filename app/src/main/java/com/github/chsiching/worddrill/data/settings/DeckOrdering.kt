package com.github.chsiching.worddrill.data.settings

/** Deck ordering (GLOSSARY.md): a global setting, never per-book (ADR 0002).
 *  Shuffled order re-randomizes on every book open and is never persisted. */
enum class DeckOrdering {
    ALPHABETICAL,
    SHUFFLED;

    companion object {
        fun fromStorageName(name: String?): DeckOrdering =
            name?.let { runCatching { valueOf(it) }.getOrNull() } ?: ALPHABETICAL
    }
}
