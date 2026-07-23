package com.github.chsiching.worddrill.ui.drill

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.chsiching.worddrill.data.local.WordWithSenses
import com.github.chsiching.worddrill.data.local.entity.Book
import com.github.chsiching.worddrill.data.local.dao.BookDao
import com.github.chsiching.worddrill.data.local.dao.SwipeLogDao
import com.github.chsiching.worddrill.data.local.dao.WordDao
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

/**
 * 「刷」Tab 的 UI 状态。三态：加载中 / 空词书 / 就绪。
 */
sealed interface DrillUiState {
    data object Loading : DrillUiState
    data object Empty : DrillUiState
    data class Ready(
        /** 当前词书名，顶部纯文字展示（不可交互）。 */
        val bookName: String,
        /** 卡片列表：一张卡 = 一个 word 的全部 sense。 */
        val cards: List<WordWithSenses>,
    ) : DrillUiState
}

/**
 * 「刷」Tab 的 ViewModel。
 *
 * 监听 DataStore 的 [SettingsRepository.currentBookId]：词书在「库」Tab 被切换后，
 * 本 Tab 自动重载新词书的卡片列表（规格验收：「刷」Tab 刷卡内容立即切换）。
 *
 * bookId 解析：null（首次启动）或记录值已失效（词书被删）→ 回退到第一本（预置 CET-4）。
 * 卡片为空的词书也显示 Empty 态。
 *
 * 计数逻辑：UI 监听 pager 页面 settled 事件，调用 [onPageSettled]；
 * 是否写 swipe_log 由纯函数 [shouldLogSwipe] 决策，保证可单测。
 */
@HiltViewModel
class DrillViewModel @Inject constructor(
    private val bookDao: BookDao,
    private val wordDao: WordDao,
    private val swipeLogDao: SwipeLogDao,
    private val settings: SettingsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<DrillUiState>(DrillUiState.Loading)
    val uiState: StateFlow<DrillUiState> = _uiState.asStateFlow()

    /** 当前就绪态下的 bookId；加载中/空态为 null。供 [onPageSettled] 写日志用。 */
    private var currentBookId: Long? = null

    init {
        // 监听 currentBookId 变化（首次启动 + 「库」Tab 切换词书都会触发）：
        // distinctUntilChanged 避免相同 bookId 重复重载（见交接链坑 A）。
        viewModelScope.launch {
            settings.currentBookId
                .distinctUntilChanged()
                .collect { recorded -> loadBookFor(recorded) }
        }
    }

    /**
     * 把 bookId 解析成 UI 态：
     * - 记录值有效 → 直接加载该词书
     * - 记录值无效（已删）/ 未记录（null）→ 回退第一本，并写回 DataStore 保持一致
     */
    private suspend fun loadBookFor(recorded: Long?) {
        val book = recorded?.let { bookDao.getById(it) }
            ?: firstBookOrNull()?.also { settings.setCurrentBookId(it.bookId) }

        if (book == null) {
            _uiState.value = DrillUiState.Empty
            return
        }
        setReady(book.bookId, book.name)
    }

    /** 第一本词书（预置在前、按 name 升序）；无任何词书时返回 null（空态）。 */
    private suspend fun firstBookOrNull(): Book? =
        bookDao.observeAll().first().firstOrNull()

    private suspend fun setReady(bookId: Long, bookName: String) {
        currentBookId = bookId
        val cards = wordDao.getWordsWithSensesByBook(bookId)
        if (cards.isEmpty()) {
            _uiState.value = DrillUiState.Empty
            return
        }
        _uiState.value = DrillUiState.Ready(bookName = bookName, cards = cards)
    }

    /**
     * 由 UI 在 pager 页面 settled 时调用。向右滑（页码递增）写一条 swipe_log；
     * 向左滑 / 到头（页码不变）不写。bookId 缺失（加载/空态）时安全跳过。
     */
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
}
