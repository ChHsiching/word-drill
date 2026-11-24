package com.github.chsiching.worddrill.ui.library

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.chsiching.worddrill.R
import com.github.chsiching.worddrill.data.local.WordPhonetics
import com.github.chsiching.worddrill.data.local.WordWithSenses
import com.github.chsiching.worddrill.data.settings.DeckOrdering
import com.github.chsiching.worddrill.data.settings.PhoneticPreference
import com.github.chsiching.worddrill.ui.theme.CharisSilFontFamily
import com.github.chsiching.worddrill.ui.theme.DialogButton
import com.github.chsiching.worddrill.ui.theme.DialogButtonStyle
import com.github.chsiching.worddrill.ui.theme.DialogFact
import com.github.chsiching.worddrill.ui.theme.DialogFacts
import com.github.chsiching.worddrill.ui.theme.DialogTextField
import com.github.chsiching.worddrill.ui.theme.WordDrillDialog
import com.github.chsiching.worddrill.ui.theme.wordDrillColors
import kotlinx.coroutines.launch

@Composable
fun WordListScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    hidePhonetic: Boolean = false,
    phoneticPreference: PhoneticPreference = PhoneticPreference.US,
    viewModel: WordListViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val readOnly = state.isPreset

    Column(modifier = modifier.fillMaxSize()) {
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

        // Progress header (issue #60): completion count, resume position, deck size.
        // Lives above the search bar — it describes the book, not the filtered view.
        // Reset sits on this line (not the top bar): plain text beside the numbers
        // it clears. Hidden for the review book — it holds no skipped words of its
        // own, so there is no progress to reset.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, top = 2.dp, end = 14.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            // Space, not a separator character, keeps the two figures apart.
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.word_list_progress_completed, state.completionCount),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(
                    R.string.word_list_progress_position,
                    state.resumePosition,
                    state.deckSize,
                ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            if (!state.isReviewBook) {
                Text(
                    text = stringResource(R.string.word_list_reset_label),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        // The padding keeps the tap target >= 44dp wide even where
                        // the touch-target expansion is disabled (tests).
                        .clickable(onClick = viewModel::openResetDialog)
                        .padding(start = 10.dp, end = 10.dp, top = 6.dp, bottom = 6.dp),
                )
            }
        }

        SearchBar(
            onQueryChange = viewModel::onSearchInput,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, bottom = 4.dp),
        )

        when {
            state.words.isEmpty() && state.searchQuery.isNotBlank() ->
                EmptyState(stringResource(R.string.word_list_search_empty))
            state.words.isEmpty() -> EmptyState(if (readOnly) stringResource(R.string.word_list_preset_readonly) else state.bookName)
            else -> {
                val listState = rememberLazyListState()
                val scope = rememberCoroutineScope()
                // A new query makes a new result list. Show it from the top
                // instead of keeping the scroll position of the previous one.
                LaunchedEffect(state.searchQuery) { listState.scrollToItem(0) }
                Row(modifier = Modifier.fillMaxSize()) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.weight(1f),
                        // Same bottom inset as the Library so the floating capsule nav
                        // cannot cover the last entries when scrolled to the end.
                        contentPadding = PaddingValues(start = 24.dp, top = 8.dp, end = 12.dp, bottom = 120.dp),
                    ) {
                        items(state.words, key = { wordWithSenses -> wordWithSenses.word.wordId }) { wordWithSenses ->
                            WordRow(
                                wordWithSenses = wordWithSenses,
                                phonetic = state.phonetics[wordWithSenses.word.wordId],
                                phoneticPreference = phoneticPreference,
                                hidePhonetic = hidePhonetic,
                                readOnly = readOnly,
                                onEditSense = { sense ->
                                    viewModel.openEditDialog(
                                        senseId = sense.senseId,
                                        wordText = wordWithSenses.word.text,
                                        pos = sense.pos,
                                        meaning = sense.meaning,
                                    )
                                },
                                onDeleteWord = {
                                    viewModel.openDeleteDialog(
                                        wordId = wordWithSenses.word.wordId,
                                        wordText = wordWithSenses.word.text,
                                    )
                                },
                            )
                        }
                    }
                    // Letter index bar (issue #60): alphabetical sections only. In
                    // shuffle mode the dealt order carries no sections, so the bar
                    // disappears while the header above stays.
                    if (state.deckOrdering == DeckOrdering.ALPHABETICAL) {
                        LetterIndexBar(
                            anchors = remember(state.words) { letterAnchors(state.words) },
                            onJump = { index -> scope.launch { listState.scrollToItem(index) } },
                            modifier = Modifier.padding(end = 4.dp),
                        )
                    }
                }
            }
        }
    }

    when (val dialog = state.dialog) {
        WordListDialog.None -> Unit
        is WordListDialog.Add -> AddWordDialog(
            state = dialog,
            onTextInput = viewModel::onTextInput,
            onPosInput = viewModel::onPosInput,
            onMeaningInput = viewModel::onMeaningInput,
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
        is WordListDialog.Delete -> DeleteWordDialog(
            wordText = dialog.wordText,
            onConfirm = viewModel::submitDelete,
            onDismiss = viewModel::dismissDialog,
        )
        WordListDialog.Reset -> ResetProgressDialog(
            bookName = state.bookName,
            onConfirm = viewModel::submitReset,
            onDismiss = viewModel::dismissDialog,
        )
    }
}

