# Glossary

Domain vocabulary for WordDrill. Keep this file free of implementation detail; it records what terms mean, not how they are stored.

- **Current book** — the book the drill tab is showing. Exactly one at a time; remembered across launches. The Library tab changes it.
- **Default book** — the first preset, non-review book in the library ordering (CET-4). Selected automatically on a fresh install; never a trap, because switching books stays one tap away.
- **Fresh install** — the first launch after install or data wipe: empty database, no persisted settings. The only state where default-book selection runs.
- **Cold start** — any process launch, including a fresh install. Do not use "cold start" when you mean "fresh install"; the fresh-install race (issue #30) only exists in the fresh-install state.
- **Resume position** — the word a book was last drilled at, remembered per book. Reopening a book lands on its resume position; when that word is no longer in the deck (skipped, deleted, restored away), the book opens at its first card instead.
- **Review book** — the preset book named 复习, a dynamic set of the words currently skipped in any book, deduplicated across books. Drilled like any other book; empty by default.
- **Deck** — the cards a book currently offers: its words that are neither skipped nor deleted. The deck has a deck ordering. Skipping a word shrinks the deck, which is why positions are remembered by word, not by index.
- **Deck ordering** — the mode governing the sequence of cards in a deck: alphabetical (case-insensitive) or shuffled. A global setting, not per-book. Shuffled order is re-randomized each time the book is opened, never persisted — this prevents memorizing a fixed shuffle pattern.
- **Auto-advance** — a drill-screen playback mode that advances to the next card after a chosen dwell time. Manual interaction repositions but does not stop it; leaving the drill screen or reaching the deck's end stops it.
- **Dwell time** — how long auto-advance stays on a card before advancing to the next; set in the Me tab, remembered across launches.
- **Completion card** — the virtual card after the last card of a deck, shown when the deck is finished. Displays the book name and completion count, and offers to restart the book or switch to another.
- **Completion count** — how many times a book's deck has been fully traversed to the completion card. Per book, cumulative.
- **Progress reset** — returning a book to its starting state: resume position set to the first card, all skipped words of that book un-skipped. Does not clear swipe logs or the completion count; those are cumulative history.
- **Self-built dictionary** — the app's built-in English-Chinese dictionary, built by crawling Youdao and packaged as app assets. Replaces ECDICT. Provides UK/US phonetics, domain-labeled senses, and clean punctuation.
- **Phonetic preference** — the user's choice of which phonetic(s) to display on cards: UK, US, or both. A global setting in the Me tab.
