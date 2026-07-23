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
 * 「我的」Tab 三项统计（Ticket #8）：
 * - [todayCount]：今日刷卡数（按当日 0 点筛选 swipe_log）
 * - [totalCount]：累计刷卡数（全表）
 * - [bookName] / [brushed] / [total] / [percent]：当前词书进度（已刷 X / 总 Y + 百分比）
 *
 * 全部响应式 Flow 驱动：刷卡后切到本 Tab 数字立即更新（规格验收）。
 * 当前词书切换（「库」Tab）后，进度对应新词书（规格验收）。
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
 * 「我的」Tab 的 ViewModel（Ticket #8）。
 *
 * 响应式聚合：observe [SettingsRepository.currentBookId] → 当 bookId 变化时
 * 用 [flatMapLatest] 切到新词书的进度 Flow；今日/累计统计独立 observe。
 * 三者 [combine] 成单一 [MeStatsUiState]，StateFlow 暴露给 Composable。
 *
 * - distinctUntilChanged（坑 F）：bookId 在相同值上不重复重订阅进度 Flow。
 * - 今日起点在 ViewModel 构造时算一次：跨午夜长期挂起场景未覆盖（规格只要求
 *   "刷卡后切回本 Tab 立即更新"；次日重开 App 会重算，满足日常使用）。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MeViewModel @Inject constructor(
    private val swipeLogDao: SwipeLogDao,
    private val bookDao: BookDao,
    private val settings: SettingsRepository,
) : ViewModel() {

    /** 当前词书进度（书名 + 已刷 + 总数）。bookId 变化时切到新词书的 Flow。 */
    private val bookProgress = settings.currentBookId
        .distinctUntilChanged()
        .flatMapLatest { bookId ->
            if (bookId == null) {
                // 无当前词书（首次启动未设）：空名 + 0/0 进度
                flowOf(BookProgress())
            } else {
                // 书名一次性异步加载（flow 块跟随 flatMapLatest 取消，
                // 避免旧 bookId 的赋值覆盖新 bookId）；数量 observe 响应式刷新。
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

/** 当前词书进度的中间聚合（书名 + 已刷 + 总数）。 */
private data class BookProgress(
    val bookName: String = "",
    val brushed: Int = 0,
    val total: Int = 0,
)
