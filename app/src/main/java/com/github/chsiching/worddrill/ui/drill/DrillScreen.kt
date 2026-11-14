package com.github.chsiching.worddrill.ui.drill

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.chsiching.worddrill.R
import com.github.chsiching.worddrill.data.local.WordWithSenses
import com.github.chsiching.worddrill.ui.theme.wordDrillColors
import com.github.chsiching.worddrill.ui.theme.wordDrillTypography
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

@Composable
fun DrillScreen(
    modifier: Modifier = Modifier,
    locked: Boolean = false,
    onToggleLock: () -> Unit = {},
    hidePhonetic: Boolean = false,
    viewModel: DrillViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    when (val state = uiState) {
        DrillUiState.Loading -> CenterText(stringResource(R.string.drill_loading), modifier)
        DrillUiState.Empty -> CenterText(stringResource(R.string.drill_empty), modifier)
        is DrillUiState.Ready -> DrillPager(
            bookName = state.bookName,
            cards = state.cards,
            onPageSettled = viewModel::onPageSettled,
            onSkip = { page -> viewModel.skipCurrentWord(page) },
            isReviewBook = state.isReviewBook,
            onRestore = { page -> viewModel.restoreCurrentWord(page) },
            locked = locked,
            onToggleLock = onToggleLock,
            hidePhonetic = hidePhonetic,
            modifier = modifier,
        )
    }
}

@Composable
internal fun DrillPager(
    bookName: String,
    cards: List<WordWithSenses>,
    onPageSettled: (previousPage: Int, currentPage: Int) -> Unit,
    onSkip: (currentPage: Int) -> Unit,
    isReviewBook: Boolean,
    onRestore: (currentPage: Int) -> Unit,
    locked: Boolean,
    onToggleLock: () -> Unit,
    hidePhonetic: Boolean,
    modifier: Modifier = Modifier,
) {
    val pagerState = rememberPagerState(pageCount = { cards.size })

    LaunchedEffect(pagerState) {
        var previousSettled = pagerState.settledPage
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .drop(1)
            .collect { current ->
                onPageSettled(previousSettled, current)
                previousSettled = current
            }
    }

    Column(modifier = modifier.fillMaxSize()) {
        DrillTopBar(
            bookName = bookName,
            index = pagerState.currentPage,
            total = cards.size,
            locked = locked,
            onToggleLock = onToggleLock,
            isReviewBook = isReviewBook,
            onAction = { page ->
                if (isReviewBook) onRestore(page) else onSkip(page)
            },
            currentPage = pagerState.currentPage,
        )

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
        ) { pageIndex ->
            WordCard(
                word = cards[pageIndex],
                hidePhonetic = hidePhonetic,
            )
        }
    }
}

@Composable
private fun DrillTopBar(
    bookName: String,
    index: Int,
    total: Int,
    locked: Boolean,
    onToggleLock: () -> Unit,
    isReviewBook: Boolean,
    onAction: (currentPage: Int) -> Unit,
    currentPage: Int,
) {
    val actionLabel = if (isReviewBook) R.string.drill_restore else R.string.drill_skip
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = bookName,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelLarge,
                letterSpacing = 1.sp,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val actionInteraction = remember { MutableInteractionSource() }
                val actionPressed by actionInteraction.collectIsPressedAsState()
                val actionAlpha by animateFloatAsState(
                    targetValue = if (actionPressed) 0.4f else 1f,
                    animationSpec = spring(),
                    label = "actionAlpha",
                )
                Text(
                    text = stringResource(actionLabel),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier
                        .alpha(actionAlpha)
                        .clickable(
                            interactionSource = actionInteraction,
                            indication = null,
                            onClick = { onAction(currentPage) },
                        )
                        .padding(start = 12.dp, top = 4.dp, bottom = 4.dp),
                )
                Spacer(Modifier.width(10.dp))
                LockButton(locked = locked, onClick = onToggleLock)
            }
        }
        Text(
            text = "${index + 1} / $total",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
private fun LockButton(locked: Boolean, onClick: () -> Unit) {
    val bg = if (locked) MaterialTheme.colorScheme.onSurface else Color.Transparent
    val fg = if (locked) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onSurfaceVariant
    val lockInteraction = remember { MutableInteractionSource() }
    val pressed by lockInteraction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.9f else 1f,
        animationSpec = spring(),
        label = "lockScale",
    )
    Surface(
        shape = CircleShape,
        color = bg,
        modifier = Modifier
            .size(30.dp)
            .clickable(
                interactionSource = lockInteraction,
                indication = null,
                onClick = onClick,
            ),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = if (locked) Icons.Outlined.LockOpen else Icons.Outlined.Lock,
                contentDescription = stringResource(
                    if (locked) R.string.drill_unlock else R.string.drill_lock
                ),
                tint = fg,
                modifier = Modifier
                    .size(16.dp)
                    .graphicsLayer { scaleX = scale; scaleY = scale },
            )
        }
    }
}

@Composable
private fun WordCard(
    word: WordWithSenses,
    hidePhonetic: Boolean,
) {
    val typography = MaterialTheme.wordDrillTypography
    val colors = MaterialTheme.colorScheme

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(0.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 80.dp),
        ) {
            Text(
                text = word.word.text,
                style = typography.word.copy(color = colors.onSurface),
                textAlign = TextAlign.Center,
            )
            if (!word.word.phonetic.isNullOrBlank() && !hidePhonetic) {
                Text(
                    text = word.word.phonetic,
                    style = typography.phonetic.copy(color = colors.onSurfaceVariant),
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
            Box(
                modifier = Modifier
                    .padding(vertical = 20.dp)
                    .width(32.dp)
                    .height(1.dp)
                    .background(MaterialTheme.wordDrillColors.separatorStrong),
            )
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                word.senses.forEach { sense ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = sense.pos,
                            style = typography.partOfSpeech.copy(color = colors.onSurfaceVariant),
                        )
                        Text(
                            text = sense.meaning,
                            style = typography.meaning.copy(color = colors.onSurface),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CenterText(text: String, modifier: Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