/** Word entry in the book's list (variant A, owner-approved): line 1 pairs the
 *  word with its phonetic, resolved exactly like the drill card
 *  (PhoneticPreference.resolve; the hide toggle outranks it). Below it every sense
 *  renders as its own block — dictionary typesetting: the meaning wraps inside the
 *  column that starts after its pos label, so continuation lines hang-indent to
 *  the meaning's first character. Rhythm comes from whitespace alone — no
 *  separators, row backgrounds or cards. */
@Composable
private fun WordRow(
    wordWithSenses: WordWithSenses,
    phonetic: WordPhonetics?,
    phoneticPreference: PhoneticPreference,
    hidePhonetic: Boolean,
    readOnly: Boolean,
    onEditSense: (com.github.chsiching.worddrill.data.local.entity.Sense) -> Unit,
    onDeleteWord: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                Text(
                    text = wordWithSenses.word.text,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.alignByBaseline(),
                )
                val displayPhonetic =
                    phoneticPreference.resolve(phonetic?.phoneticUk, phonetic?.phoneticUs)
                if (displayPhonetic != null && !hidePhonetic) {
                    Text(
                        text = displayPhonetic,
                        fontFamily = CharisSilFontFamily,
                        fontSize = 12.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.alignByBaseline(),
                    )
                }
            }
            Column(
                modifier = Modifier.padding(top = 2.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                wordWithSenses.senses.forEach { sense ->
                    val editModifier = if (readOnly) Modifier
                    else Modifier.clickable { onEditSense(sense) }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = sense.pos,
                            fontSize = 13.sp,
                            fontStyle = FontStyle.Italic,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.then(editModifier),
                        )
                        Text(
                            text = sense.meaning,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .weight(1f)
                                .then(editModifier),
                        )
                    }
                }
            }
        }
        if (!readOnly) {
            IconButton(onClick = onDeleteWord, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = stringResource(R.string.word_list_delete),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddWordDialog(
    state: WordListDialog.Add,
    onTextInput: (String) -> Unit,
    onPosInput: (String) -> Unit,
    onMeaningInput: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    var posExpanded by remember { mutableStateOf(false) }
    // Once the form has a gap, each field reports only its own gap.
    val invalid = state.error != null
    val wordBlank = invalid && state.text.isBlank()
    val posBlank = invalid && state.pos.isBlank()
    val meaningBlank = invalid && state.meaning.isBlank()

    WordDrillDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.word_list_add),
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DialogTextField(
                    value = state.text,
                    onValueChange = onTextInput,
                    label = stringResource(R.string.word_list_text_hint),
                    singleLine = true,
                    isError = wordBlank,
                    supportingText = fieldMessage(wordBlank, R.string.validation_word_empty),
                )
                ExposedDropdownMenuBox(
                    expanded = posExpanded,
                    onExpandedChange = { posExpanded = it },
                ) {
                    DialogTextField(
                        value = state.pos,
                        onValueChange = {},
                        label = stringResource(R.string.word_list_pos_dropdown_label),
                        singleLine = true,
                        isError = posBlank,
                        readOnly = true,
                        supportingText = fieldMessage(posBlank, R.string.validation_pos_empty),
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(posExpanded)
                        },
                        modifier = Modifier
                            .menuAnchor()
                            .clickable { posExpanded = !posExpanded },
                    )
                    ExposedDropdownMenu(
                        expanded = posExpanded,
                        onDismissRequest = { posExpanded = false },
                    ) {
                        for (option in WORD_LIST_POS_OPTIONS) {
                            DropdownMenuItem(
                                text = { Text(option) },
                                onClick = {
                                    onPosInput(option)
                                    posExpanded = false
                                },
                            )
                        }
                    }
                }
                DialogTextField(
                    value = state.meaning,
                    onValueChange = onMeaningInput,
                    label = stringResource(R.string.word_list_meaning_hint),
                    singleLine = true,
                    isError = meaningBlank,
                    supportingText = fieldMessage(meaningBlank, R.string.validation_meaning_empty),
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
                text = stringResource(R.string.library_confirm),
                onClick = onConfirm,
                style = DialogButtonStyle.Primary,
                enabled = state.error == null && state.text.isNotBlank(),
            )
        },
    )
}

/** The message under one field of a form dialog, or nothing while that field is fine. */
@Composable
private fun fieldMessage(show: Boolean, @StringRes message: Int): (@Composable () -> Unit)? =
    if (show) ({ Text(stringResource(message)) }) else null

