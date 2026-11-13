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

/**
 * Dialog states of the "Library" tab: none / create / rename / delete confirm / import
 * from file / import done.
 *
 * - [Import]: pick a file + enter a book name, then submit (working=true means the
 *   database write is in progress)
 * - [ImportDone]: import finished (carrying [ImportSummary]); the Composable shows it and
 *   then dismisses
 */
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

/**
 * UI state of the "Library" tab: book list (with word counts) + current selection + dialog
 * state. books and currentBookId are merged into one Flow; any change re-renders the whole
 * list.
 */
data class LibraryUiState(
    val books: List<BookWithCount> = emptyList(),
    val currentBookId: Long? = null,
    val dialog: LibraryDialog = LibraryDialog.None,
)

/**
 * ViewModel of the "Library" tab.
 *
 * - The list is driven by [BookDao.observeAll] (presets first, ascending by name)
 * - The current selection is tracked via [SettingsRepository.currentBookId]; a switch
 *   writes to DataStore, and the "Drill" tab observes the same-source Flow and reloads
 *   automatically (spec: the "Drill" tab's card content switches immediately)
 * - Create: explicit duplicate check (no relying on @Insert REPLACE dedup; handoff-chain
 *   pitfall C)
 * - Delete: double confirmation then [BookDao.deleteCustom] (presets are guarded by the
 *   SQL isPreset=0 condition)
 * - File import (#21): SAF picks a Uri + book name entry, then [FileWordImporter.import],
 *   then ImportDone
 */
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

    // ---- switch current book ----

    /** Sets the current book: writes DataStore; the "Drill" tab observes the same-source Flow and reloads immediately. */
    fun selectBook(bookId: Long) {
        viewModelScope.launch { settings.setCurrentBookId(bookId) }
    }

    // ---- create ----

    fun openCreateDialog() { _dialog.value = LibraryDialog.Create() }

    /** Validates input as it changes (called by the dialog TextField's onValueChange). Rename excludes the book itself. */
    fun onNameInput(name: String) {
        val all = uiState.value.books
        val current = (_dialog.value as? LibraryDialog.Rename)?.bookId
        val existing = all.filter { it.bookId != current }.map { it.name }
        val error = validateBookName(name, existing)
        _dialog.value = when (val d = _dialog.value) {
            is LibraryDialog.Create -> d.copy(name = name, error = error)
            is LibraryDialog.Rename -> d.copy(name = name, error = error)
            is LibraryDialog.Import -> d.copy(name = name, error = error)
            is LibraryDialog.Delete -> d // delete-confirm dialog has no input field; ignore name input
            LibraryDialog.None, is LibraryDialog.ImportDone -> d
        }
    }

    /** Submits creation: writes only after validation passes; duplicates rejected for both preset and custom books. */
    fun submitCreate() {
        val d = _dialog.value as? LibraryDialog.Create ?: return
        if (d.error != null) return // validateBookName already checks after trim; passing means non-empty
        viewModelScope.launch {
            bookDao.insert(Book(name = d.name.trim(), isPreset = false))
            _dialog.value = LibraryDialog.None
        }
    }

    // ---- rename ----

    fun openRenameDialog(bookId: Long) {
        val book = uiState.value.books.firstOrNull { it.bookId == bookId } ?: return
        _dialog.value = LibraryDialog.Rename(bookId = bookId, name = book.name)
    }

    /** Submits rename: duplicate check excludes the book itself; writes only after validation passes. The DAO layer rejects preset books as a fallback (returns 0). */
    fun submitRename() {
        val d = _dialog.value as? LibraryDialog.Rename ?: return
        if (d.error != null) return // validateBookName already checks after trim; passing means non-empty
        viewModelScope.launch {
            bookDao.rename(d.bookId, d.name.trim())
            _dialog.value = LibraryDialog.None
        }
    }

    // ---- delete (custom books only, double confirmation #18) ----

    /** Opens the delete-confirmation dialog (the book name is used in the dialog text). */
    fun openDeleteDialog(bookId: Long) {
        val book = uiState.value.books.firstOrNull { it.bookId == bookId } ?: return
        _dialog.value = LibraryDialog.Delete(bookId = bookId, name = book.name)
    }

    /** Confirms deletion: the DAO layer guards with isPreset=0, so preset books are never deleted. */
    fun submitDelete() {
        val d = _dialog.value as? LibraryDialog.Delete ?: return
        viewModelScope.launch {
            bookDao.deleteCustom(d.bookId)
            _dialog.value = LibraryDialog.None
        }
    }

    // ---- file import (Ticket #21) ----

    fun openImportDialog() { _dialog.value = LibraryDialog.Import() }

    /** SAF callback: after the user picks a file, the Composable passes in the Uri + filename (read from Cursor DISPLAY_NAME). */
    fun onFileSelected(uri: Uri, filename: String) {
        val d = _dialog.value as? LibraryDialog.Import ?: return
        _dialog.value = d.copy(uri = uri, filename = filename, failureMessage = null, failureDetail = null)
    }

    /** Submits import: runs [FileWordImporter.import] only after validation (name + a file must be picked). */
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
