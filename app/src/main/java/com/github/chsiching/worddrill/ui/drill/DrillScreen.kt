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
 * 「刷」Tab：全屏单卡片浏览（Ticket #5 + #16 重写）。
 *
 * 顶部条（#16）：词书名（左） ↔ 跳过 + 锁定按钮（右）对称布局。
 * 锁定态下隐藏跳过按钮；锁定按钮图标变体 + 反色背景。
 * 切 Tab 自动解锁由 [com.github.chsiching.worddrill.ui.navigation.WordDrillRoot] 负责
 * （切 Tab 时 reset locked = false）。
 *
 * 卡片（#16）：单词 → 音标（若有，Charis SIL，可被「隐藏音标」设置关闭）→ 分割线
 * → 义项列表（词性斜体 + 中文同行，一行一义项）。
 *
 * 计数：[DrillViewModel.onPageSettled]，沿用 Ticket #5 的纯函数 [shouldLogSwipe]。
 *
 * @param locked 顶部锁定态（hoisted at [com.github.chsiching.worddrill.ui.navigation.WordDrillRoot]，
 *   锁定时导航栏淡出、隐藏跳过、禁用 Pager 滑动）
 * @param onToggleLock 锁定按钮点击回调
 * @param hidePhonetic 「隐藏音标」设置生效时为 true，卡片不渲染音标行
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
                // issue #1：复习词书用「恢复」(onRestore)，普通词书用「跳过」(onSkip)。
                // 两者的卡片消失行为一致（恢复/跳过后词都从当前列表移除），
                // pager 停在同 index 指向下一张。
                if (isReviewBook) onRestore(page) else onSkip(page)
            },
            currentPage = pagerState.currentPage,
        )

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            // 审核反馈 3：锁定不禁用卡片滑动。锁定只隐藏导航栏 + 跳过按钮（见 WordDrillRoot
            // 的 BottomNavOverlay.hidden 和 DrillTopBar 的 AnimatedVisibility），
            // 用户锁定后仍可左右滑卡片浏览。
        ) { pageIndex ->
            WordCard(
                word = cards[pageIndex],
                hidePhonetic = hidePhonetic,
            )
        }
    }
}

/**
 * 顶部条：词书名（左） · 计数器（中） · 跳过+锁定（右）。
 *
 * 计数器要真正在屏幕水平中心，不能跟着 SpaceBetween 走（左右两块宽度不等时，
 * SpaceBetween 会把中间元素挤偏 —— 审核反馈：跳过+锁定比词书名宽，计数器偏左）。
 * 用 Box 叠加：底层 Row 用 SpaceBetween 放左右两块，叠加层 Text 计数器用
 * Alignment.TopCenter 绝对居中（脱离 Row 的均分逻辑）。
 *
 * 锁定态隐藏跳过（AnimatedVisibility fade），锁定按钮反色背景 + LockOpen 图标。
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
    // issue #1：复习词书按钮文案「恢复」，普通词书「跳过」。onAction 在 DrillPager 内
    // 已按 isReviewBook 分发到 onRestore/onSkip，这里只负责无参闭包 + 文案切换。
    val actionLabel = if (isReviewBook) R.string.drill_restore else R.string.drill_skip
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp),
        contentAlignment = Alignment.Center,
    ) {
        // 底层：左右两块 SpaceBetween
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 词书名（左）
            Text(
                text = bookName,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelLarge,
                letterSpacing = 1.sp,
            )
            // 跳过/恢复 + 锁定（右）
            // 审核反馈 4：锁定时不隐藏跳过按钮（锁定只隐藏导航栏，跳过仍可用）。
            Row(verticalAlignment = Alignment.CenterVertically) {
                // #17 审核反馈 #5：跳过按钮无 ripple 方框，按下时 alpha 0.4→1.0 反馈。
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
        // 叠加层：计数器绝对居中（不被左右宽度差影响）
        Text(
            text = "${index + 1} / $total",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

/**
 * 锁定按钮：默认透明 + tertiary 图标；锁定态反色背景（onSurface）+ background 图标。
 *
 * #17 审核反馈 #6：无 ripple 方框。按下时图标 scale 0.9 反馈（pointer-down 即反馈，
 * 不等 release）。
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
 * 单张卡片：单词 → 音标 → 分割线 → 义项列表。
 * 卡片切换的 spring 入场由 HorizontalPager 的页面重组自然驱动（每页 Composable 独立实例）。
 *
 * 审核反馈：去掉「已是第一张 / 已是最后一张」提示 —— 顶部计数器「X / Y」已表达位置，
 * 卡片内再重复提示冗余。
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
            // 审核反馈 2：卡片原垂直居中显得太靠下，改靠上偏移约屏幕 12%
            // （给一个合理的顶部呼吸空间，不顶到顶条，也不沉到中间）。
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 80.dp),
        ) {
            // 英文单词 —— 44sp SemiBold
            Text(
                text = word.word.text,
                style = typography.word.copy(color = colors.onSurface),
                textAlign = TextAlign.Center,
            )
            // 音标 —— Charis SIL，受 hidePhonetic 控制
            if (!word.word.phonetic.isNullOrBlank() && !hidePhonetic) {
                Text(
                    text = word.word.phonetic,
                    style = typography.phonetic.copy(color = colors.onSurfaceVariant),
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
            // 分割线 —— 32×1 separatorStrong
            Box(
                modifier = Modifier
                    .padding(vertical = 20.dp)
                    .width(32.dp)
                    .height(1.dp)
                    .background(MaterialTheme.wordDrillColors.separatorStrong),
            )
            // 义项列表 —— 一行一义项：词性斜体 + 中文同行
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
