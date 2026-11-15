# Glossary

Domain vocabulary for WordDrill. Keep this file free of implementation detail; it records what terms mean, not how they are stored.

- **Current book** — the book the drill tab is showing. Exactly one at a time; remembered across launches. The Library tab changes it.
- **Default book** — the first preset, non-review book in the library ordering (CET-4). Selected automatically on a fresh install; never a trap, because switching books stays one tap away.
- **Fresh install** — the first launch after install or data wipe: empty database, no persisted settings. The only state where default-book selection runs.
- **Cold start** — any process launch, including a fresh install. Do not use "cold start" when you mean "fresh install"; the fresh-install race (issue #30) only exists in the fresh-install state.
- **Resume position** — the word a book was last drilled at, remembered per book. Reopening a book lands on its resume position; when that word is no longer in the deck (skipped, deleted, restored away), the book opens at its first card instead.
- **Review book** — the preset book named 复习, a dynamic set of the words currently skipped in any book, deduplicated across books. Drilled like any other book; empty by default.
- **Deck** — the cards a book currently offers: its words that are neither skipped nor deleted, ordered alphabetically. Skipping a word shrinks the deck, which is why positions are remembered by word, not by index.
