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
 * 「库」Tab（Ticket #6 + #16 重写 + #21 文件导入）：词书列表 + 切换/新建/重命名/删除/导入。
 *
 * 设计稿（#16）：去掉 CenterAlignedTopAppBar，改大标题；纯文字列表（无前置 icon）；
 * 选中态用浅灰背景（chipBg）+ check + 文字加粗；原地选中不跳转。
 *
 * 保留 Ticket #6/#11 的能力：
 * - 点行主体 → 设为当前词书（写 DataStore，「刷」Tab 监听同源 Flow 立即重载）
 * - 点行尾 chevron → 进入词书内词条列表（[WordListScreen]）
 * - 自定义词书：重命名/删除入口（保留原 TextButton，与既有交互一致）
 *
 * Ticket #21 新增：「+ 新建词书」按钮下方加「从文件导入」按钮，弹 SAF + 输入词书名。
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

/**
 * 从文件导入按钮（Ticket #21）：与 [AddBookButton] 同位置风格，但用 OutlinedButton
 * 区分主次（"新建"是主入口，"导入"是次入口）。
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
 * 文件导入对话框（Ticket #21）。
 *
 * - 顶部说明文案（支持的格式 + 列结构）
 * - 「选择文件」按钮触发 SAF（OpenDocument，mime 过滤 xlsx/txt/csv/pdf/任意）
 * - 已选文件名展示
 * - 词书名输入框（沿用 BookNameValidation 校验）
 * - 确定按钮：选了文件 + 名称通过校验 + 非 working 才可点
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
            // 从 Uri Cursor 取 DISPLAY_NAME；取不到时 fallback 到 uri.lastPathSegment
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

/** 从 SAF Uri 查文件名（DISPLAY_NAME 列）。取不到时 fallback 到 lastPathSegment。 */
private fun queryFilename(context: android.content.Context, uri: Uri): String {
    return runCatching {
        context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
    }.getOrNull()?.takeIf { it.isNotBlank() } ?: (uri.lastPathSegment ?: "unknown")
}

/**
 * 导入完成对话框（Ticket #21）。展示 [ImportSummary]，用户点确定关闭。
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
 * 删除词书二次确认对话框（Ticket #18）。
 * 取消是安全选项（默认）；删除用 destructive 浅红底按钮提示破坏性。DAO 层 isPreset=0 兜底，
 * UI 层已对预置词书隐藏删除入口，这里只处理自定义词书。
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
