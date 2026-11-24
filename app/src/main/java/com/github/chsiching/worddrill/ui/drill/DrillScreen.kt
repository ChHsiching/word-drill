package com.github.chsiching.worddrill.ui.drill

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.Window
import android.view.WindowManager
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.chsiching.worddrill.R
import com.github.chsiching.worddrill.data.local.WordPhonetics
import com.github.chsiching.worddrill.data.local.WordWithSenses
import com.github.chsiching.worddrill.data.settings.PhoneticPreference
import com.github.chsiching.worddrill.ui.theme.DialogButton
import com.github.chsiching.worddrill.ui.theme.DialogButtonStyle
import com.github.chsiching.worddrill.ui.theme.wordDrillColors
import com.github.chsiching.worddrill.ui.theme.wordDrillTypography
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/** Auto-advance lifecycle integration (#39). The keep-screen-on window flag is held
 *  exactly while the phase is Playing, and playback stops when this screen leaves
 *  composition (leaving the drill route, whose nav backstack retains the ViewModel
 *  across tab switches, so disposal is the leave signal) and on app ON_STOP.
 *  Re-entering always starts idle. */
// Sense-block auto-shrink constants (owner direction): cap total sense height;
// stage 1 shrinks the meaning text alone down to the part-of-speech size (15sp),
// stage 2 shrinks both together toward a 12sp hard floor.
private val SensesMaxHeight = 300.dp
private const val MeaningBaseSize = 17f
private const val MeaningBaseLineHeight = 24f
private const val PosBaseSize = 15f
private const val PosBaseLineHeight = 20f
private const val MeaningFloorRatio = PosBaseSize / MeaningBaseSize
private const val UniformFloorRatio = 12f / PosBaseSize

@Composable
private fun AutoAdvanceLifecycle(isPlaying: Boolean, onStopPlayback: () -> Unit) {
    val view = LocalView.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val window = view.context.findActivity()?.window

    // Pause, stop, and leave all flip [isPlaying] or dispose the effect, releasing
    // the flag so the screen may sleep again outside an active session.
    DisposableEffect(isPlaying) {
        if (isPlaying) window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { releaseKeepScreenOn(window) }
    }

    DisposableEffect(Unit) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                // Cards must not flip unattended in the background. The flag is
                // cleared here too: recomposition is frame-driven and frames stop
                // with the stopped window, so the effect above may not re-run promptly.
                onStopPlayback()
                releaseKeepScreenOn(window)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            releaseKeepScreenOn(window)
            onStopPlayback()
        }
    }
}

/** The keep-screen-on invariant: the flag is held exactly while playing, so every
 *  exit from an active session — pause/stop recomposition, app ON_STOP, or leaving
 *  composition — must release it so the screen may sleep again. */
private fun releaseKeepScreenOn(window: Window?) {
    window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
}

