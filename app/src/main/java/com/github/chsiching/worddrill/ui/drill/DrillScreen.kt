package com.github.chsiching.worddrill.ui.drill

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.chsiching.worddrill.R
import com.github.chsiching.worddrill.data.local.WordWithSenses
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop

/**
 * 「刷」Tab：全屏单卡片浏览。
 *
 * - 顶部纯文字展示当前词书名（不可点击、不可交互）
 * - 中间 HorizontalPager：一页 = 一个 word 的全部 sense（pos + meaning）
 * - 向右滑切下一张并写 swipe_log（计数在 ViewModel，由 [DrillViewModel.onPageSettled] 处理）
 * - 向左滑切上一张，不计数
 * - 到头（第一张/最后一张）显示提示文案，不计数
 */
@Composable
fun DrillScreen(
    modifier: Modifier = Modifier,
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
            modifier = modifier,
        )
    }
}

@Composable
internal fun DrillPager(
    bookName: String,
    cards: List<WordWithSenses>,
    onPageSettled: (previousPage: Int, currentPage: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 线性推进、不循环：pageCount 固定、initialPage = 0。
    val pagerState = rememberPagerState(pageCount = { cards.size })

    // 监听 settled 页面变化：每次页面真正落定，回调 ViewModel 做计数决策。
    // 用局部变量记住上一次落定的页码，配对成 (previous, current) 交给纯函数决策。
    // drop(1) 跳过初始页（首次显示不算一次滑动）；
    // distinctUntilChanged 防止 settledPage 在同一值上重复发射导致重复计数。
    LaunchedEffect(pagerState) {
        var previousSettled = pagerState.settledPage
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .drop(1) // 跳过初始值，首次落定不计数
            .collect { current ->
                onPageSettled(previousSettled, current)
                previousSettled = current
            }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // 顶部：当前词书名（纯文字，不可交互）
        Text(
            text = bookName,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // 中间：全屏卡片
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
        ) { pageIndex ->
            WordCard(
                word = cards[pageIndex],
                isAtFirstBoundary = pageIndex == 0,
                isAtLastBoundary = pageIndex == cards.lastIndex,
            )
        }
    }
}

@Composable
private fun WordCard(
    word: WordWithSenses,
    isAtFirstBoundary: Boolean,
    isAtLastBoundary: Boolean,
) {
    // 到头提示在当前页底部，提示不计数（计数只由 onPageSettled 决定）
    val boundaryHint = when {
        isAtFirstBoundary && isAtLastBoundary -> null // 仅一张卡：不提示到头
        isAtFirstBoundary -> stringResource(R.string.drill_first_card)
        isAtLastBoundary -> stringResource(R.string.drill_last_card)
        else -> null
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 英文单词（大字号居中）
            Text(
                text = word.word.text,
                style = MaterialTheme.typography.displaySmall,
                textAlign = TextAlign.Center,
            )
            // 每条释义一行：词性 + 中文释义
            word.senses.forEach { sense ->
                Text(
                    text = "${sense.pos}  ${sense.meaning}",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            if (boundaryHint != null) {
                Text(
                    text = boundaryHint,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 16.dp),
                )
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
