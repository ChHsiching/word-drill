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
 * Fixed POS dropdown options for manual word entry (issue #9 verbatim, 12 common POS).
 * File import's [com.github.chsiching.worddrill.data.wordimport.PosMeaningParser] supports
 * more (17), but manual entry is a high-frequency interaction and a shorter dropdown is
 * easier to pick from, so the issue's list of 12 is used.
 */
val WORD_LIST_POS_OPTIONS: List<String> = listOf(
    "n.", "v.", "vt.", "vi.", "adj.", "adv.",
    "prep.", "conj.", "pron.", "art.", "num.", "int.",
)

/** Dictionary-lookup debounce: query the database only after the user stops typing for [DEBOUNCE_MS], avoiding one query per character. */
private const val DEBOUNCE_MS = 300L

/**
 * Secondary page of the "Library" tab: dialog states for a book's word list.
 * - [Add]: add a word (text + pos + meaning; since #9, entering text auto-looks-up the
 *   dictionary to fill pos+meaning)
 * - [Edit]: edit one sense (pos + meaning; wordText shown read-only)
 * - [Delete]: delete-word double confirmation (Ticket #22, soft-delete into the recycle
 *   bin, restorable)
 */
sealed interface WordListDialog {
    data object None : WordListDialog
    data class Add(
        val text: String = "",
        val pos: String = "",
        val meaning: String = "",
        @StringRes val error: Int? = null,
        /** Auto-fill flag: true means pos/meaning were auto-filled from the dictionary; the user can still edit them by hand. */
        val autoFilled: Boolean = false,
    ) : WordListDialog
    data class Edit(
        val senseId: Long,
        val wordText: String,
        val pos: String = "",
        val meaning: String = "",
        @StringRes val error: Int? = null,
    ) : WordListDialog
    /** Ticket #22: delete-word double confirmation (wordText is used in the dialog text). */
    data class Delete(val wordId: Long, val wordText: String) : WordListDialog
}

/**
 * Word-list page UI state. bookName/isPreset are loaded once in init (constant for the
 * same destination).
 */
data class WordListUiState(
    val bookName: String = "",
    val isPreset: Boolean = false,
    val words: List<WordWithSenses> = emptyList(),
    val dialog: WordListDialog = WordListDialog.None,
)

/**
 * ViewModel for a book's word list (Ticket #7 + #9 POS dropdown and dictionary lookup).
 *
 * - bookId comes from [SavedStateHandle] (navigation-compose syncs the composable route's
 *   nav arg into the same-source SavedStateHandle; `hiltViewModel()` binds to the current
 *   backStackEntry by default).
 * - The word list subscribes to [WordDao.observeWordsWithSensesByBook]; inserts, deletes,
 *   and edits re-emit automatically.
 * - Add: reuse the global word pool ([WordDao.findIdByText]), write the sense, then
 *   linkBookWord; the whole step is one transaction.
 * - **Ticket #9**: after [onTextInput], a debounced [DictionaryDao] lookup auto-fills pos
 *   + meaning on a hit (autoFilled=true flag; the user can still edit afterwards).
 * - Edit: [WordDao.updateSense].
 * - Delete (Ticket #22): [BookDao.setDeleted] soft-deletes book_word.deleted=1
 *   (book-level: only this book's link changes; deleting apple in CET-4 does not affect
 *   CET-6's link to the same word). The word goes to the recycle bin and is restorable.
 * - Preset books: the UI decides from isPreset whether to render the add/edit/delete
 *   buttons (the DAO layer does not block; the spec only requires the UI to be inert).
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

    /** Current debounced dictionary-lookup coroutine; a new input cancels the previous one. */
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

    // ---- add word ----

    fun openAddDialog() { _dialog.value = WordListDialog.Add() }

    /**
     * Word text input change (called from onValueChange).
     *
     * Separate from [onPosInput] / [onMeaningInput]: a text change triggers the debounced
     * dictionary lookup. On a hit, pos + meaning are auto-filled (autoFilled=true). Values
     * the user hand-entered (autoFilled=false) are not overwritten (so the dictionary does
     * not clobber a pos the user just changed).
     */
    fun onTextInput(text: String) {
        val current = _dialog.value as? WordListDialog.Add ?: return
        // Once the user has hand-edited pos/meaning (autoFilled=false), dictionary auto-fill no longer overrides them
        val keepUserEdit = !current.autoFilled && (current.pos.isNotBlank() || current.meaning.isNotBlank())
        _dialog.value = current.copy(
            text = text,
            error = validateAddWordInput(text, current.pos, current.meaning),
            // Clear fields the user has not edited yet, so a dictionary hit can refill them
            pos = if (keepUserEdit) current.pos else "",
            meaning = if (keepUserEdit) current.meaning else "",
            autoFilled = false,
        )
        scheduleDictLookup(text, overwriteUserEdit = !keepUserEdit)
    }

    /** POS input change (user picks/edits by hand; autoFilled immediately set to false). */
    fun onPosInput(pos: String) {
        val current = _dialog.value as? WordListDialog.Add ?: return
        _dialog.value = current.copy(pos = pos, error = validateAddWordInput(current.text, pos, current.meaning), autoFilled = false)
    }

    /** Meaning input change (same as [onPosInput]). */
    fun onMeaningInput(meaning: String) {
        val current = _dialog.value as? WordListDialog.Add ?: return
        _dialog.value = current.copy(meaning = meaning, error = validateAddWordInput(current.text, current.pos, meaning), autoFilled = false)
    }

    /** Debounced dictionary lookup; on a hit, auto-fill when pos+meaning are both empty or dictionary-filled. */
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
            // Fill only when overwriting is allowed (the new input has not been hand-edited); leave the user's edits alone
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

    /** Submits add: after validation, one transaction writes word (reuse/create) + sense + linkBookWord. */
    fun submitAdd() {
        val d = _dialog.value as? WordListDialog.Add ?: return
        if (d.error != null) return // validateAddWordInput already checks after trim
        val text = d.text.trim()
        val pos = d.pos.trim()
        val meaning = d.meaning.trim()
        viewModelScope.launch {
            db.withTransaction {
                // 1) Dedup against the global word pool (reuse an existing wordId, else create; IGNORE fallback: -1 triggers a re-query)
                val wordId = wordDao.findIdByText(text)
                    ?: wordDao.insert(Word(text = text)).let { id ->
                        if (id == -1L) wordDao.findIdByText(text)!! else id
                    }
                // 2) Write the sense ((wordId, pos) unique; IGNORE covers duplicate POS)
                wordDao.insertSense(Sense(wordId = wordId, pos = pos, meaning = meaning))
                // 3) Link to the current book
                bookDao.linkBookWord(BookWord(bookId = bookId, wordId = wordId))
            }
            _dialog.value = WordListDialog.None
        }
    }

    // ---- edit sense ----

    fun openEditDialog(senseId: Long, wordText: String, pos: String, meaning: String) {
        _dialog.value = WordListDialog.Edit(
            senseId = senseId, wordText = wordText, pos = pos, meaning = meaning,
        )
    }

    /** Updates the edit inputs as they change and validates. */
    fun onEditInput(pos: String, meaning: String) {
        val error = validateSenseEditInput(pos, meaning)
        _dialog.value = (_dialog.value as? WordListDialog.Edit)
            ?.copy(pos = pos, meaning = meaning, error = error)
            ?: WordListDialog.None
    }

    /** Submits edit: after validation, updateSense. */
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

    // ---- remove word from book (Ticket #22: soft-delete into the recycle bin, restorable) ----

    /** Opens the delete-confirmation dialog (wordText is used in the dialog text). */
    fun openDeleteDialog(wordId: Long, wordText: String) {
        _dialog.value = WordListDialog.Delete(wordId = wordId, wordText = wordText)
    }

    /** Confirms deletion: soft-delete book_word.deleted=1 (book-level: only this book's link changes). */
    fun submitDelete() {
        val d = _dialog.value as? WordListDialog.Delete ?: return
        viewModelScope.launch {
            bookDao.setDeleted(bookId, d.wordId, deleted = true)
            _dialog.value = WordListDialog.None
        }
    }

    fun dismissDialog() {
        dictLookupJob?.cancel()
        _dialog.value = WordListDialog.None
    }
}
