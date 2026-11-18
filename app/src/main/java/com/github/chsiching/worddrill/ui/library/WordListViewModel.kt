package com.github.chsiching.worddrill.ui.library

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.github.chsiching.worddrill.data.ProgressResetter
import com.github.chsiching.worddrill.data.local.WordDrillDatabase
import com.github.chsiching.worddrill.data.local.WordWithSenses
import com.github.chsiching.worddrill.data.local.dao.BookDao
import com.github.chsiching.worddrill.data.local.dao.REVIEW_BOOK_NAME
import com.github.chsiching.worddrill.data.local.dao.DictionaryDao
import com.github.chsiching.worddrill.data.local.dao.WordDao
import com.github.chsiching.worddrill.data.local.entity.BookWord
import com.github.chsiching.worddrill.data.local.entity.Sense
import com.github.chsiching.worddrill.data.settings.DeckOrdering
import com.github.chsiching.worddrill.data.settings.SettingsRepository
import com.github.chsiching.worddrill.ui.DeckSequenceState
import com.github.chsiching.worddrill.ui.WordOrdering
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
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
    data object Reset : WordListDialog
}

/** Case-insensitive substring filter behind the word-list search bar (issue #51);
 *  an empty query keeps every word. */
internal fun filterWords(words: List<WordWithSenses>, query: String): List<WordWithSenses> =
    if (query.isEmpty()) words
    else words.filter { it.word.text.contains(query, ignoreCase = true) }

/** Letter index bar jump map (issue #60): first displayed index per A–Z section,
 *  case-insensitive. Words with a non-A–Z initial belong to no section. */
internal fun letterAnchors(words: List<WordWithSenses>): Map<Char, Int> {
    val anchors = linkedMapOf<Char, Int>()
    words.forEachIndexed { index, wordWithSenses ->
        val initial = wordWithSenses.word.text.firstOrNull()?.uppercaseChar()
        if (initial != null && initial in 'A'..'Z') {
            anchors.putIfAbsent(initial, index)
        }
    }
    return anchors
}

data class WordListUiState(
    val bookName: String = "",
    val isPreset: Boolean = false,
    val isReviewBook: Boolean = false,
    val words: List<WordWithSenses> = emptyList(),
    val searchQuery: String = "",
    val dialog: WordListDialog = WordListDialog.None,
    /** Progress header (issue #60). [resumePosition] is 1-based like the drill's
     *  card counter; 0 only when the deck is empty. [deckSize] is the book's whole
     *  deck — the search filter narrows [words], never these numbers. */
    val completionCount: Int = 0,
    val deckSize: Int = 0,
    val resumePosition: Int = 0,
    /** Ordering the displayed list is in (issue #60): the dealt mode when the
     *  drill published a sequence this session, alphabetical otherwise. The letter
     * bar shows only in ALPHABETICAL — shuffle has no meaningful sections. */
    val deckOrdering: DeckOrdering = DeckOrdering.ALPHABETICAL,
)

/** Book-level inputs of the header, nested so the outer combine stays within the
 *  five-flow overload. */
private data class BookHeaderInputs(
    val bookName: String,
    val isPreset: Boolean,
    val isReviewBook: Boolean,
    val completionCount: Int,
    val resumeWordId: Long?,
)

@HiltViewModel
class WordListViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val db: WordDrillDatabase,
    private val wordDao: WordDao,
    private val bookDao: BookDao,
    private val dictionaryDao: DictionaryDao,
    private val settings: SettingsRepository,
    private val progressResetter: ProgressResetter,
    private val deckSequence: DeckSequenceState,
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
    private val _isReviewBook = MutableStateFlow(false)

    private var dictLookupJob: Job? = null

    init {
        viewModelScope.launch {
            val book = bookDao.getById(bookId)
            if (book != null) {
                _bookName.value = book.name
                _isPreset.value = book.isPreset
                _isReviewBook.value = book.name == REVIEW_BOOK_NAME
            }
        }
    }

    val uiState: StateFlow<WordListUiState> = combine(
        wordDao.observeWordsWithSensesByBook(bookId),
        // Book meta plus the live header inputs (issue #60) nested so the outer
        // combine stays within the five-flow overload.
        combine(
            _bookName,
            _isPreset,
            _isReviewBook,
            bookDao.observeCompletionCount(bookId),
            settings.resumeWordIdFlow(bookId),
        ) { bookName, isPreset, isReviewBook, completionCount, resumeWordId ->
            BookHeaderInputs(bookName, isPreset, isReviewBook, completionCount, resumeWordId)
        },
        _searchQuery,
        _dialog,
        // Issue #54: the list mirrors the deck's published card sequence instead of
        // sorting on its own, so the two surfaces show one order. The lookup is
        // de-duplicated: another book's publish must not re-deal this list.
        deckSequence.sequences.map { it[bookId] }.distinctUntilChanged(),
    ) { words, headerInputs, searchQuery, dialog, dealtDeck ->
        val ordered = WordOrdering.byDeckSequence(words, dealtDeck?.wordIds)
        WordListUiState(
            bookName = headerInputs.bookName,
            isPreset = headerInputs.isPreset,
            isReviewBook = headerInputs.isReviewBook,
            words = filterWords(ordered, searchQuery),
            searchQuery = searchQuery,
            dialog = dialog,
            completionCount = headerInputs.completionCount,
            deckSize = ordered.size,
            resumePosition = if (ordered.isEmpty()) 0
            else WordOrdering.resumeIndex(headerInputs.resumeWordId, ordered) + 1,
            deckOrdering = dealtDeck?.ordering ?: DeckOrdering.ALPHABETICAL,
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

    /** Progress reset (GLOSSARY.md), first entry point (#52): the reusable engine
     *  restores the deck, clears the resume position and trims the review book. */
    fun openResetDialog() {
        _dialog.value = WordListDialog.Reset
    }

    fun submitReset() {
        if (_dialog.value !is WordListDialog.Reset) return
        viewModelScope.launch {
            progressResetter.reset(bookId)
            _dialog.value = WordListDialog.None
        }
    }

    fun dismissDialog() {
        dictLookupJob?.cancel()
        _dialog.value = WordListDialog.None
    }
}
