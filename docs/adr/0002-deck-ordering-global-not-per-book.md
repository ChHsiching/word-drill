# ADR 0002: Deck ordering is a global setting, not per-book

## Status

Accepted (2026-11-18)

## Context

The book experience overhaul introduces deck ordering: alphabetical (case-insensitive) vs shuffled. The design question was whether this setting lives per-book (each book remembers its own mode) or globally (one setting for all books).

## Decision

Deck ordering is a **global setting** in the Me tab (显示 group). All books share the same ordering mode. Shuffled order is re-randomized each time a book is opened — a specific shuffle sequence is never persisted.

## Consequences

**Positive:**
- One setting row, not a per-book property to manage.
- No storage or migration cost.
- Consistent experience: switching books doesn't surprise with a different ordering mode.
- Non-persisted shuffle prevents "memorizing the shuffle pattern".

**Negative:**
- A user who wants CET-4 alphabetical but 考研 shuffled cannot do so.
- Changing the setting mid-session takes effect on the *next* book open (current session uninterrupted), which may not be immediately obvious.

**Why not per-book:**
- Per-book adds a BookDao column, a settings surface per book (word-list page or library row), and state management for "which book am I in, which mode applies". The owner prioritized simplicity; nobody on the team could articulate a concrete scenario where per-book ordering is essential rather than merely nice.
