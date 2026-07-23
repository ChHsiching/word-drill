package com.github.chsiching.worddrill.ui.me

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.chsiching.worddrill.BuildConfig
import com.github.chsiching.worddrill.R
import com.github.chsiching.worddrill.data.settings.ThemeMode

/**
 * 「我的」Tab（Ticket #8 + #9）：统计展示 + 软件设置。
 *
 * 统计（#8）：从 swipe_log 聚合的三个数字，全部响应式 Flow 驱动（[MeViewModel]）。
 * 在「刷」Tab 刷卡后切回本 Tab，数字立即更新；切换当前词书后进度对应新词书。
 *
 * 设置（#9）：主题切换（浅色/深色/跟随系统）+ 关于入口。主题写入 DataStore，
 * [com.github.chsiching.worddrill.MainActivity] 同样订阅 → 全局配色立即生效。
 */
@Composable
fun MeScreen(
    modifier: Modifier = Modifier,
    viewModel: MeViewModel = hiltViewModel(),
    settingsViewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val themeMode by settingsViewModel.themeMode.collectAsStateWithLifecycle()
    var showAbout by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        MeStatsContent(state = state)
        MeSettingsContent(
            currentTheme = themeMode,
            onThemeSelected = settingsViewModel::setTheme,
            onAboutClick = { showAbout = true },
        )
    }

    if (showAbout) {
        AboutDialog(onDismiss = { showAbout = false })
    }
}

/**
 * 统计展示主体（从 [MeScreen] 拆出，按 state 渲染，便于阅读/复用）。
 */
@Composable
private fun MeStatsContent(state: MeStatsUiState) {
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

/**
 * 软件设置区（Ticket #9）：主题三选一 + 关于入口。
 */
@Composable
private fun MeSettingsContent(
    currentTheme: ThemeMode,
    onThemeSelected: (ThemeMode) -> Unit,
    onAboutClick: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.me_settings),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.me_theme),
            style = MaterialTheme.typography.bodyLarge,
        )
        themeOptions.forEach { (mode, labelRes) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = mode == currentTheme,
                        onClick = { onThemeSelected(mode) },
                    )
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                RadioButton(
                    selected = mode == currentTheme,
                    onClick = null, // selectable 在 Row 上处理点击
                )
                Text(
                    text = stringResource(labelRes),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
        TextButton(onClick = onAboutClick, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.me_about))
        }
    }
}

/** 主题三选项：(模式, 文案资源)。集中一处便于测试与扩展。 */
private val themeOptions: List<Pair<ThemeMode, Int>> = listOf(
    ThemeMode.LIGHT to R.string.me_theme_light,
    ThemeMode.DARK to R.string.me_theme_dark,
    ThemeMode.SYSTEM to R.string.me_theme_system,
)

/**
 * 关于对话框（Ticket #9）：App 名 + 版本号。
 * 版本号读 [BuildConfig.VERSION_NAME]（由 versionName 注入，buildConfig feature 已启用）。
 */
@Composable
private fun AboutDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.me_about_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.me_about_app_name),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(R.string.me_about_version, BuildConfig.VERSION_NAME),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.me_about_close))
            }
        },
    )
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
