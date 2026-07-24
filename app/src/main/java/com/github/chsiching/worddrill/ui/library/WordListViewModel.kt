package com.github.chsiching.worddrill.ui.library

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.github.chsiching.worddrill.data.local.WordDrillDatabase
import com.github.chsiching.worddrill.data.local.WordWithSenses
import com.github.chsiching.worddrill.data.local.dao.BookDao
import com.github.chsiching.worddrill.data.local.dao.DictionaryDao
import com.github.chsiching.worddrill.data.local.dao.WordDao
import com.github.chsiching.worddrill.data.local.entity.BookWord
import com.github.chsiching.worddrill.data.local.entity.Sense
import com.github.chsiching.worddrill.data.local.entity.Word
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 手动加词条时 POS 下拉固定项（issue #9 字面，12 个常用 POS）。
 * 文件导入的 [com.github.chsiching.worddrill.data.wordimport.PosMeaningParser] 支持更全（17 个），
 * 但手动加词条是高频交互，下拉越短越易选 —— 按 issue 只给 12 个。
 */
val WORD_LIST_POS_OPTIONS: List<String> = listOf(
    "n.", "v.", "vt.", "vi.", "adj.", "adv.",
    "prep.", "conj.", "pron.", "art.", "num.", "int.",
)

/** 查词典 debounce 时长：用户停止输入 [DEBOUNCE_MS] 后才查库，避免每字符一次查询。 */
private const val DEBOUNCE_MS = 300L

/**
 * 「库」Tab 二级页：词书内词条列表的对话框状态。
 * - [Add]：新增词条（text + pos + meaning；#9 起输入 text 后自动查词典带出 pos+meaning）
 * - [Edit]：编辑某条义项（pos + meaning；wordText 只读展示）
 */
sealed interface WordListDialog {
    data object None : WordListDialog
    data class Add(
        val text: String = "",
        val pos: String = "",
        val meaning: String = "",
        @StringRes val error: Int? = null,
        /** 自动填值标记：true 表示当前 pos/meaning 是词典自动填的，用户可继续手改。 */
        val autoFilled: Boolean = false,
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
 * 词书内词条列表的 ViewModel（Ticket #7 + #9 POS 下拉与词典查词）。
 *
 * - bookId 取自 [SavedStateHandle]（navigation-compose 在 composable route 的 nav arg
 *   会自动同步进同源 SavedStateHandle；`hiltViewModel()` 默认绑定当前 backStackEntry）。
 * - 词条列表订阅 [WordDao.observeWordsWithSensesByBook]，增删改后自动重发。
 * - 新增：全局词条池复用（[WordDao.findIdByText]），写 sense，再 linkBookWord；整步一个事务。
 * - **Ticket #9**：[onTextInput] 后 debounce 查 [DictionaryDao]，命中自动填 pos + meaning
 *   （autoFilled=true 标记，用户后续手动改仍可）。
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
    private val dictionaryDao: DictionaryDao,
) : ViewModel() {

    private val bookId: Long = checkNotNull(savedStateHandle.get<String>("bookId")?.toLongOrNull()) {
        "WordListViewModel requires 'bookId' nav arg"
    }

    private val _dialog = MutableStateFlow<WordListDialog>(WordListDialog.None)
    val dialog: StateFlow<WordListDialog> = _dialog.asStateFlow()

    private val _bookName = MutableStateFlow("")
    private val _isPreset = MutableStateFlow(false)

    /** 当前 debounce 查词典的协程；新输入到来时 cancel 上一次。 */
    private var dictLookupJob: Job? = null

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

    /**
     * 单词输入变更（onValueChange 调）。
     *
     * 与既有 [onPosInput] / [onMeaningInput] 分开：text 变化触发 debounce 查词典。
     * 词典命中 → 自动填 pos + meaning（autoFilled=true）。用户之前手填过的（autoFilled=false）
     * 不被覆盖（避免用户改完 pos 又被词典冲掉）。
     */
    fun onTextInput(text: String) {
        val current = _dialog.value as? WordListDialog.Add ?: return
        // 用户手动改过 pos/meaning 后（autoFilled=false），不再被词典自动填冲掉
        val keepUserEdit = !current.autoFilled && (current.pos.isNotBlank() || current.meaning.isNotBlank())
        _dialog.value = current.copy(
            text = text,
            error = validateAddWordInput(text, current.pos, current.meaning),
            // 清掉用户尚未改的字段，让词典命中后重填
            pos = if (keepUserEdit) current.pos else "",
            meaning = if (keepUserEdit) current.meaning else "",
            autoFilled = false,
        )
        scheduleDictLookup(text, overwriteUserEdit = !keepUserEdit)
    }

    /** 词性输入变更（用户手动选/改；autoFilled 立即置 false）。 */
    fun onPosInput(pos: String) {
        val current = _dialog.value as? WordListDialog.Add ?: return
        _dialog.value = current.copy(pos = pos, error = validateAddWordInput(current.text, pos, current.meaning), autoFilled = false)
    }

    /** 释义输入变更（同 [onPosInput]）。 */
    fun onMeaningInput(meaning: String) {
        val current = _dialog.value as? WordListDialog.Add ?: return
        _dialog.value = current.copy(meaning = meaning, error = validateAddWordInput(current.text, current.pos, meaning), autoFilled = false)
    }

    /** debounce 后查词典；命中且当前 pos+meaning 都空 / 词典填的 → 自动填。 */
    private fun scheduleDictLookup(text: String, overwriteUserEdit: Boolean) {
        dictLookupJob?.cancel()
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        dictLookupJob = viewModelScope.launch {
            delay(DEBOUNCE_MS)
            val entries = dictionaryDao.findByWord(trimmed.lowercase())
            if (entries.isEmpty()) return@launch
            val first = entries.first()
            val current = _dialog.value as? WordListDialog.Add ?: return@launch
            // 只在允许覆盖（用户新输入尚未手改）时填；用户改过则不打扰
            if (!overwriteUserEdit && !current.autoFilled && (current.pos.isNotBlank() || current.meaning.isNotBlank())) {
                return@launch
            }
            _dialog.value = current.copy(
                pos = first.pos,
                meaning = first.meaning,
                error = validateAddWordInput(current.text, first.pos, first.meaning),
                autoFilled = true,
            )
        }
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

    fun dismissDialog() {
        dictLookupJob?.cancel()
        _dialog.value = WordListDialog.None
    }
}
