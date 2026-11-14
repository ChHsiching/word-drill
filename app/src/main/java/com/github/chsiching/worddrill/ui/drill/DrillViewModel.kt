package com.github.chsiching.worddrill.ui.drill

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.github.chsiching.worddrill.data.local.WordDrillDatabase
import com.github.chsiching.worddrill.data.local.WordWithSenses
import com.github.chsiching.worddrill.data.local.entity.Book
import com.github.chsiching.worddrill.data.local.dao.BookDao
import com.github.chsiching.worddrill.data.local.dao.SwipeLogDao
import com.github.chsiching.worddrill.data.local.dao.WordDao
import com.github.chsiching.worddrill.data.local.entity.BookWord
import com.github.chsiching.worddrill.data.local.entity.SwipeLog
import com.github.chsiching.worddrill.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface DrillUiState {
    data object Loading : DrillUiState
    data object Empty : DrillUiState
    data class Ready(
        val bookName: String,
        val cards: List<WordWithSenses>,
        val isReviewBook: Boolean = false,
    ) : DrillUiState
}

@HiltViewModel
class DrillViewModel @Inject constructor(
    private val db: WordDrillDatabase,
    private val bookDao: BookDao,
    private val wordDao: WordDao,
    private val swipeLogDao: SwipeLogDao,
    private val settings: SettingsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<DrillUiState>(DrillUiState.Loading)
    val uiState: StateFlow<DrillUiState> = _uiState.asStateFlow()

    private var currentBookId: Long? = null

    init {
        viewModelScope.launch {
            settings.currentBookId
                .distinctUntilChanged()
                .collect { recorded -> loadBookFor(recorded) }
        }
    }

    private suspend fun loadBookFor(recorded: Long?) {
        val book = recorded?.let { bookDao.getById(it) }
            ?: firstBookOrNull()?.also { settings.setCurrentBookId(it.bookId) }

        if (book == null) {
            _uiState.value = DrillUiState.Empty
            return
        }
        setReady(book.bookId, book.name)
    }

    private suspend fun firstBookOrNull(): Book? =
        bookDao.observeAll().first().firstOrNull()

    private suspend fun setReady(bookId: Long, bookName: String) {
        currentBookId = bookId
        val cards = wordDao.getWordsWithSensesByBook(bookId)
        if (cards.isEmpty()) {
            _uiState.value = DrillUiState.Empty
            return
        }
        _uiState.value = DrillUiState.Ready(
            bookName = bookName,
            cards = cards,
            isReviewBook = bookName == REVIEW_BOOK_NAME,
        )
    }

    fun onPageSettled(previousPage: Int, currentPage: Int) {
        if (!shouldLogSwipe(previousPage, currentPage)) return
        val bookId = currentBookId ?: return
        val ready = _uiState.value as? DrillUiState.Ready ?: return
        val wordId = ready.cards.getOrNull(previousPage)?.word?.wordId ?: return
        viewModelScope.launch {
            swipeLogDao.insert(
                SwipeLog(bookId = bookId, wordId = wordId, timestamp = System.currentTimeMillis())
            )
        }
    }

    fun skipCurrentWord(currentPage: Int) {
        val bookId = currentBookId ?: return
        val ready = _uiState.value as? DrillUiState.Ready ?: return
        if (ready.isReviewBook) return
        val wordId = ready.cards.getOrNull(currentPage)?.word?.wordId ?: return
        viewModelScope.launch {
            db.withTransaction {
                bookDao.setSkipped(bookId, wordId, skipped = true)
                val reviewBookId = (bookDao.getByName(REVIEW_BOOK_NAME)?.bookId
                    ?: bookDao.insert(Book(name = REVIEW_BOOK_NAME, isPreset = true)))
                bookDao.linkBookWord(BookWord(bookId = reviewBookId, wordId = wordId))
            }
            reloadCards(bookId, ready)
        }
    }

    fun restoreCurrentWord(currentPage: Int) {
        val bookId = currentBookId ?: return
        val ready = _uiState.value as? DrillUiState.Ready ?: return
        if (!ready.isReviewBook) return
        val wordId = ready.cards.getOrNull(currentPage)?.word?.wordId ?: return
        viewModelScope.launch {
            db.withTransaction {
                bookDao.unskipWordEverywhere(wordId)
                bookDao.unlinkBookWord(bookId, wordId)
            }
            reloadCards(bookId, ready)
        }
    }

    private suspend fun reloadCards(bookId: Long, ready: DrillUiState.Ready) {
        val cards = wordDao.getWordsWithSensesByBook(bookId)
        if (cards.isEmpty()) {
            _uiState.value = DrillUiState.Empty
        } else {
            _uiState.value = ready.copy(cards = cards)
        }
    }

    private companion object {
        const val REVIEW_BOOK_NAME = "复习"
    }
}
