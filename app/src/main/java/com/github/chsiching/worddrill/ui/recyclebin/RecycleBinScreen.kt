package com.github.chsiching.worddrill.ui.recyclebin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.chsiching.worddrill.R
import com.github.chsiching.worddrill.data.local.dao.DeletedEntry
import com.github.chsiching.worddrill.data.local.entity.Book
import com.github.chsiching.worddrill.ui.theme.wordDrillColors

/** 待永久删除的目标（ sealed 区分词书 / 词条，用于确认对话框文案）。 */
private sealed interface PurgeTarget {
    val displayName: String
    data class BookItem(val bookId: Long, override val displayName: String) : PurgeTarget
    data class EntryItem(val bookId: Long, val wordId: Long, override val displayName: String) : PurgeTarget
}

/**
 * 回收站页面（Ticket #22）：列出软删的词书和词条关联，支持恢复 / 永久删除。
 *
 * 两段列表：先是「词书」段（整本被软删），后是「词条」段（某词书的某词条被软删）。
 * 每项有「恢复」/「永久删除」两个 TextButton；永久删除二次确认（error 色，不可撤销）。
 * 空态：两类都为空时居中显示「回收站为空」。
 *
 * 与 [com.github.chsiching.worddrill.ui.library.WordListScreen] 同风格
 * （内联头部 + 行底分隔线）。
 */
@Composable
fun RecycleBinScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RecycleBinViewModel = hiltViewModel(),
) {
    val books by viewModel.books.collectAsStateWithLifecycle()
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    var pendingPurge by remember { mutableStateOf<PurgeTarget?>(null) }
    val isEmpty = books.isEmpty() && entries.isEmpty()

    Column(modifier = modifier.fillMaxSize()) {
        // 内联头部：back + 标题
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, top = 8.dp, end = 16.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            Text(
                text = stringResource(R.string.recycle_bin_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        if (isEmpty) {
            EmptyState(stringResource(R.string.recycle_bin_empty))
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
            ) {
                if (books.isNotEmpty()) {
                    item {
                        SectionLabel(stringResource(R.string.recycle_bin_section_books))
                    }
                    items(books, key = { "book-${it.bookId}" }) { book ->
                        BookRow(
                            book = book,
                            onRestore = { viewModel.restoreBook(book.bookId) },
                            onPurge = {
                                pendingPurge = PurgeTarget.BookItem(book.bookId, book.name)
                            },
                        )
                    }
                }
                if (entries.isNotEmpty()) {
                    item {
                        SectionLabel(stringResource(R.string.recycle_bin_section_entries))
                    }
                    items(entries, key = { "entry-${it.bookId}-${it.wordId}" }) { entry ->
                        EntryRow(
                            entry = entry,
                            onRestore = { viewModel.restoreEntry(entry.bookId, entry.wordId) },
                            onPurge = {
                                pendingPurge = PurgeTarget.EntryItem(
                                    entry.bookId, entry.wordId, entry.wordText,
                                )
                            },
                        )
                    }
                }
            }
        }
    }

    pendingPurge?.let { target ->
        PurgeConfirmDialog(
            displayName = target.displayName,
            onConfirm = {
                when (target) {
                    is PurgeTarget.BookItem -> viewModel.purgeBook(target.bookId)
                    is PurgeTarget.EntryItem -> viewModel.purgeEntry(target.bookId, target.wordId)
                }
                pendingPurge = null
            },
            onDismiss = { pendingPurge = null },
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 4.dp),
    )
}

@Composable
private fun BookRow(
    book: Book,
    onRestore: () -> Unit,
    onPurge: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp),
    ) {
        Text(
            text = book.name,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(R.string.recycle_bin_book_subtitle, book.name),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp),
        )
        ActionButtons(onRestore = onRestore, onPurge = onPurge)
        RowSeparator()
    }
}

@Composable
private fun EntryRow(
    entry: DeletedEntry,
    onRestore: () -> Unit,
    onPurge: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp),
    ) {
        Text(
            text = entry.wordText,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(R.string.recycle_bin_entry, entry.wordText, entry.bookName),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp),
        )
        ActionButtons(onRestore = onRestore, onPurge = onPurge)
        RowSeparator()
    }
}

@Composable
private fun ActionButtons(onRestore: () -> Unit, onPurge: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        TextButton(onClick = onRestore, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
            Text(stringResource(R.string.recycle_bin_restore), style = MaterialTheme.typography.labelSmall)
        }
        TextButton(onClick = onPurge, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
            Text(
                text = stringResource(R.string.recycle_bin_purge),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun RowSeparator() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.wordDrillColors.separator),
    )
}

/**
 * 永久删除二次确认对话框（Ticket #22）。
 * 与 [com.github.chsiching.worddrill.ui.library.WordListScreen] 的 DeleteWordDialog 同模式，
 * 但提示「不可撤销」（永久删除 = 真 DELETE，无法恢复）。
 */
@Composable
private fun PurgeConfirmDialog(
    displayName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.recycle_bin_purge_title)) },
        text = { Text(stringResource(R.string.recycle_bin_purge_message, displayName)) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
            ) { Text(stringResource(R.string.recycle_bin_purge_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.library_cancel)) }
        },
    )
}

@Composable
private fun EmptyState(text: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
