package com.github.chsiching.worddrill.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
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
import com.github.chsiching.worddrill.data.local.WordWithSenses

/**
 * 「库」Tab 二级页：词书内词条列表（Ticket #7）。
 *
 * - 列出该词书的所有词条（单词 + 词性 + 释义）
 * - 自定义词书：新增词条 / 编辑义项 / 从词书移除（断关联，不删全局词）
 * - 预置词书：只读，不渲染操作按钮（isPreset 判定）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WordListScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WordListViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val readOnly = state.isPreset

    Scaffold(
        modifier = modifier,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(state.bookName) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                        )
                    }
                },
                actions = {
                    if (!readOnly) {
                        IconButton(onClick = viewModel::openAddDialog) {
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = stringResource(R.string.word_list_add),
                            )
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        when {
            state.words.isEmpty() -> EmptyState(
                innerPadding,
                if (readOnly) stringResource(R.string.word_list_preset_readonly) else state.bookName,
            )
            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
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
    ListItem(
        headlineContent = { Text(wordWithSenses.word.text) },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                wordWithSenses.senses.forEach { sense ->
                    Text(
                        text = "${sense.pos}  ${sense.meaning}",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = if (readOnly) Modifier else Modifier.clickable { onEditSense(sense) },
                    )
                }
            }
        },
        trailingContent = {
            if (!readOnly) {
                IconButton(onClick = onRemoveWord) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = stringResource(R.string.word_list_remove),
                    )
                }
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
    )
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
private fun EmptyState(innerPadding: PaddingValues, text: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
