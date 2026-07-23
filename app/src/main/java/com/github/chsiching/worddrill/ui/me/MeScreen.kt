package com.github.chsiching.worddrill.ui.me

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.chsiching.worddrill.R

/**
 * 「我的」Tab（Ticket #8）：从 swipe_log 聚合的统计展示。
 *
 * 三个数字，全部响应式 Flow 驱动（[MeViewModel]）：
 * - 今日刷卡数（当日 0 点起的事件计数）
 * - 累计刷卡数（全表计数）
 * - 当前词书进度：已刷 X / 总 Y + 百分比 + 进度条
 *
 * 在「刷」Tab 刷卡后切回本 Tab，数字立即更新。
 * 在「库」Tab 切换当前词书后，进度对应新词书。
 */
@Composable
fun MeScreen(
    modifier: Modifier = Modifier,
    viewModel: MeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    MeStatsContent(state = state, modifier = modifier)
}

/**
 * 统计展示主体（从 [MeScreen] 拆出，按 state 渲染，便于阅读/复用）。
 */
@Composable
private fun MeStatsContent(
    state: MeStatsUiState,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        StatCard(
            label = stringResource(R.string.me_today),
            value = state.todayCount.toString(),
        )
        StatCard(
            label = stringResource(R.string.me_total),
            value = state.totalCount.toString(),
        )

        // 当前词书进度：书名 + 已刷 X / 总 Y (Z%) + 进度条
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.me_progress),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val bookLine = if (state.bookName.isEmpty()) {
                stringResource(R.string.me_no_current_book)
            } else {
                stringResource(
                    R.string.me_progress_line,
                    state.bookName,
                    state.brushed,
                    state.total,
                    state.percent,
                )
            }
            Text(
                text = bookLine,
                style = MaterialTheme.typography.bodyLarge,
            )
            LinearProgressIndicator(
                progress = { state.percent / 100f },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun StatCard(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.displaySmall,
            textAlign = TextAlign.Start,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
