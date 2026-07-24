package com.github.chsiching.worddrill.ui.library

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.chsiching.worddrill.data.local.dao.BookDao
import com.github.chsiching.worddrill.data.local.dao.BookWithCount
import com.github.chsiching.worddrill.data.local.entity.Book
import com.github.chsiching.worddrill.data.settings.SettingsRepository
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
 * 「库」Tab 对话框状态：未打开 / 新建 / 重命名 / 删除确认（带目标 bookId + 书名）。
 */
sealed interface LibraryDialog {
    data object None : LibraryDialog
    data class Create(val name: String = "", @StringRes val error: Int? = null) : LibraryDialog
    data class Rename(val bookId: Long, val name: String = "", @StringRes val error: Int? = null) : LibraryDialog
    data class Delete(val bookId: Long, val name: String) : LibraryDialog
}

/**
 * 「库」Tab 的 UI 状态：词书列表（含词条数）+ 当前选中 + 对话框态。
 * books 与 currentBookId 合并成一个 Flow，任一变化列表整体重渲染。
 */
data class LibraryUiState(
    val books: List<BookWithCount> = emptyList(),
    val currentBookId: Long? = null,
    val dialog: LibraryDialog = LibraryDialog.None,
)

/**
 * 「库」Tab 的 ViewModel。
 *
 * - 列表由 [BookDao.observeAll] 驱动（预置在前、按 name 升序）
 * - 当前选中由 [SettingsRepository.currentBookId] 标记，切换写入 DataStore
 *   → 「刷」Tab 监听同源 Flow 自动重载（规格：「刷」Tab 刷卡内容立即切换）
 * - 新建：显式查重（不靠 @Insert REPLACE 去重，交接链坑 C）
 * - 删除：二次确认后走 [BookDao.deleteCustom]（预置由 SQL 的 isPreset=0 兜底）
 */
@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val bookDao: BookDao,
    private val settings: SettingsRepository,
) : ViewModel() {

    private val _dialog = MutableStateFlow<LibraryDialog>(LibraryDialog.None)
    val dialog: StateFlow<LibraryDialog> = _dialog.asStateFlow()

    val uiState: StateFlow<LibraryUiState> = combine(
        bookDao.observeAllWithCounts(),
        settings.currentBookId,
        _dialog,
    ) { books, currentBookId, dialog ->
        LibraryUiState(books = books, currentBookId = currentBookId, dialog = dialog)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = LibraryUiState(),
    )

    // ---- 切换当前词书 ----

    /** 设为当前词书：写 DataStore，「刷」Tab 监听同源 Flow 立即重载。 */
    fun selectBook(bookId: Long) {
        viewModelScope.launch { settings.setCurrentBookId(bookId) }
    }

    // ---- 新建 ----

    fun openCreateDialog() { _dialog.value = LibraryDialog.Create() }

    /** 实时校验输入（对话框内 TextField onValueChange 调用）。重命名排除自身。 */
    fun onNameInput(name: String) {
        val all = uiState.value.books
        val current = (_dialog.value as? LibraryDialog.Rename)?.bookId
        val existing = all.filter { it.bookId != current }.map { it.name }
        val error = validateBookName(name, existing)
        _dialog.value = when (val d = _dialog.value) {
            is LibraryDialog.Create -> d.copy(name = name, error = error)
            is LibraryDialog.Rename -> d.copy(name = name, error = error)
            is LibraryDialog.Delete -> d // 删除确认对话框无输入框，不响应名称输入
            LibraryDialog.None -> d
        }
    }

    /** 提交新建：通过校验才写库；预置/自定义重名一律拒绝。 */
    fun submitCreate() {
        val d = _dialog.value as? LibraryDialog.Create ?: return
        if (d.error != null) return // validateBookName 已在 trim 后校验，通过即非空
        viewModelScope.launch {
            bookDao.insert(Book(name = d.name.trim(), isPreset = false))
            _dialog.value = LibraryDialog.None
        }
    }

    // ---- 重命名 ----

    fun openRenameDialog(bookId: Long) {
        val book = uiState.value.books.firstOrNull { it.bookId == bookId } ?: return
        _dialog.value = LibraryDialog.Rename(bookId = bookId, name = book.name)
    }

    /** 提交重命名：排除自身后查重；通过校验才写库。DAO 层对预置词书兜底拒绝（返回 0）。 */
    fun submitRename() {
        val d = _dialog.value as? LibraryDialog.Rename ?: return
        if (d.error != null) return // validateBookName 已在 trim 后校验，通过即非空
        viewModelScope.launch {
            bookDao.rename(d.bookId, d.name.trim())
            _dialog.value = LibraryDialog.None
        }
    }

    // ---- 删除（仅自定义，带二次确认 #18）----

    /** 打开删除确认对话框（书名用于对话框文案）。 */
    fun openDeleteDialog(bookId: Long) {
        val book = uiState.value.books.firstOrNull { it.bookId == bookId } ?: return
        _dialog.value = LibraryDialog.Delete(bookId = bookId, name = book.name)
    }

    /** 确认删除：DAO 层已用 isPreset=0 兜底，预置词书不会被删。 */
    fun submitDelete() {
        val d = _dialog.value as? LibraryDialog.Delete ?: return
        viewModelScope.launch {
            bookDao.deleteCustom(d.bookId)
            _dialog.value = LibraryDialog.None
        }
    }

    fun dismissDialog() { _dialog.value = LibraryDialog.None }
}
