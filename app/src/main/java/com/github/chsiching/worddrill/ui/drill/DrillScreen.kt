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

/**
 * "Drill" tab: full-screen single-card browsing (Ticket #5 + #16 rewrite).
 *
 * Top bar (#16): book name (left) and skip + lock buttons (right) in a symmetric layout.
 * The skip button hides in the locked state; the lock button swaps icon variant and
 * inverts its background. Auto-unlock on tab switch is handled by
 * [com.github.chsiching.worddrill.ui.navigation.WordDrillRoot] (locked resets to false on
 * a tab switch).
 *
 * Card (#16): word, then phonetics (if present, Charis SIL, can be turned off by the
 * "hide phonetics" setting), then a divider, then the sense list (italic part of speech
 * on the same line as the Chinese, one sense per row).
 *
 * Counting: [DrillViewModel.onPageSettled], reusing the Ticket #5 pure function
 * [shouldLogSwipe].
 *
 * @param locked top-bar locked state (hoisted at
 *   [com.github.chsiching.worddrill.ui.navigation.WordDrillRoot]; when locked the nav bar
 *   fades out, skip hides, Pager swiping is disabled)
 * @param onToggleLock lock button click callback
 * @param hidePhonetic true when the "hide phonetics" setting is active; the card omits the
 *   phonetics line
 */
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
                // issue #1: the review book uses "Restore" (onRestore), regular books use
                // "Skip" (onSkip). Both remove the card the same way (the word leaves the
                // current list after restore/skip), and the pager stays at the same index
                // pointing at the next card.
                if (isReviewBook) onRestore(page) else onSkip(page)
            },
            currentPage = pagerState.currentPage,
        )

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            // Review feedback 3: locking does not disable card swiping. Locking only hides
            // the nav bar and skip button (see BottomNavOverlay.hidden in WordDrillRoot and
            // the AnimatedVisibility in DrillTopBar); the user can still swipe through
            // cards while locked.
        ) { pageIndex ->
            WordCard(
                word = cards[pageIndex],
                hidePhonetic = hidePhonetic,
            )
        }
    }
}

/**
 * Top bar: book name (left), counter (center), skip + lock (right).
 *
 * Three-part layout: [book name weight 1f][Spacer weight 1f][buttons intrinsic].
 * The book name takes at most the left half (half of the remaining space), the Spacer
 * fills the middle for the counter overlay, and the buttons hug the far right at their
 * intrinsic width. A long name therefore wraps within the left half only; it never
 * encroaches on the counter's horizontal area and never squeezes the buttons. With a
 * short name the Text does not fill its weight, so the left-aligned look is unchanged;
 * the counter is absolutely centered by the overlay Text.
 *
 * The locked state hides skip (AnimatedVisibility fade); the lock button gets an inverted
 * background and LockOpen icon.
 */
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
    // issue #1: the review book button label is "Restore", regular books "Skip". onAction
    // inside DrillPager already dispatches to onRestore/onSkip by isReviewBook; this spot
    // only handles the no-arg closure and label switch.
    val actionLabel = if (isReviewBook) R.string.drill_restore else R.string.drill_skip
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp),
        contentAlignment = Alignment.Center,
    ) {
        // Base layer: book name (weight 1f, at most the left half) + Spacer (weight 1f, reserved for the counter) + buttons (intrinsic, flush right)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Book name (left): weight(1f) splits the space left after the button width
            // evenly with the Spacer, so the book name takes at most (total width - button
            // width) / 2, stopping left of the screen centerline and never encroaching on
            // the counter. softWrap is on by default; overlong names wrap instead of being
            // cut off.
            Text(
                text = bookName,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelLarge,
                letterSpacing = 1.sp,
                modifier = Modifier.weight(1f),
            )
            // Middle spacer: weight(1f) reserves space for the counter overlay, keeping the book name's right edge left of the screen centerline
            Spacer(Modifier.weight(1f))
            // Skip/restore + lock (right)
            // Review feedback 4: do not hide the skip button when locked (locking only hides the nav bar; skip stays available).
            Row(verticalAlignment = Alignment.CenterVertically) {
                // #17 review feedback #5: the skip button has no ripple box; press feedback
                // comes from animating alpha between 0.4 and 1.0.
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
        // Overlay: the counter is absolutely centered (unaffected by differing left/right widths)
        Text(
            text = "${index + 1} / $total",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

/**
 * Lock button: transparent background and tertiary icon by default; inverted background (onSurface)
 * and background-colored icon in the locked state.
 *
 * #17 review feedback #6: no ripple box. Press feedback scales the icon to 0.9
 * (feedback on pointer-down, no waiting for release).
 */
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

/**
 * Single card: word, phonetics, divider, sense list.
 * The spring entrance on card switch is driven naturally by HorizontalPager's page
 * recomposition (each page composable is an independent instance).
 *
 * Review feedback: dropped the "first card / last card" hints; the top counter "X / Y"
 * already states the position, and repeating it inside the card is redundant.
 */
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
            // Review feedback 2: centering the card vertically sat too low; shifted up by
            // about 12% of the screen (clear of the top bar, above the vertical center).
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 80.dp),
        ) {
            // English word: 44sp SemiBold
            Text(
                text = word.word.text,
                style = typography.word.copy(color = colors.onSurface),
                textAlign = TextAlign.Center,
            )
            // Phonetics: Charis SIL, controlled by hidePhonetic
            if (!word.word.phonetic.isNullOrBlank() && !hidePhonetic) {
                Text(
                    text = word.word.phonetic,
                    style = typography.phonetic.copy(color = colors.onSurfaceVariant),
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
            // Divider: 32x1 separatorStrong
            Box(
                modifier = Modifier
                    .padding(vertical = 20.dp)
                    .width(32.dp)
                    .height(1.dp)
                    .background(MaterialTheme.wordDrillColors.separatorStrong),
            )
            // Sense list: one sense per row, italic part of speech on the same line as the Chinese
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