/** The hosting activity's window, walking ContextWrapper chains (themed contexts). */
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
fun DrillScreen(
    modifier: Modifier = Modifier,
    locked: Boolean = false,
    onToggleLock: () -> Unit = {},
    hidePhonetic: Boolean = false,
    phoneticPreference: PhoneticPreference = PhoneticPreference.US,
    onSwitchBook: () -> Unit,
    viewModel: DrillViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val playback by viewModel.playback.collectAsStateWithLifecycle()

    AutoAdvanceLifecycle(
        isPlaying = playback.phase == AutoAdvancePhase.Playing,
        onStopPlayback = viewModel::stopAutoAdvance,
    )

    when (val state = uiState) {
        DrillUiState.Loading -> CenterText(stringResource(R.string.drill_loading), modifier)
        // The empty deck keeps the header (book name, 0 / 0, action label, lock) with
        // the centered message below it; skip/restore stay inert because the
        // ViewModel guards them on the Ready state.
        is DrillUiState.Empty -> Column(modifier = modifier.fillMaxSize()) {
            DrillTopBar(
                bookName = state.bookName,
                counter = "0 / 0",
                locked = locked,
                onToggleLock = onToggleLock,
                isReviewBook = state.isReviewBook,
                onAction = {
                    if (state.isReviewBook) {
                        viewModel.restoreCurrentWord(0)
                    } else {
                        viewModel.skipCurrentWord(0)
                    }
                },
            )
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.drill_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
        // Keyed on the book id so switching books rebuilds the pager at the new book's
        // resume position; within one book the pager state (and the user's page)
        // survives deck reloads from skip/restore. deckGeneration only advances on a
        // progress reset, which rebuilds the pager at the restored first card.
        is DrillUiState.Ready -> key(state.bookId, state.deckGeneration) {
            DrillPager(
                bookName = state.bookName,
                cards = state.cards,
                initialPage = state.initialPageIndex,
                completionCount = state.completionCount,
                onPageSettled = viewModel::onPageSettled,
                onSkip = { page -> viewModel.skipCurrentWord(page) },
                isReviewBook = state.isReviewBook,
                onRestore = { page -> viewModel.restoreCurrentWord(page) },
                onRestartBook = viewModel::restartBook,
                onSwitchBook = onSwitchBook,
                locked = locked,
                onToggleLock = onToggleLock,
                hidePhonetic = hidePhonetic,
                phonetics = state.phonetics,
                phoneticPreference = phoneticPreference,
                modifier = modifier,
                onToggleAutoAdvance = viewModel::toggleAutoAdvance,
                advance = viewModel.advance,
            )
        }
    }
}

@Composable
internal fun DrillPager(
    bookName: String,
    cards: List<WordWithSenses>,
    phonetics: Map<Long, WordPhonetics>,
    phoneticPreference: PhoneticPreference,
    initialPage: Int = 0,
    /** Completed passes before the one the completion card is showing (the settle
     *  handler bumps it once the card is reached). */
    completionCount: Int,
    onPageSettled: (previousPage: Int, currentPage: Int) -> Unit,
    onSkip: (currentPage: Int) -> Unit,
    isReviewBook: Boolean,
    onRestore: (currentPage: Int) -> Unit,
    onRestartBook: () -> Unit,
    onSwitchBook: () -> Unit,
    locked: Boolean,
    onToggleLock: () -> Unit,
    hidePhonetic: Boolean,
    modifier: Modifier = Modifier,
    onToggleAutoAdvance: (currentPage: Int) -> Unit,
    advance: Flow<Int>,
) {
    // One virtual page after the deck's last card (GLOSSARY.md, completion card).
    val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { cards.size + 1 })

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

    // Auto-advance flips execute as programmatic animated scrolls; the settle they
    // produce flows through the same onPageSettled path as a manual swipe, so swipe
    // logging and resume writes stay completely unchanged.
    LaunchedEffect(pagerState, advance) {
        advance.collect { target -> pagerState.animateScrollToPage(target) }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            DrillTopBar(
                bookName = bookName,
                // The completion page keeps the last card's counter (N / N): it is not
                // a card position.
                counter = "${(pagerState.currentPage + 1).coerceAtMost(cards.size)} / ${cards.size}",
                locked = locked,
                onToggleLock = onToggleLock,
                isReviewBook = isReviewBook,
                onAction = {
                    if (isReviewBook) onRestore(pagerState.currentPage) else onSkip(pagerState.currentPage)
                },
            )

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxSize()
                    // Double-tap on the card area toggles auto-advance. Single taps
                    // stay inert, so the pair window is generous (600 ms) — injected
                    // input (emulator mouse, adb) arrives with per-tap latency that
                    // can push two taps past the 300 ms platform default.
                    .pointerInput(onToggleAutoAdvance) {
                        var lastTapAt = 0L
                        var lastTapOffset = Offset.Zero
                        detectTapGestures(
                            onTap = { offset ->
                                val now = System.currentTimeMillis()
                                if (now - lastTapAt < 600 &&
                                    (offset - lastTapOffset).getDistance() < 96f) {
                                    lastTapAt = 0L
                                    onToggleAutoAdvance(pagerState.currentPage)
                                } else {
                                    lastTapAt = now
                                    lastTapOffset = offset
                                }
                            },
                        )
                    },
            ) { pageIndex ->
                if (pageIndex == cards.size) {
                    // While the swipe toward the card is still settling, the count has
                    // not been bumped yet, so the pass shown is the one this traversal
                    // completes (count + 1); once settled, the settle handler has
                    // already recorded it and the card reads the count itself.
                    CompletionCard(
                        bookName = bookName,
                        passCount = if (pagerState.settledPage == pageIndex) completionCount
                        else completionCount + 1,
                        onRestart = onRestartBook,
                        onSwitchBook = onSwitchBook,
                    )
                } else {
                    WordCard(
                        word = cards[pageIndex],
                        phonetic = phonetics[cards[pageIndex].word.wordId],
                        phoneticPreference = phoneticPreference,
                        hidePhonetic = hidePhonetic,
                    )
                }
            }
        }

    }
}

