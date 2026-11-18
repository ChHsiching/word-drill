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
import com.github.chsiching.worddrill.ui.WordOrdering
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

val WORD_LIST_POS_OPTIONS: List<String> = listOf(
    "n.", "v.", "vt.", "vi.", "adj.", "adv.",
    "prep.", "conj.", "pron.", "art.", "num.", "int.",
)

private const val DEBOUNCE_MS = 300L

enum class WordListFillSource { USER, DICTIONARY }

sealed interface WordListDialog {
    data object None : WordListDialog
    data class Add(
        val text: String = "",
        val pos: String = "",
        val meaning: String = "",
        @StringRes val error: Int? = null,

        val fillSource: WordListFillSource = WordListFillSource.USER,
    ) : WordListDialog
    data class Edit(
        val senseId: Long,
        val wordText: String,
        val pos: String = "",
        val meaning: String = "",
        @StringRes val error: Int? = null,
    ) : WordListDialog

    data class Delete(val wordId: Long, val wordText: String) : WordListDialog
}

/** Case-insensitive substring filter behind the word-list search bar (issue #51);
 *  an empty query keeps every word. */
internal fun filterWords(words: List<WordWithSenses>, query: String): List<WordWithSenses> =
    if (query.isEmpty()) words
    else words.filter { it.word.text.contains(query, ignoreCase = true) }

data class WordListUiState(
    val bookName: String = "",
    val isPreset: Boolean = false,
    val words: List<WordWithSenses> = emptyList(),
    val searchQuery: String = "",
    val dialog: WordListDialog = WordListDialog.None,
)

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

    // ViewModel state, so the search survives tab switches within the app session
    // (saveState/restoreState navigation) but not process death (issue #51).
    private val _searchQuery = MutableStateFlow("")

    private val _bookName = MutableStateFlow("")
    private val _isPreset = MutableStateFlow(false)

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
        _searchQuery,
        _dialog,
    ) { words, bookName, isPreset, searchQuery, dialog ->
        WordListUiState(
            bookName = bookName,
            isPreset = isPreset,
            words = filterWords(WordOrdering.alphabetical(words), searchQuery),
            searchQuery = searchQuery,
            dialog = dialog,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = WordListUiState(),
    )

    fun onSearchInput(query: String) {
        _searchQuery.value = query
    }

    fun openAddDialog() { _dialog.value = WordListDialog.Add() }

    fun onTextInput(text: String) {
        val current = _dialog.value as? WordListDialog.Add ?: return
        val keepUserEdit = current.fillSource == WordListFillSource.USER &&
            (current.pos.isNotBlank() || current.meaning.isNotBlank())
        _dialog.value = current.copy(
            text = text,
            error = validateAddWordInput(text, current.pos, current.meaning),
            pos = if (keepUserEdit) current.pos else "",
            meaning = if (keepUserEdit) current.meaning else "",
            fillSource = WordListFillSource.USER,
        )
        scheduleDictLookup(text, overwriteUserEdit = !keepUserEdit)
    }

    fun onPosInput(pos: String) {
        val current = _dialog.value as? WordListDialog.Add ?: return
        _dialog.value = current.copy(pos = pos, error = validateAddWordInput(current.text, pos, current.meaning), fillSource = WordListFillSource.USER)
    }

    fun onMeaningInput(meaning: String) {
        val current = _dialog.value as? WordListDialog.Add ?: return
        _dialog.value = current.copy(meaning = meaning, error = validateAddWordInput(current.text, current.pos, meaning), fillSource = WordListFillSource.USER)
    }

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
            if (!overwriteUserEdit && current.fillSource == WordListFillSource.USER &&
                (current.pos.isNotBlank() || current.meaning.isNotBlank())
            ) {
                return@launch
            }
            _dialog.value = current.copy(
                pos = first.pos,
                meaning = first.meaning,
                error = validateAddWordInput(current.text, first.pos, first.meaning),
                fillSource = WordListFillSource.DICTIONARY,
            )
        }
    }

    fun submitAdd() {
        val d = _dialog.value as? WordListDialog.Add ?: return
        if (d.error != null) return
        val text = d.text.trim()
        val pos = d.pos.trim()
        val meaning = d.meaning.trim()
        viewModelScope.launch {
            db.withTransaction {
                val wordId = wordDao.findOrInsertWord(text, null)
                wordDao.insertSense(Sense(wordId = wordId, pos = pos, meaning = meaning))
                bookDao.linkBookWord(BookWord(bookId = bookId, wordId = wordId))
            }
            _dialog.value = WordListDialog.None
        }
    }

    fun openEditDialog(senseId: Long, wordText: String, pos: String, meaning: String) {
        _dialog.value = WordListDialog.Edit(
            senseId = senseId, wordText = wordText, pos = pos, meaning = meaning,
        )
    }

    fun onEditInput(pos: String, meaning: String) {
        val error = validateSenseEditInput(pos, meaning)
        _dialog.value = (_dialog.value as? WordListDialog.Edit)
            ?.copy(pos = pos, meaning = meaning, error = error)
            ?: WordListDialog.None
    }

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

    fun openDeleteDialog(wordId: Long, wordText: String) {
        _dialog.value = WordListDialog.Delete(wordId = wordId, wordText = wordText)
    }

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
