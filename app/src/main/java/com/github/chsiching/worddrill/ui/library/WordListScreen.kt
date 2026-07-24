package com.github.chsiching.worddrill.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.chsiching.worddrill.R
import com.github.chsiching.worddrill.data.local.WordWithSenses
import com.github.chsiching.worddrill.ui.theme.wordDrillColors

/**
 * 「库」Tab 二级页：词书内词条列表（Ticket #7 + #16 重写）。
 *
 * 设计稿（#16）：去掉 TopAppBar，改 back + 标题 + 新增入口的内联头部；行用 separator 分隔。
 * 仍保留全部 Ticket #7 能力（自定义词书增/改/移，预置只读）。
 */
@Composable
fun WordListScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WordListViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val readOnly = state.isPreset

    Column(modifier = modifier.fillMaxSize()) {
        // 内联头部：back + 标题 + 新增
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
                text = state.bookName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                if (!readOnly) {
                    IconButton(onClick = viewModel::openAddDialog) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = stringResource(R.string.word_list_add),
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }

        when {
            state.words.isEmpty() -> EmptyState(if (readOnly) stringResource(R.string.word_list_preset_readonly) else state.bookName)
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
            ) {
                items(state.words, key = { it.word.wordId }) { wordWithSenses ->
                    WordRow(
                        wordWithSenses = wordWithSenses,
                        readOnly = readOnly,
                        onEditSense = { sense ->
                            viewModel.openEditDialog(
                                senseId = sense.senseId,
                                wordText = wordWithSenses.word.text,
                                pos = sense.pos,
                                meaning = sense.meaning,
                            )
                        },
                        onRemoveWord = { viewModel.removeWordFromBook(wordWithSenses.word.wordId) },
                    )
                }
            }
        }
    }

    when (val dialog = state.dialog) {
        WordListDialog.None -> Unit
        is WordListDialog.Add -> AddWordDialog(
            state = dialog,
            onTextInput = { t -> viewModel.onAddInput(t, dialog.pos, dialog.meaning) },
            onPosInput = { p -> viewModel.onAddInput(dialog.text, p, dialog.meaning) },
            onMeaningInput = { m -> viewModel.onAddInput(dialog.text, dialog.pos, m) },
            onConfirm = viewModel::submitAdd,
            onDismiss = viewModel::dismissDialog,
        )
        is WordListDialog.Edit -> EditSenseDialog(
            state = dialog,
            onPosInput = { p -> viewModel.onEditInput(p, dialog.meaning) },
            onMeaningInput = { m -> viewModel.onEditInput(dialog.pos, m) },
            onConfirm = viewModel::submitEdit,
            onDismiss = viewModel::dismissDialog,
        )
    }
}

@Composable
private fun WordRow(
    wordWithSenses: WordWithSenses,
    readOnly: Boolean,
    onEditSense: (com.github.chsiching.worddrill.data.local.entity.Sense) -> Unit,
    onRemoveWord: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Text(
                text = wordWithSenses.word.text,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.width(80.dp),
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                wordWithSenses.senses.forEach { sense ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = sense.pos,
                            style = MaterialTheme.typography.bodySmall,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = if (readOnly) Modifier else Modifier.clickable { onEditSense(sense) },
                        )
                        Text(
                            text = sense.meaning,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = if (readOnly) Modifier else Modifier.clickable { onEditSense(sense) },
                        )
                    }
                }
            }
            if (!readOnly) {
                IconButton(onClick = onRemoveWord, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = stringResource(R.string.word_list_remove),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        // 行底分割线
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.wordDrillColors.separator),
        )
    }
}

@Composable
private fun AddWordDialog(
    state: WordListDialog.Add,
    onTextInput: (String) -> Unit,
    onPosInput: (String) -> Unit,
    onMeaningInput: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.word_list_add)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = state.text,
                    onValueChange = onTextInput,
                    label = { Text(stringResource(R.string.word_list_text_hint)) },
                    singleLine = true,
                    isError = state.error != null,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = state.pos,
                    onValueChange = onPosInput,
                    label = { Text(stringResource(R.string.word_list_pos_hint)) },
                    singleLine = true,
                    isError = state.error != null,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = state.meaning,
                    onValueChange = onMeaningInput,
                    label = { Text(stringResource(R.string.word_list_meaning_hint)) },
                    singleLine = true,
                    isError = state.error != null,
                    supportingText = if (state.error != null) ({ Text(stringResource(state.error)) }) else null,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = state.error == null && state.text.isNotBlank()) {
                Text(stringResource(R.string.library_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.library_cancel)) }
        },
    )
}

@Composable
private fun EditSenseDialog(
    state: WordListDialog.Edit,
    onPosInput: (String) -> Unit,
    onMeaningInput: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.word_list_edit)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = state.wordText,
                    style = MaterialTheme.typography.titleMedium,
                )
                OutlinedTextField(
                    value = state.pos,
                    onValueChange = onPosInput,
                    label = { Text(stringResource(R.string.word_list_pos_hint)) },
                    singleLine = true,
                    isError = state.error != null,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = state.meaning,
                    onValueChange = onMeaningInput,
                    label = { Text(stringResource(R.string.word_list_meaning_hint)) },
                    singleLine = true,
                    isError = state.error != null,
                    supportingText = if (state.error != null) ({ Text(stringResource(state.error)) }) else null,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = state.error == null && state.pos.isNotBlank()) {
                Text(stringResource(R.string.library_confirm))
            }
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