@Composable
private fun DrillTopBar(
    bookName: String,
    counter: String,
    locked: Boolean,
    onToggleLock: () -> Unit,
    isReviewBook: Boolean,
    onAction: () -> Unit,
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
                            onClick = onAction,
                        )
                        .padding(start = 12.dp, top = 4.dp, bottom = 4.dp),
                )
                Spacer(Modifier.width(10.dp))
                LockButton(locked = locked, onClick = onToggleLock)
            }
        }
        Text(
            text = counter,
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
    phonetic: WordPhonetics?,
    phoneticPreference: PhoneticPreference,
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
            // Phonetic preference (issue #58): resolve UK/US/both per word at render
            // time; the hide toggle outranks the preference. Each segment carries a
            // 英/美 label so the two variants are tellable apart; the identical-form
            // dedupe shows one unlabeled phonetic.
            val phoneticSegments =
                phoneticPreference.segments(phonetic?.phoneticUk, phonetic?.phoneticUs)
            if (phoneticSegments.isNotEmpty() && !hidePhonetic) {
                Row(
                    modifier = Modifier.padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    phoneticSegments.forEach { segment ->
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (segment.kind != PhoneticPreference.Kind.SHARED) {
                                Text(
                                    text = stringResource(
                                        when (segment.kind) {
                                            PhoneticPreference.Kind.UK -> R.string.drill_phonetic_uk
                                            else -> R.string.drill_phonetic_us
                                        }
                                    ),
                                    fontSize = 12.sp,
                                    letterSpacing = 1.sp,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.onSurfaceVariant,
                                    modifier = Modifier.alignByBaseline(),
                                )
                            }
                            Text(
                                text = segment.text,
                                style = typography.phonetic.copy(color = colors.onSurfaceVariant),
                                modifier = Modifier.alignByBaseline(),
                            )
                        }
                    }
                }
            }
            Box(
                modifier = Modifier
                    .padding(vertical = 20.dp)
                    .width(32.dp)
                    .height(1.dp)
                    .background(MaterialTheme.wordDrillColors.separatorStrong),
            )
            // Sense block with an auto-shrink cap (owner direction): total height is
            // capped; on overflow the meaning font shrinks first, never below the
            // part-of-speech size, so the pos labels stay readable. The scale only
            // ever decreases for a given word (reset on word change). A sense that
            // is still too long at the smallest size scrolls inside the capped
            // block instead of running under the navigation bar.
            val sensesCapPx = with(LocalDensity.current) { SensesMaxHeight.toPx() }
            var meaningScale by remember(word.word.wordId) { mutableFloatStateOf(1f) }
            var uniformScale by remember(word.word.wordId) { mutableFloatStateOf(1f) }
            Column(
                modifier = Modifier
                    .heightIn(max = SensesMaxHeight)
                    .verticalScroll(rememberScrollState()),
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.onGloballyPositioned { coords ->
                        val heightPx = coords.size.height.toFloat()
                        if (heightPx > sensesCapPx) {
                            val nextMeaning = maxOf(
                                sensesCapPx / heightPx * meaningScale,
                                MeaningFloorRatio,
                            )
                            if (nextMeaning < meaningScale) {
                                meaningScale = nextMeaning
                            } else if (uniformScale > UniformFloorRatio) {
                                uniformScale = maxOf(
                                    sensesCapPx / heightPx * uniformScale,
                                    UniformFloorRatio,
                                )
                            }
                        }
                    },
                ) {
                    if (word.senses.isEmpty()) {
                        // Imported word the dictionary does not know: say so instead of
                        // showing a bare separator.
                        Text(
                            text = stringResource(R.string.drill_not_in_dictionary),
                            style = typography.meaning.copy(color = colors.onSurfaceVariant),
                            textAlign = TextAlign.Center,
                        )
                    }
                    word.senses.forEach { sense ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = sense.pos,
                                style = typography.partOfSpeech.copy(
                                    color = colors.onSurfaceVariant,
                                    fontSize = (PosBaseSize * uniformScale).sp,
                                    lineHeight = (PosBaseLineHeight * uniformScale).sp,
                                ),
                            )
                            Text(
                                text = sense.meaning,
                                style = typography.meaning.copy(
                                    color = colors.onSurface,
                                    fontSize = (MeaningBaseSize * meaningScale * uniformScale).sp,
                                    lineHeight = (MeaningBaseLineHeight * meaningScale * uniformScale).sp,
                                ),
                            )
                        }
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

