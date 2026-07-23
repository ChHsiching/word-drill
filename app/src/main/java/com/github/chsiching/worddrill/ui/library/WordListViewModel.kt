package com.github.chsiching.worddrill.ui.library

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.github.chsiching.worddrill.data.local.WordDrillDatabase
import com.github.chsiching.worddrill.data.local.WordWithSenses
import com.github.chsiching.worddrill.data.local.dao.BookDao
import com.github.chsiching.worddrill.data.local.dao.WordDao
import com.github.chsiching.worddrill.data.local.entity.BookWord
import com.github.chsiching.worddrill.data.local.entity.Sense
import com.github.chsiching.worddrill.data.local.entity.Word
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 「库」Tab 二级页：词书内词条列表的对话框状态。
 * - [Add]：新增词条（text + pos + meaning）
 * - [Edit]：编辑某条义项（pos + meaning；wordText 只读展示）
 */
sealed interface WordListDialog {
    data object None : WordListDialog
    data class Add(
        val text: String = "",
        val pos: String = "",
        val meaning: String = "",
        @StringRes val error: Int? = null,
    ) : WordListDialog
    data class Edit(
        val senseId: Long,
        val wordText: String,
        val pos: String = "",
        val meaning: String = "",
        @StringRes val error: Int? = null,
    ) : WordListDialog
}

/**
 * 词条列表页 UI 状态。bookName/isPreset 在 init 时加载一次（同一 destination 下不变）。
 */
data class WordListUiState(
    val bookName: String = "",
    val isPreset: Boolean = false,
    val words: List<WordWithSenses> = emptyList(),
    val dialog: WordListDialog = WordListDialog.None,
)

/**
 * 词书内词条列表的 ViewModel（Ticket #7）。
 *
 * - bookId 取自 [SavedStateHandle]（navigation-compose 在 composable route 的 nav arg
 *   会自动同步进同源 SavedStateHandle；`hiltViewModel()` 默认绑定当前 backStackEntry）。
 * - 词条列表订阅 [WordDao.observeWordsWithSensesByBook]，增删改后自动重发。
 * - 新增：全局词条池复用（[WordDao.findIdByText]），写 sense，再 linkBookWord；整步一个事务。
 * - 编辑：[WordDao.updateSense]。
 * - 移除：[BookDao.unlinkBookWord]（只断关联，不删全局 word，避免影响其他词书）。
 * - 预置词书：UI 由 isPreset 决定是否渲染新增/编辑/移除按钮（DAO 层不拦，规格只要求 UI 不可操作）。
 */
@HiltViewModel
class WordListViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val db: WordDrillDatabase,
    private val wordDao: WordDao,
    private val bookDao: BookDao,
) : ViewModel() {

    private val bookId: Long = checkNotNull(savedStateHandle.get<String>("bookId")?.toLongOrNull()) {
        "WordListViewModel requires 'bookId' nav arg"
    }

    private val _dialog = MutableStateFlow<WordListDialog>(WordListDialog.None)
    val dialog: StateFlow<WordListDialog> = _dialog.asStateFlow()

    private val _bookName = MutableStateFlow("")
    private val _isPreset = MutableStateFlow(false)

    init {
        viewModelScope.launch {
            val book = bookDao.getById(bookId)
            if (book != null) {
                _bookName.value = book.name
                _isPreset.value = book.isPreset
            }
        }
    }

    val uiState: StateFlow<WordListUiState> = combine(
        wordDao.observeWordsWithSensesByBook(bookId),
        _bookName,
        _isPreset,
        _dialog,
    ) { words, bookName, isPreset, dialog ->
        WordListUiState(
            bookName = bookName,
            isPreset = isPreset,
            words = words,
            dialog = dialog,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = WordListUiState(),
    )

    // ---- 新增词条 ----

    fun openAddDialog() { _dialog.value = WordListDialog.Add() }

    /** 实时更新输入并校验（onValueChange 调）。 */
    fun onAddInput(text: String, pos: String, meaning: String) {
        val error = validateAddWordInput(text, pos, meaning)
        _dialog.value = (_dialog.value as? WordListDialog.Add)
            ?.copy(text = text, pos = pos, meaning = meaning, error = error)
            ?: WordListDialog.None
    }

    /** 提交新增：通过校验 → 事务写 word(复用/新建) + sense + linkBookWord。 */
    fun submitAdd() {
        val d = _dialog.value as? WordListDialog.Add ?: return
        if (d.error != null) return // validateAddWordInput 已在 trim 后校验
        val text = d.text.trim()
        val pos = d.pos.trim()
        val meaning = d.meaning.trim()
        viewModelScope.launch {
            db.withTransaction {
                // 1) 全局词条池去重（复用既有 wordId，否则新建；IGNORE 兜底 -1 取回查）
                val wordId = wordDao.findIdByText(text)
                    ?: wordDao.insert(Word(text = text)).let { id ->
                        if (id == -1L) wordDao.findIdByText(text)!! else id
                    }
                // 2) 写义项（(wordId, pos) 唯一，IGNORE 兜底重复词性）
                wordDao.insertSense(Sense(wordId = wordId, pos = pos, meaning = meaning))
                // 3) 挂到当前词书
                bookDao.linkBookWord(BookWord(bookId = bookId, wordId = wordId))
            }
            _dialog.value = WordListDialog.None
        }
    }

    // ---- 编辑义项 ----

    fun openEditDialog(senseId: Long, wordText: String, pos: String, meaning: String) {
        _dialog.value = WordListDialog.Edit(
            senseId = senseId, wordText = wordText, pos = pos, meaning = meaning,
        )
    }

    /** 实时更新编辑输入并校验。 */
    fun onEditInput(pos: String, meaning: String) {
        val error = validateSenseEditInput(pos, meaning)
        _dialog.value = (_dialog.value as? WordListDialog.Edit)
            ?.copy(pos = pos, meaning = meaning, error = error)
            ?: WordListDialog.None
    }

    /** 提交编辑：通过校验 → updateSense。 */
    fun submitEdit() {
        val d = _dialog.value as? WordListDialog.Edit ?: return
        if (d.error != null) return
        val pos = d.pos.trim()
        val meaning = d.meaning.trim()
        viewModelScope.launch {
            wordDao.updateSense(d.senseId, pos, meaning)
            _dialog.value = WordListDialog.None
        }
    }

    // ---- 从词书移除词条（断关联，不删全局词）----

    fun removeWordFromBook(wordId: Long) {
        viewModelScope.launch { bookDao.unlinkBookWord(bookId, wordId) }
    }

    fun dismissDialog() { _dialog.value = WordListDialog.None }
}
