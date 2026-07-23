package com.github.chsiching.worddrill.ui.me

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import kotlinx.coroutines.delay

/**
 * 「我的」Tab（Ticket #8 + #9 + #10）：统计展示 + 软件设置 + 数据导出/导入。
 *
 * 统计（#8）：从 swipe_log 聚合的三个数字，全部响应式 Flow 驱动（[MeViewModel]）。
 * 在「刷」Tab 刷卡后切回本 Tab，数字立即更新；切换当前词书后进度对应新词书。
 *
 * 设置（#9）：主题切换（浅色/深色/跟随系统）+ 关于入口。主题写入 DataStore，
 * [com.github.chsiching.worddrill.MainActivity] 同样订阅 → 全局配色立即生效。
 *
 * 数据（#10）：整库导出/导入（SAF 选文件，JSON 格式）。导入默认覆盖，用于换机迁移。
 */
@Composable
fun MeScreen(
    modifier: Modifier = Modifier,
    viewModel: MeViewModel = hiltViewModel(),
    settingsViewModel: SettingsViewModel = hiltViewModel(),
    exportImportViewModel: ExportImportViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val themeMode by settingsViewModel.themeMode.collectAsStateWithLifecycle()
    val exportStatus by exportImportViewModel.status.collectAsStateWithLifecycle()
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
        MeDataContent(status = exportStatus, viewModel = exportImportViewModel)
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

/**
 * 数据导出/导入区（Ticket #10）。
 *
 * SAF 文件选择由本 Composable 内的 launcher 发起：
 * - 导出：先弹昵称输入框（可选），确认后 launch [ActivityResultContracts.CreateDocument]；
 *   SAF 回调 uri → [ExportImportViewModel.exportTo]。
 * - 导入：先弹覆盖确认框，确认后 launch [ActivityResultContracts.OpenDocument]；
 *   SAF 回调 uri → [ExportImportViewModel.importFrom]。
 *
 * 状态：[ExportImportStatus]，Done/Failed 展示几秒后自动清回 Idle（[LaunchedEffect]）。
 */
@Composable
private fun MeDataContent(
    status: ExportImportStatus,
    viewModel: ExportImportViewModel,
) {
    // SAF 导出：pendingNickname 在 dialog 确认时记下，SAF 回调时取用
    var pendingNickname by remember { mutableStateOf<String?>(null) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri: Uri? ->
        if (uri != null) viewModel.exportTo(uri, pendingNickname)
        pendingNickname = null
    }
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri != null) viewModel.importFrom(uri)
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.me_data),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // 坑 G：用 substring=false 或完整文案消歧；这里按钮文案独立无重复，无歧义
            Button(
                onClick = { showExportDialog = true },
                enabled = status !is ExportImportStatus.Working,
                modifier = Modifier.weight(1f),
            ) { Text(stringResource(R.string.me_export)) }
            OutlinedButton(
                onClick = { showImportDialog = true },
                enabled = status !is ExportImportStatus.Working,
                modifier = Modifier.weight(1f),
            ) { Text(stringResource(R.string.me_import)) }
        }
        StatusLine(status = status, onClear = viewModel::clearStatus)
    }

    if (showExportDialog) {
        var nicknameInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = { Text(stringResource(R.string.me_export_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.me_export_message))
                    OutlinedTextField(
                        value = nicknameInput,
                        onValueChange = { nicknameInput = it },
                        label = { Text(stringResource(R.string.me_export_nickname_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showExportDialog = false
                    // 记下昵称（空白视为无昵称），SAF 回调时取用
                    pendingNickname = nicknameInput.trim().ifEmpty { null }
                    exportLauncher.launch("worddrill-backup.json")
                }) { Text(stringResource(R.string.me_export_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showExportDialog = false }) {
                    Text(stringResource(R.string.me_cancel))
                }
            },
        )
    }

    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            title = { Text(stringResource(R.string.me_import_title)) },
            text = { Text(stringResource(R.string.me_import_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showImportDialog = false
                    importLauncher.launch(arrayOf("application/json", "*/*"))
                }) { Text(stringResource(R.string.me_import_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showImportDialog = false }) {
                    Text(stringResource(R.string.me_cancel))
                }
            },
        )
    }
}

/** 导出/导入状态行：Working/Failed 展示文案；Done 短暂展示后自动清回 Idle。 */
@Composable
private fun StatusLine(status: ExportImportStatus, onClear: () -> Unit) {
    when (status) {
        ExportImportStatus.Idle -> Unit
        ExportImportStatus.Working -> {
            Text(
                text = stringResource(R.string.me_working),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        is ExportImportStatus.Done -> {
            Text(
                text = stringResource(status.messageRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
            LaunchedEffect(status) {
                delay(2_000)
                onClear()
            }
        }
        is ExportImportStatus.Failed -> {
            // 失败文案：失败标签 + 异常详情（若有）；详情多为英文/路径，仅辅助排障
            val message = status.detail?.let { "${stringResource(status.messageRes)}：$it" }
                ?: stringResource(status.messageRes)
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
            LaunchedEffect(status) {
                delay(4_000)
                onClear()
            }
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
