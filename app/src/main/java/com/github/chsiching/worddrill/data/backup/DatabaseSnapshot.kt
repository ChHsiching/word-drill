package com.github.chsiching.worddrill.data.backup

import com.github.chsiching.worddrill.data.local.entity.Book
import com.github.chsiching.worddrill.data.local.entity.BookWord
import com.github.chsiching.worddrill.data.local.entity.Sense
import com.github.chsiching.worddrill.data.local.entity.SwipeLog
import com.github.chsiching.worddrill.data.local.entity.Word

/**
 * Whole-database snapshot (in-memory model for export/import).
 *
 * Import strategy is **overwrite** (Ticket #10 default): restore by original primary keys,
 * clear the whole database, then batch re-insert. This way cross-table foreign key references
 * (book_word / sense reference word, swipe_log.bookId references book) are fully preserved, especially orphaned
 * swipe_log rows (their bookId/wordId may reference deleted books/words, but must still be kept
 * for cumulative statistics).
 *
 * Fields align with Room tables; no Android dependencies, making it easy to construct and
 * verify JSON round-trips in JVM unit tests.
 *
 * @param version JSON schema version number; currently 1. Backward compatible: defaults to 1 when absent.
 * @param nickname Optional nickname label. User-visible identifier of the exported file, not a
 *   global setting; blank is treated as null (not written).
 */
data class DatabaseSnapshot(
    val version: Int = 1,
    val nickname: String?,
    val books: List<Book> = emptyList(),
    val words: List<Word> = emptyList(),
    val senses: List<Sense> = emptyList(),
    val bookWords: List<BookWord> = emptyList(),
    val swipeLogs: List<SwipeLog> = emptyList(),
)
