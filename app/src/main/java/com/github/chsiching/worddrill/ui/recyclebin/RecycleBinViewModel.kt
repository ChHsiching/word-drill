package com.github.chsiching.worddrill.ui.recyclebin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.chsiching.worddrill.data.local.dao.BookDao
import com.github.chsiching.worddrill.data.local.dao.DeletedEntry
import com.github.chsiching.worddrill.data.local.entity.Book
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 回收站页面（Ticket #22）：列出 deleted=1 的词书和词书-词条关联，支持恢复 / 永久删除。
 *
 * 两类条目：
 * - **词书**（[books]）：整本词书被软删（[Book.deleted]=1）。恢复 = 整本书回列表；
 *   永久删除 = 真 DELETE book（CASCADE 清其下 book_word 关联）。
 * - **词条关联**（[entries]）：某词书的某词条关联被软删（[com.github.chsiching.worddrill.data.local.entity.BookWord.deleted]=1）。
 *   恢复 = 该词回原词书；永久删除 = 真 DELETE 该关联行。
 *
 * 词书永久删除会 CASCADE 清掉其下所有关联（含软删的），所以词书段和词条段不会出现
 * 「词书已永久删但词条还在回收站」的不一致 —— INNER JOIN book/word 保证孤儿条目不展示。
 */
@HiltViewModel
class RecycleBinViewModel @Inject constructor(
    private val bookDao: BookDao,
) : ViewModel() {

    val books: StateFlow<List<Book>> = bookDao.observeDeletedBooks()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    val entries: StateFlow<List<DeletedEntry>> = bookDao.observeDeletedEntries()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    // ---- 词书恢复 / 永久删除 ----

    /** 恢复词书：deleted 置 0，词书回到词库列表。 */
    fun restoreBook(bookId: Long) {
        viewModelScope.launch { bookDao.restoreBook(bookId) }
    }

    /** 永久删除词书：真 DELETE（CASCADE 清关联，调用前应由 UI 弹二次确认）。 */
    fun purgeBook(bookId: Long) {
        viewModelScope.launch { bookDao.purgeBook(bookId) }
    }

    // ---- 词条关联恢复 / 永久删除 ----

    /** 恢复词条关联：deleted 置 0，词回到原词书列表。 */
    fun restoreEntry(bookId: Long, wordId: Long) {
        viewModelScope.launch { bookDao.setDeleted(bookId, wordId, deleted = false) }
    }

    /** 永久删除词条关联：真 DELETE 关联行（不可恢复，调用前应由 UI 弹二次确认）。 */
    fun purgeEntry(bookId: Long, wordId: Long) {
        viewModelScope.launch { bookDao.purgeDeleted(bookId, wordId) }
    }
}