@Composable
private fun EditSenseDialog(
    state: WordListDialog.Edit,
    onPosInput: (String) -> Unit,
    onMeaningInput: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    // Once the form has a gap, each field reports only its own gap.
    val invalid = state.error != null
    val posBlank = invalid && state.pos.isBlank()
    val meaningBlank = invalid && state.meaning.isBlank()
    WordDrillDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.word_list_edit),
        message = state.wordText,
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DialogTextField(
                    value = state.pos,
                    onValueChange = onPosInput,
                    label = stringResource(R.string.word_list_pos_hint),
                    singleLine = true,
                    isError = posBlank,
                    supportingText = fieldMessage(posBlank, R.string.validation_pos_empty),
                )
                DialogTextField(
                    value = state.meaning,
                    onValueChange = onMeaningInput,
                    label = stringResource(R.string.word_list_meaning_hint),
                    singleLine = true,
                    isError = meaningBlank,
                    supportingText = fieldMessage(meaningBlank, R.string.validation_meaning_empty),
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
                text = stringResource(R.string.library_confirm),
                onClick = onConfirm,
                style = DialogButtonStyle.Primary,
                enabled = state.error == null && state.pos.isNotBlank(),
            )
        },
    )
}

@Composable
private fun DeleteWordDialog(
    wordText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    WordDrillDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.word_list_delete_title),
        content = {
            DialogFacts {
                DialogFact(
                    label = stringResource(R.string.word_list_delete_fact_word_label),
                    lines = listOf(wordText),
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
                text = stringResource(R.string.word_list_delete_confirm),
                onClick = onConfirm,
                style = DialogButtonStyle.Destructive,
            )
        },
    )
}

@Composable
private fun ResetProgressDialog(
    bookName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    WordDrillDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.word_list_reset_title),
        content = {
            DialogFacts {
                DialogFact(
                    label = stringResource(R.string.dialog_fact_book_label),
                    lines = listOf(bookName),
                )
                DialogFact(
                    label = stringResource(R.string.word_list_reset_fact_start_label),
                    lines = listOf(stringResource(R.string.word_list_reset_fact_start_line)),
                )
                DialogFact(
                    label = stringResource(R.string.word_list_reset_fact_skipped_label),
                    lines = listOf(
                        stringResource(R.string.word_list_reset_fact_skipped_line1),
                        stringResource(R.string.word_list_reset_fact_skipped_line2),
                    ),
                )
                DialogFact(
                    label = stringResource(R.string.word_list_reset_fact_kept_label),
                    lines = listOf(stringResource(R.string.word_list_reset_fact_kept_line)),
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
                text = stringResource(R.string.word_list_reset_confirm),
                onClick = onConfirm,
                style = DialogButtonStyle.Destructive,
            )
        },
    )
}

/** Centered empty-state caption. */
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

/** Modifier.clickable silently expands its touch region to the ViewConfiguration's
 *  minimum touch target (48dp in production). The bar's cells are a fraction of
 *  that, so each letter's expanded region spans the two cells below it and
 *  steals their taps — every jump lands one letter high (e2e defect). The cells
 *  are already full-height touch targets, so the bar opts out of the expansion. */
private class CellSizedTouchTargets(wrapped: ViewConfiguration) : ViewConfiguration by wrapped {
    override val minimumTouchTargetSize: DpSize
        get() = DpSize(0.dp, 0.dp)
}

@Composable
private fun LetterIndexBar(
    anchors: Map<Char, Int>,
    onJump: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    CompositionLocalProvider(
        LocalViewConfiguration provides
            CellSizedTouchTargets(LocalViewConfiguration.current)
    ) {
        Column(
            modifier = modifier
                .fillMaxHeight()
                .width(32.dp)
                // Mirrors the list's content insets so the bar spans exactly the
                // scrollable region; 'Z' stays clear of the floating capsule nav.
                .padding(top = 8.dp, bottom = 120.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            for (letter in 'A'..'Z') {
                val anchor = anchors[letter]
                Text(
                    text = letter.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                    color = if (anchor != null) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                    // The full cell is the touch target (weight over the bar height);
                    // the glyph centers inside it. Letters with no section in the
                    // displayed list keep their slot for a stable thumb map but are inert.
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .then(if (anchor != null) Modifier.clickable { onJump(anchor) } else Modifier)
                        .wrapContentHeight(Alignment.CenterVertically),
                )
            }
        }
    }
}

@Composable
private fun SearchBar(
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // The field owns its text so the value round trip stays synchronous. A
    // value fed from the async uiState flow lags the editor by one frame and
    // the value-based TextField then resets the editor to the stale value,
    // wiping the typed query. The text is saved with the nav entry so it
    // survives tab switches along with the ViewModel; the one-shot effect
    // re-applies a restored query to keep the filter consistent.
    var text by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(Unit) {
        if (text.isNotEmpty()) onQueryChange(text)
    }
    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it
            onQueryChange(it)
        },
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        placeholder = { Text(stringResource(R.string.word_list_search_hint)) },
        leadingIcon = {
            Icon(
                imageVector = Icons.Outlined.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.wordDrillColors.chipBg,
            unfocusedContainerColor = MaterialTheme.wordDrillColors.chipBg,
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = Color.Transparent,
            cursorColor = MaterialTheme.colorScheme.primary,
        ),
        shape = RoundedCornerShape(10.dp),
    )
}
