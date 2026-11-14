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

    fun restoreBook(bookId: Long) {
        viewModelScope.launch { bookDao.restoreBook(bookId) }
    }

    fun purgeBook(bookId: Long) {
        viewModelScope.launch { bookDao.purgeBook(bookId) }
    }

    fun restoreEntry(bookId: Long, wordId: Long) {
        viewModelScope.launch { bookDao.setDeleted(bookId, wordId, deleted = false) }
    }

    fun purgeEntry(bookId: Long, wordId: Long) {
        viewModelScope.launch { bookDao.purgeDeleted(bookId, wordId) }
    }
}
