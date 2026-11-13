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

/**
 * The three stats of the "Me" tab (Ticket #8):
 * - [todayCount]: cards drilled today (swipe_log filtered from today's start of day)
 * - [totalCount]: lifetime cards drilled (whole table)
 * - [bookName] / [brushed] / [total] / [percent]: current book progress (X brushed / Y
 *   total + percent)
 *
 * All driven by reactive Flows: after drilling, switching to this tab updates the numbers
 * immediately (spec acceptance). After switching the current book (Library tab), the
 * progress matches the new book (spec acceptance).
 */
data class MeStatsUiState(
    val todayCount: Int = 0,
    val totalCount: Int = 0,
    val bookName: String = "",
    val brushed: Int = 0,
    val total: Int = 0,
    val percent: Int = 0,
)

/**
 * ViewModel of the "Me" tab (Ticket #8).
 *
 * Reactive aggregation: observe [SettingsRepository.currentBookId]; when bookId changes,
 * [flatMapLatest] switches to the new book's progress Flow. Today/total stats are observed
 * independently. The three are [combine]d into a single [MeStatsUiState] exposed to the
 * Composable as a StateFlow.
 *
 * - distinctUntilChanged (pitfall F): a repeated bookId does not re-subscribe the progress
 *   Flow.
 * - Today's start point is computed once at ViewModel construction: the long-lived
 *   suspended-across-midnight case is not covered (the spec only requires "switching back
 *   to this tab after drilling updates immediately"; reopening the app the next day
 *   recomputes, which covers daily use).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MeViewModel @Inject constructor(
    private val swipeLogDao: SwipeLogDao,
    private val bookDao: BookDao,
    private val settings: SettingsRepository,
) : ViewModel() {

    /** Current book progress (name + brushed + total). Switches to the new book's Flow when bookId changes. */
    private val bookProgress = settings.currentBookId
        .distinctUntilChanged()
        .flatMapLatest { bookId ->
            if (bookId == null) {
                // No current book (unset on first launch): empty name + 0/0 progress
                flowOf(BookProgress())
            } else {
                // Book name loads once asynchronously (the flow block is cancelled with
                // flatMapLatest, so an old bookId's assignment cannot clobber the new one);
                // counts are observed reactively.
                combine(
                    flow { emit(BookProgress(bookName = bookDao.getById(bookId)?.name ?: "")) },
                    bookDao.observeWordCountInBook(bookId),
                    swipeLogDao.observeDistinctWordCountForBook(bookId),
                ) { seeded, total, brushed -> seeded.copy(brushed = brushed, total = total) }
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
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = MeStatsUiState(),
    )
}

/** Intermediate aggregate for current book progress (name + brushed + total). */
private data class BookProgress(
    val bookName: String = "",
    val brushed: Int = 0,
    val total: Int = 0,
)
