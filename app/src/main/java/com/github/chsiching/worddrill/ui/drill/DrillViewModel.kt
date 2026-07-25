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
        /**
         * 当前词书是不是「复习」词书（issue #1）。
         * true 时顶部按钮显示「恢复」并走 [DrillViewModel.restoreCurrentWord]；
         * false 时显示「跳过」走 [DrillViewModel.skipCurrentWord]。
         */
        val isReviewBook: Boolean = false,
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
 *
 * Ticket #20 跳过：[skipCurrentWord] 在一个事务里把当前 book_word.skipped 置 1
 * + 把该 word 加到「复习」词书（若不在），然后重载卡片列表（被跳过的词因
 * [WordDao.getWordsWithSensesByBook] 过滤 skipped=0 而消失）。
 */
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
        _uiState.value = DrillUiState.Ready(
            bookName = bookName,
            cards = cards,
            isReviewBook = bookName == REVIEW_BOOK_NAME,
        )
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

    /**
     * Ticket #20：跳过当前词。
     *
     * 在一个事务里：
     * 1. 把当前 book_word 的 skipped 置 1（词书级：只改这本词书的关联行）
     * 2. 把该 word 加到「复习」词书（若不在；linkBookWord IGNORE 兜底）
     * 然后重载卡片列表 —— 被跳过的词因 [WordDao.getWordsWithSensesByBook] 过滤
     * skipped=0 而从列表消失，UI 据此把 pager 停在同一 index（指向下一张未跳过的卡）。
     *
     * 复习词书懒创建兜底：首启 [com.github.chsiching.worddrill.data.ReviewBookInitializer]
     * 并行建书，若跳过时还没建好则此处 getByName 返回 null 时即时建一本，保证跳过不丢词。
     *
     * @param currentPage 当前 pager 页码，用于定位要跳过的 word。
     * @return 跳过成功返回 true；当前不在就绪态或页码越界返回 false（UI 不翻页）。
     */
    fun skipCurrentWord(currentPage: Int) {
        val bookId = currentBookId ?: return
        val ready = _uiState.value as? DrillUiState.Ready ?: return
        // 复习词书不跳过（UI 不该显示跳过按钮，这里兜底防误触）。
        // 复习词书里的词用 restoreCurrentWord 恢复，不是再跳过。
        if (ready.isReviewBook) return
        val wordId = ready.cards.getOrNull(currentPage)?.word?.wordId ?: return
        viewModelScope.launch {
            db.withTransaction {
                // 1) 标记当前词书内此词为已跳过（词书级，不影响其他词书的同词关联）
                bookDao.setSkipped(bookId, wordId, skipped = true)
                // 2) 加到复习词书（懒创建兜底；linkBookWord IGNORE 处理已在复习词书的情形）
                val reviewBookId = (bookDao.getByName(REVIEW_BOOK_NAME)?.bookId
                    ?: bookDao.insert(Book(name = REVIEW_BOOK_NAME, isPreset = true)))
                bookDao.linkBookWord(BookWord(bookId = reviewBookId, wordId = wordId))
            }
            // 重载卡片：被跳过的词从列表消失；列表空了则转 Empty 态
            reloadCards(bookId, ready)
        }
    }

    /**
     * 复习词书的「恢复」操作（issue #1）。
     *
     * 在一个事务里：
     * 1. [BookDao.unskipWordEverywhere] —— 该词在所有原词书的 skipped 全置 0，词回到原词书可刷
     * 2. [BookDao.unlinkBookWord] —— 从复习词书移除关联（复习词书是「当前跳过中」动态集合）
     * 然后重载复习词书卡片（被恢复的词从复习词书消失，pager 停在同 index 指向下一张）。
     *
     * @param currentPage 当前 pager 页码，定位要恢复的 word
     */
    fun restoreCurrentWord(currentPage: Int) {
        val bookId = currentBookId ?: return
        val ready = _uiState.value as? DrillUiState.Ready ?: return
        // 只在复习词书响应恢复（普通词书没有恢复入口，兜底防误触）
        if (!ready.isReviewBook) return
        val wordId = ready.cards.getOrNull(currentPage)?.word?.wordId ?: return
        viewModelScope.launch {
            db.withTransaction {
                // 1) 该词在所有原词书恢复未跳过
                bookDao.unskipWordEverywhere(wordId)
                // 2) 从复习词书移除（复习词书只保留当前跳过中的词）
                bookDao.unlinkBookWord(bookId, wordId)
            }
            // 重载复习词书卡片：被恢复的词从列表消失
            reloadCards(bookId, ready)
        }
    }

    /** 跳过/恢复后重载当前词书的卡片列表（skipped=0 过滤已生效）。保留 isReviewBook 标志。 */
    private suspend fun reloadCards(bookId: Long, ready: DrillUiState.Ready) {
        val cards = wordDao.getWordsWithSensesByBook(bookId)
        if (cards.isEmpty()) {
            _uiState.value = DrillUiState.Empty
        } else {
            _uiState.value = ready.copy(cards = cards)
        }
    }

    private companion object {
        /** 「复习」词书名。与 [com.github.chsiching.worddrill.data.ReviewBookInitializer] 一致。 */
        const val REVIEW_BOOK_NAME = "复习"
    }
}
