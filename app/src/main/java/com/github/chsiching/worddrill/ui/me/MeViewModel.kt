package com.github.chsiching.worddrill.ui.me

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.chsiching.worddrill.data.local.dao.BookDao
import com.github.chsiching.worddrill.data.local.dao.SwipeLogDao
import com.github.chsiching.worddrill.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class MeStatsUiState(
    val todayCount: Int = 0,
    val totalCount: Int = 0,
    val bookName: String = "",
    val brushed: Int = 0,
    val total: Int = 0,
    val percent: Double = 0.0,
    /** Completed passes of the current book (GLOSSARY.md, completion count). */
    val completionCount: Int = 0,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MeViewModel @Inject constructor(
    private val swipeLogDao: SwipeLogDao,
    private val bookDao: BookDao,
    private val settings: SettingsRepository,
) : ViewModel() {

    private val bookProgress = settings.currentBookId
        .distinctUntilChanged()
        .flatMapLatest { bookId ->
            if (bookId == null) {
                flowOf(BookProgress())
            } else {
                combine(
                    flow { emit(BookProgress(bookName = bookDao.getById(bookId)?.name ?: "")) },
                    bookDao.observeWordCountInBook(bookId),
                    swipeLogDao.observeDistinctWordCountForBook(bookId),
                    bookDao.observeCompletionCount(bookId),
                ) { seeded, total, brushed, completionCount ->
                    seeded.copy(brushed = brushed, total = total, completionCount = completionCount)
                }
            }
        }

    val uiState: StateFlow<MeStatsUiState> = combine(
        swipeLogDao.observeTotalCount(),
        swipeLogDao.observeCountSince(MeStats.startOfTodayMillis()),
        bookProgress,
    ) { totalCount, todayCount, progress ->
        MeStatsUiState(
            todayCount = todayCount,
            totalCount = totalCount,
            bookName = progress.bookName,
            brushed = progress.brushed,
            total = progress.total,
            percent = MeStats.progressPercent(progress.brushed, progress.total),
            completionCount = progress.completionCount,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = MeStatsUiState(),
    )
}

private data class BookProgress(
    val bookName: String = "",
    val brushed: Int = 0,
    val total: Int = 0,
    val completionCount: Int = 0,
)
