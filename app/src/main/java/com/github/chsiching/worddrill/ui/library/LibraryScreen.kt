package com.github.chsiching.worddrill.ui.library

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.chsiching.worddrill.R
import com.github.chsiching.worddrill.data.local.entity.Book

/**
 * 「库」Tab：词书列表 + 切换/新建/重命名/删除。
 *
 * - 列表项：词书名 + 预置/自定义标识 + 当前选中高亮 + 重命名/删除入口
 * - 点击项设为当前词书（写 DataStore，「刷」Tab 监听同源 Flow 立即重载）
 * - 预置词书不显示重命名/删除按钮（isPreset 判定，DAO 层 rename/deleteCustom 也有 isPreset=0 兜底）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onOpenBook: (bookId: Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.library_title)) },
                actions = {
                    IconButton(onClick = viewModel::openCreateDialog) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = stringResource(R.string.library_create),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        if (state.books.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.library_title),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
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

@Composable
private fun BookRow(
    book: Book,
    isCurrent: Boolean,
    onOpen: () -> Unit,
    onSelectCurrent: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    ListItem(
        headlineContent = {
            Text(
                text = book.name,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpen),
            )
        },
        supportingContent = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AssistChip(
                    onClick = onSelectCurrent,
                    label = {
                        Text(
                            if (book.isPreset) stringResource(R.string.library_preset_badge)
                            else stringResource(R.string.library_custom_badge)
                        )
                    },
                    colors = AssistChipDefaults.assistChipColors(),
                )
                if (isCurrent) {
                    Text(
                        text = stringResource(R.string.library_current_badge),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        },
        trailingContent = {
            Row {
                if (!book.isPreset) {
                    IconButton(onClick = onRename) {
                        Icon(
                            imageVector = Icons.Outlined.Edit,
                            contentDescription = stringResource(R.string.library_rename),
                        )
                    }
                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Outlined.Delete,
                            contentDescription = stringResource(R.string.library_delete),
                        )
                    }
                }
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
    )
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