/** The virtual card after the deck's last one (GLOSSARY.md, completion card). Pure
 *  typography in the app's own grammar: the pass count as a StatCard-style hero
 *  (第 N 遍, N in the tnum number style), the StatCard progress track rendered full
 *  as the "complete" signal, and the two ways on in dialog-button form — restart
 *  (R.string.drill_completion_restart, back to the first card of the current deck;
 *  skipped words stay skipped) primary, switch book (R.string.drill_completion_switch_book,
 *  Library) secondary. */
@Composable
private fun CompletionCard(
    bookName: String,
    passCount: Int,
    onRestart: () -> Unit,
    onSwitchBook: () -> Unit,
) {
    val typography = MaterialTheme.wordDrillTypography
    val colors = MaterialTheme.colorScheme
    // Proportional vertical layout (owner direction): the hero cluster centers
    // between the page top and the action row with a slight upward bias, so any
    // screen aspect self-balances instead of a fixed offset drifting on tall
    // displays.
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp),
    ) {
        Spacer(modifier = Modifier.weight(0.44f))
            // The hero: the pass count in the StatCard number grammar, flanked by
            // its ordinal units on the same baseline — the word slot of the card.
            Row {
                Text(
                    text = stringResource(R.string.drill_completion_pass_prefix),
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.alignByBaseline(),
                )
                Text(
                    text = passCount.toString(),
                    style = typography.statNumberLarge.copy(color = colors.onSurface),
                    modifier = Modifier
                        .padding(horizontal = 6.dp)
                        .alignByBaseline(),
                )
                Text(
                    text = stringResource(R.string.drill_completion_pass_suffix),
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.alignByBaseline(),
                )
            }
            // The phonetic slot: book name + done status in one secondary line.
            Text(
                text = stringResource(R.string.drill_completion_status),
                style = typography.phonetic.copy(color = colors.onSurfaceVariant),
                modifier = Modifier.padding(top = 10.dp),
            )
            // The app's own completion vocabulary: the StatCard progress track, full.
            Box(
                modifier = Modifier
                    .padding(top = 28.dp)
                    .fillMaxWidth()
                    .height(6.dp)
                    .background(MaterialTheme.wordDrillColors.progressTrack, RoundedCornerShape(3.dp)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .background(colors.onSurface, RoundedCornerShape(3.dp)),
                )
            }
        Spacer(modifier = Modifier.weight(0.56f))
        // Actions sit low in the thumb zone, clear of the floating nav.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DialogButton(
                text = stringResource(R.string.drill_completion_restart),
                onClick = onRestart,
                style = DialogButtonStyle.Primary,
            )
            DialogButton(
                text = stringResource(R.string.drill_completion_switch_book),
                onClick = onSwitchBook,
                style = DialogButtonStyle.Cancel,
            )
        }
        Spacer(modifier = Modifier.height(140.dp))
    }
}
