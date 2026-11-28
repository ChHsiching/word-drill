package com.github.chsiching.worddrill.ui.library

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.chsiching.worddrill.R
import com.github.chsiching.worddrill.data.local.dao.BookWithCount
import com.github.chsiching.worddrill.ui.theme.DialogButton
import com.github.chsiching.worddrill.ui.theme.DialogButtonStyle
import com.github.chsiching.worddrill.ui.theme.DialogFact
import com.github.chsiching.worddrill.ui.theme.DialogFacts
import com.github.chsiching.worddrill.ui.theme.DialogTextField
import com.github.chsiching.worddrill.ui.theme.WordDrillDialog
import com.github.chsiching.worddrill.ui.theme.wordDrillColors

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
                        onDelete = { viewModel.openDeleteDialog(book.bookId) },
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
            item { Spacer(Modifier.size(8.dp)) }
            item {
                ImportBookButton(
                    onClick = viewModel::openImportDialog,
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
        is LibraryDialog.Delete -> DeleteBookDialog(
            name = dialog.name,
            onConfirm = viewModel::submitDelete,
            onDismiss = viewModel::dismissDialog,
        )
        is LibraryDialog.Import -> ImportBookDialog(
            state = dialog,
            onNameInput = viewModel::onNameInput,
            onPickFile = viewModel::onFileSelected,
            onSubmit = viewModel::submitImport,
            onDismiss = viewModel::dismissDialog,
        )
        is LibraryDialog.ImportDone -> ImportDoneDialog(
            summary = dialog.summary,
            onDismiss = viewModel::dismissDialog,
        )
    }
}

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
                // Space, not a separator character, keeps the count and the kind apart.
                Row(
                    modifier = Modifier.padding(top = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.library_word_count_format, book.wordCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = stringResource(
                            if (book.isPreset) R.string.library_preset_badge else R.string.library_custom_badge,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
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
            if (isCurrent) {
                Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = stringResource(R.string.library_current_badge),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
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
private fun ImportBookButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.FileDownload,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.library_import))
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
    WordDrillDialog(
        onDismissRequest = onDismiss,
        title = title,
        content = {
            DialogTextField(
                value = name,
                onValueChange = onNameInput,
                label = stringResource(R.string.library_name_hint),
                singleLine = true,
                isError = error != null,
                supportingText = if (error != null) ({ Text(stringResource(error)) }) else null,
            )
        },
        buttons = {
            DialogButton(
                text = stringResource(R.string.library_cancel),
                onClick = onDismiss,
                style = DialogButtonStyle.Cancel,
            )
            DialogButton(
                text = stringResource(R.string.library_confirm),
                onClick = onConfirm,
                style = DialogButtonStyle.Primary,
                enabled = error == null && name.isNotBlank(),
            )
        },
    )
}

@Composable
private fun ImportBookDialog(
    state: LibraryDialog.Import,
    onNameInput: (String) -> Unit,
    onPickFile: (uri: Uri, filename: String) -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val pickFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri != null) {
            val filename = queryFilename(context, uri)
            onPickFile(uri, filename)
        }
    }

    WordDrillDialog(
        onDismissRequest = { if (!state.working) onDismiss() },
        title = stringResource(R.string.library_import_title),
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // What the importer accepts, stated as facts above the controls.
                DialogFacts {
                    DialogFact(
                        label = stringResource(R.string.library_import_fact_format_label),
                        lines = listOf(stringResource(R.string.library_import_fact_format_line)),
                    )
                    DialogFact(
                        label = stringResource(R.string.library_import_fact_columns_label),
                        lines = listOf(stringResource(R.string.library_import_fact_columns_line)),
                    )
                    DialogFact(
                        label = stringResource(R.string.library_import_fact_meaning_label),
                        lines = listOf(
                            stringResource(R.string.library_import_fact_meaning_line1),
                            stringResource(R.string.library_import_fact_meaning_line2),
                        ),
                    )
                }
                Spacer(Modifier.height(4.dp))
                OutlinedButton(
                    onClick = {
                        pickFileLauncher.launch(
                            arrayOf(
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                "application/vnd.ms-excel",
                                "text/plain",
                                "text/csv",
                                "application/pdf",
                                "*/*",
                            )
                        )
                    },
                    enabled = !state.working,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.library_import_pick_file))
                }
                Text(
                    text = state.filename?.let { stringResource(R.string.library_import_file_picked, it) }
                        ?: stringResource(R.string.library_import_file_none),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                DialogTextField(
                    value = state.name,
                    onValueChange = onNameInput,
                    label = stringResource(R.string.library_name_hint),
                    singleLine = true,
                    isError = state.error != null,
                    enabled = !state.working,
                    supportingText = if (state.error != null) ({ Text(stringResource(state.error)) }) else null,
                )
                if (state.working) {
                    Text(
                        text = stringResource(R.string.library_import_working),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (state.failureMessage != null) {
                    val msg = state.failureDetail?.let { "${stringResource(state.failureMessage)}：$it" }
                        ?: stringResource(state.failureMessage)
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        buttons = {
            DialogButton(
                text = stringResource(R.string.library_cancel),
                onClick = onDismiss,
                style = DialogButtonStyle.Cancel,
                enabled = !state.working,
            )
            DialogButton(
                text = stringResource(R.string.library_confirm),
                onClick = onSubmit,
                style = DialogButtonStyle.Primary,
                enabled = !state.working && state.error == null
                    && state.name.isNotBlank() && state.uri != null,
            )
        },
    )
}

private fun queryFilename(context: android.content.Context, uri: Uri): String {
    return runCatching {
        context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
    }.getOrNull()?.takeIf { it.isNotBlank() } ?: (uri.lastPathSegment ?: "unknown")
}

@Composable
private fun ImportDoneDialog(
    summary: com.github.chsiching.worddrill.data.wordimport.ImportSummary,
    onDismiss: () -> Unit,
) {
    // Name up to a few skipped words so the user can see WHAT was incomplete,
    // not just how much.
    val skippedNames = summary.skippedWords.joinToString("、")
    val skippedLine = if (summary.skipped > summary.skippedWords.size) {
        stringResource(R.string.library_import_fact_skipped_words_more, skippedNames)
    } else {
        skippedNames
    }
    WordDrillDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.library_import_done_title),
        content = {
            DialogFacts {
                DialogFact(
                    label = stringResource(R.string.library_import_fact_success_label),
                    lines = listOf(stringResource(R.string.library_import_fact_count, summary.success)),
                )
                DialogFact(
                    label = stringResource(R.string.library_import_fact_skipped_label),
                    lines = listOf(stringResource(R.string.library_import_fact_count, summary.skipped)),
                )
                if (summary.skippedWords.isNotEmpty()) {
                    DialogFact(
                        label = stringResource(R.string.library_import_fact_skipped_reason_label),
                        lines = listOf(stringResource(R.string.library_import_fact_skipped_reason_line)),
                    )
                    DialogFact(
                        label = stringResource(R.string.library_import_fact_skipped_words_label),
                        lines = listOf(skippedLine),
                    )
                }
            }
        },
        buttons = {
            DialogButton(
                text = stringResource(R.string.library_confirm),
                onClick = onDismiss,
                style = DialogButtonStyle.Primary,
            )
        },
    )
}

@Composable
private fun DeleteBookDialog(
    name: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    WordDrillDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.library_delete_title),
        content = {
            DialogFacts {
                DialogFact(
                    label = stringResource(R.string.dialog_fact_book_label),
                    lines = listOf(name),
                )
                DialogFact(
                    label = stringResource(R.string.dialog_fact_after_delete_label),
                    lines = listOf(stringResource(R.string.dialog_fact_recoverable)),
                )
            }
        },
        buttons = {
            DialogButton(
                text = stringResource(R.string.library_cancel),
                onClick = onDismiss,
                style = DialogButtonStyle.Cancel,
            )
            DialogButton(
                text = stringResource(R.string.library_delete_confirm),
                onClick = onConfirm,
                style = DialogButtonStyle.Destructive,
            )
        },
    )
}
