package com.github.chsiching.worddrill.ui.library

import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.chsiching.worddrill.R
import com.github.chsiching.worddrill.data.local.dao.BookDao
import com.github.chsiching.worddrill.data.local.dao.BookWithCount
import com.github.chsiching.worddrill.data.local.entity.Book
import com.github.chsiching.worddrill.data.settings.SettingsRepository
import com.github.chsiching.worddrill.data.wordimport.FileNotOpenableException
import com.github.chsiching.worddrill.data.wordimport.FileWordImporter
import com.github.chsiching.worddrill.data.wordimport.ImportSummary
import com.github.chsiching.worddrill.data.wordimport.UnsupportedFileTypeException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface LibraryDialog {
    data object None : LibraryDialog
    data class Create(val name: String = "", @StringRes val error: Int? = null) : LibraryDialog
    data class Rename(val bookId: Long, val name: String = "", @StringRes val error: Int? = null) : LibraryDialog
    data class Delete(val bookId: Long, val name: String) : LibraryDialog
    data class Import(
        val uri: Uri? = null,
        val filename: String? = null,
        val name: String = "",
        @StringRes val error: Int? = null,
        val working: Boolean = false,
        @StringRes val failureMessage: Int? = null,
        val failureDetail: String? = null,
    ) : LibraryDialog
    data class ImportDone(val summary: ImportSummary) : LibraryDialog
}

data class LibraryUiState(
    val books: List<BookWithCount> = emptyList(),
    val currentBookId: Long? = null,
    val dialog: LibraryDialog = LibraryDialog.None,
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val bookDao: BookDao,
    private val settings: SettingsRepository,
    private val fileImporter: FileWordImporter,
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

    fun selectBook(bookId: Long) {
        viewModelScope.launch { settings.setCurrentBookId(bookId) }
    }

    fun openCreateDialog() { _dialog.value = LibraryDialog.Create() }

    fun onNameInput(name: String) {
        val all = uiState.value.books
        val current = (_dialog.value as? LibraryDialog.Rename)?.bookId
        val existing = all.filter { it.bookId != current }.map { it.name }
        val error = validateBookName(name, existing)
        _dialog.value = when (val d = _dialog.value) {
            is LibraryDialog.Create -> d.copy(name = name, error = error)
            is LibraryDialog.Rename -> d.copy(name = name, error = error)
            is LibraryDialog.Import -> d.copy(name = name, error = error)
            is LibraryDialog.Delete -> d
            LibraryDialog.None, is LibraryDialog.ImportDone -> d
        }
    }

    fun submitCreate() {
        val d = _dialog.value as? LibraryDialog.Create ?: return
        if (d.error != null) return
        viewModelScope.launch {
            bookDao.insert(Book(name = d.name.trim(), isPreset = false))
            _dialog.value = LibraryDialog.None
        }
    }

    fun openRenameDialog(bookId: Long) {
        val book = uiState.value.books.firstOrNull { it.bookId == bookId } ?: return
        _dialog.value = LibraryDialog.Rename(bookId = bookId, name = book.name)
    }

    fun submitRename() {
        val d = _dialog.value as? LibraryDialog.Rename ?: return
        if (d.error != null) return
        viewModelScope.launch {
            bookDao.rename(d.bookId, d.name.trim())
            _dialog.value = LibraryDialog.None
        }
    }

    fun openDeleteDialog(bookId: Long) {
        val book = uiState.value.books.firstOrNull { it.bookId == bookId } ?: return
        _dialog.value = LibraryDialog.Delete(bookId = bookId, name = book.name)
    }

    fun submitDelete() {
        val d = _dialog.value as? LibraryDialog.Delete ?: return
        viewModelScope.launch {
            bookDao.deleteCustom(d.bookId)
            _dialog.value = LibraryDialog.None
        }
    }

    fun openImportDialog() { _dialog.value = LibraryDialog.Import() }

    fun onFileSelected(uri: Uri, filename: String) {
        val d = _dialog.value as? LibraryDialog.Import ?: return
        _dialog.value = d.copy(uri = uri, filename = filename, failureMessage = null, failureDetail = null)
    }

    fun submitImport() {
        val d = _dialog.value as? LibraryDialog.Import ?: return
        if (d.error != null) return
        val uri = d.uri ?: return
        val filename = d.filename ?: return
        val name = d.name.trim()
        if (name.isEmpty()) return
        viewModelScope.launch {
            _dialog.value = d.copy(working = true)
            try {
                val summary = fileImporter.import(uri = uri, bookName = name, filenameDecoded = filename)
                _dialog.value = LibraryDialog.ImportDone(summary)
            } catch (e: UnsupportedFileTypeException) {
                _dialog.value = d.copy(
                    working = false,
                    failureMessage = R.string.library_import_failed_file_type,
                    failureDetail = null,
                )
            } catch (e: FileNotOpenableException) {
                _dialog.value = d.copy(
                    working = false,
                    failureMessage = R.string.library_import_failed_open,
                    failureDetail = null,
                )
            } catch (e: Exception) {
                _dialog.value = d.copy(
                    working = false,
                    failureMessage = R.string.library_import_failed,
                    failureDetail = e.message,
                )
            }
        }
    }

    fun dismissDialog() { _dialog.value = LibraryDialog.None }
}
