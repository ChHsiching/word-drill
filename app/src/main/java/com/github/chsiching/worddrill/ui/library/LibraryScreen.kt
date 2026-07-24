package com.github.chsiching.worddrill.ui.library

import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.chsiching.worddrill.R
import com.github.chsiching.worddrill.data.local.dao.BookWithCount
import com.github.chsiching.worddrill.ui.theme.wordDrillColors

/**
 * 「库」Tab（Ticket #6 + #16 重写）：词书列表 + 切换/新建/重命名/删除。
 *
 * 设计稿（#16）：去掉 CenterAlignedTopAppBar，改大标题；纯文字列表（无前置 icon）；
 * 选中态用浅灰背景（chipBg）+ check + 文字加粗；原地选中不跳转。
 *
 * 保留 Ticket #6/#11 的能力：
 * - 点行主体 → 设为当前词书（写 DataStore，「刷」Tab 监听同源 Flow 立即重载）
 * - 点行尾 chevron → 进入词书内词条列表（[WordListScreen]）
 * - 自定义词书：重命名/删除入口（保留原 TextButton，与既有交互一致）
 *
 * 预置词书不显示重命名/删除（isPreset 判定，DAO 层 rename/deleteCustom 也有 isPreset=0 兜底）。
 */
@Composable
fun LibraryScreen(
    onOpenBook: (bookId: Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 120.dp),
        ) {
            item {
                Text(
                    text = stringResource(R.string.library_large_title),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = 24.dp, top = 24.dp, bottom = 24.dp, end = 24.dp),
                )
            }
            if (state.books.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.library_title),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(24.dp),
                    )
                }
            } else {
                items(state.books, key = { it.bookId }) { book ->
                    BookRow(
                        book = book,
                        isCurrent = book.bookId == state.currentBookId,
                        onOpen = { onOpenBook(book.bookId) },
                        onSelectCurrent = { viewModel.selectBook(book.bookId) },
                        onRename = { viewModel.openRenameDialog(book.bookId) },
                        onDelete = { viewModel.deleteBook(book.bookId) },
                    )
                }
            }
            item { Spacer(Modifier.size(20.dp)) }
            item {
                AddBookButton(
                    onClick = viewModel::openCreateDialog,
                    modifier = Modifier.padding(horizontal = 28.dp),
                )
            }
        }
    }

    when (val dialog = state.dialog) {
        LibraryDialog.None -> Unit
        is LibraryDialog.Create -> BookNameDialog(
            title = stringResource(R.string.library_create),
            name = dialog.name,
            error = dialog.error,
            onNameInput = viewModel::onNameInput,
            onConfirm = viewModel::submitCreate,
            onDismiss = viewModel::dismissDialog,
        )
        is LibraryDialog.Rename -> BookNameDialog(
            title = stringResource(R.string.library_rename),
            name = dialog.name,
            error = dialog.error,
            onNameInput = viewModel::onNameInput,
            onConfirm = viewModel::submitRename,
            onDismiss = viewModel::dismissDialog,
        )
    }
}

/**
 * 词书行：纯文字（书名 + 副标题）+ 选中态 chipBg 背景 + check。
 * 点行主体 = 设为当前；尾 chevron = 进词条列表；自定义词书的重命名/删除 TextButton。
 */
@Composable
private fun BookRow(
    book: BookWithCount,
    isCurrent: Boolean,
    onOpen: () -> Unit,
    onSelectCurrent: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    val transparent = MaterialTheme.colorScheme.background
    val rowBg by animateColorAsState(
        targetValue = if (isCurrent) MaterialTheme.wordDrillColors.chipBg else transparent,
        label = "bookRowBg",
    )
    Surface(
        color = rowBg,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onSelectCurrent)
                .padding(start = 16.dp, top = 14.dp, end = 8.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = book.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(
                        R.string.library_word_count_format,
                        book.wordCount,
                        stringResource(if (book.isPreset) R.string.library_preset_badge else R.string.library_custom_badge),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
                // 自定义词书的重命名/删除入口（TextButton，贴副标题下方，与既有交互一致）
                if (!book.isPreset) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = onRename, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                            Text(stringResource(R.string.library_rename), style = MaterialTheme.typography.labelSmall)
                        }
                        TextButton(onClick = onDelete, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                            Text(stringResource(R.string.library_delete), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
            // 选中态 check（secondary 色，无反色）
            if (isCurrent) {
                Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = stringResource(R.string.library_current_badge),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
            // 尾 chevron —— 进词条列表
            IconButton(onClick = onOpen) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                    contentDescription = stringResource(R.string.library_title),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 新建词书按钮：chipBg 背景，圆角 14，Plus 图标 + 文案居中。 */
@Composable
private fun AddBookButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.wordDrillColors.chipBg,
        shape = RoundedCornerShape(14.dp),
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(15.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.library_create),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun BookNameDialog(
    title: String,
    name: String,
    @StringRes error: Int?,
    onNameInput: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = onNameInput,
                    label = { Text(stringResource(R.string.library_name_hint)) },
                    singleLine = true,
                    isError = error != null,
                    supportingText = if (error != null) ({ Text(stringResource(error)) }) else null,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = error == null && name.isNotBlank()) {
                Text(stringResource(R.string.library_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.library_cancel)) }
        },
    )
}
