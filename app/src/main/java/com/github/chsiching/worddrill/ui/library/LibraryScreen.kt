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
import androidx.compose.material.icons.outlined.FileUpload
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
import com.github.chsiching.worddrill.ui.theme.DialogTextField
import com.github.chsiching.worddrill.ui.theme.WordDrillDialog
import com.github.chsiching.worddrill.ui.theme.wordDrillColors

/**
 * "Library" tab (Ticket #6 + #16 rewrite + #21 file import): book list + switch/create/
 * rename/delete/import.
 *
 * Design spec (#16): dropped CenterAlignedTopAppBar for a large title; plain-text list (no
 * leading icon); selected state uses a light gray background (chipBg) + check + bold text;
 * selection happens in place with no navigation.
 *
 * Keeps the Ticket #6/#11 abilities:
 * - Tap the row body: set as current book (writes DataStore; the "Drill" tab observes the
 *   same-source Flow and reloads immediately)
 * - Tap the trailing chevron: open the book's word list ([WordListScreen])
 * - Custom books: rename/delete entries (kept as TextButtons, matching the existing
 *   interaction)
 *
 * Ticket #21 addition: an "import from file" button below the "+ create book" button,
 * opening SAF + a book-name input.
 *
 * Preset books do not show rename/delete (isPreset check; the DAO layer's
 * rename/deleteCustom also guards with isPreset=0).
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

/**
 * Book row: plain text (name + subtitle) + chipBg background and check in the selected
 * state. Row body tap = set as current; trailing chevron = open the word list; custom
 * books get rename/delete TextButtons.
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
                // Rename/delete entries for custom books (TextButtons under the subtitle, matching the existing interaction)
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
            // Selected-state check (secondary color, no inversion)
            if (isCurrent) {
                Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = stringResource(R.string.library_current_badge),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
            // Trailing chevron: opens the word list
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

/** Create-book button: chipBg background, corner radius 14, Plus icon + centered label. */
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

/**
 * Import-from-file button (Ticket #21): same placement and style family as
 * [AddBookButton], but an OutlinedButton to set it apart (create is the primary entry,
 * import the secondary one).
 */
@Composable
private fun ImportBookButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.FileUpload,
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

/**
 * File import dialog (Ticket #21).
 *
 * - Explaining text at the top (supported formats + column structure)
 * - "Pick file" button triggers SAF (OpenDocument, mime filter xlsx/txt/csv/pdf/any)
 * - Shows the picked filename
 * - Book-name input (reuses BookNameValidation)
 * - Confirm button: clickable only when a file is picked, the name passes validation, and
 *   not working
 */
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
            // Read DISPLAY_NAME from the Uri Cursor; fall back to uri.lastPathSegment when unavailable
            val filename = queryFilename(context, uri)
            onPickFile(uri, filename)
        }
    }

    WordDrillDialog(
        onDismissRequest = { if (!state.working) onDismiss() },
        title = stringResource(R.string.library_import_title),
        message = stringResource(R.string.library_import_message),
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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

/** Looks up the filename from a SAF Uri (DISPLAY_NAME column). Falls back to lastPathSegment when unavailable. */
private fun queryFilename(context: android.content.Context, uri: Uri): String {
    return runCatching {
        context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
    }.getOrNull()?.takeIf { it.isNotBlank() } ?: (uri.lastPathSegment ?: "unknown")
}

/**
 * Import-done dialog (Ticket #21). Shows [ImportSummary]; the user closes it with OK.
 */
@Composable
private fun ImportDoneDialog(
    summary: com.github.chsiching.worddrill.data.wordimport.ImportSummary,
    onDismiss: () -> Unit,
) {
    WordDrillDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.library_import_title),
        message = stringResource(R.string.library_import_done, summary.success, summary.skipped),
        buttons = {
            DialogButton(
                text = stringResource(R.string.library_confirm),
                onClick = onDismiss,
                style = DialogButtonStyle.Primary,
            )
        },
    )
}

/**
 * Delete-book double-confirmation dialog (Ticket #18).
 * Cancel is the safe option (default); delete uses a destructive light-red button to signal
 * the risk. The DAO layer guards with isPreset=0, and the UI already hides the delete entry
 * for preset books, so only custom books reach here.
 */
@Composable
private fun DeleteBookDialog(
    name: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    WordDrillDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.library_delete_title),
        message = stringResource(R.string.library_delete_message, name),
        buttons = {
            DialogButton(
                text = stringResource(R.string.library_delete_confirm),
                onClick = onConfirm,
                style = DialogButtonStyle.Destructive,
            )
            DialogButton(
                text = stringResource(R.string.library_cancel),
                onClick = onDismiss,
                style = DialogButtonStyle.Cancel,
            )
        },
    )
}
