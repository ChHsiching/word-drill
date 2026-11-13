package com.github.chsiching.worddrill.ui.recyclebin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.chsiching.worddrill.data.local.dao.BookDao
import com.github.chsiching.worddrill.data.local.dao.DeletedEntry
import com.github.chsiching.worddrill.data.local.entity.Book
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Recycle bin page (Ticket #22): lists deleted=1 books and book-word links, supporting
 * restore and permanent delete.
 *
 * Two kinds of items:
 * - **Books** ([books]): a whole book soft-deleted ([Book.deleted]=1). Restore = the whole
 *   book returns to the list; permanent delete = a real DELETE of the book (CASCADE clears
 *   its book_word links).
 * - **Word links** ([entries]): a word link of some book soft-deleted
 *   ([com.github.chsiching.worddrill.data.local.entity.BookWord.deleted]=1).
 *   Restore = the word returns to its original book; permanent delete = a real DELETE of
 *   that link row.
 *
 * Permanently deleting a book CASCADEs away all its links (including soft-deleted ones),
 * so the book section and the word section can never disagree with "book purged but its
 * words still in the recycle bin"; the INNER JOIN on book/word keeps orphan entries from
 * showing.
 */
@HiltViewModel
class RecycleBinViewModel @Inject constructor(
    private val bookDao: BookDao,
) : ViewModel() {

    val books: StateFlow<List<Book>> = bookDao.observeDeletedBooks()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    val entries: StateFlow<List<DeletedEntry>> = bookDao.observeDeletedEntries()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    // ---- book restore / permanent delete ----

    /** Restores a book: deleted set to 0, the book returns to the library list. */
    fun restoreBook(bookId: Long) {
        viewModelScope.launch { bookDao.restoreBook(bookId) }
    }

    /** Permanently deletes a book: real DELETE (CASCADE clears links; the UI should confirm before calling). */
    fun purgeBook(bookId: Long) {
        viewModelScope.launch { bookDao.purgeBook(bookId) }
    }

    // ---- word-link restore / permanent delete ----

    /** Restores a word link: deleted set to 0, the word returns to its original book list. */
    fun restoreEntry(bookId: Long, wordId: Long) {
        viewModelScope.launch { bookDao.setDeleted(bookId, wordId, deleted = false) }
    }

    /** Permanently deletes a word link: real DELETE of the link row (irreversible; the UI should confirm before calling). */
    fun purgeEntry(bookId: Long, wordId: Long) {
        viewModelScope.launch { bookDao.purgeDeleted(bookId, wordId) }
    }
}
