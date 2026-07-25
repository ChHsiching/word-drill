package com.github.chsiching.worddrill.ui.me

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.BrightnessAuto
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.chsiching.worddrill.BuildConfig
import com.github.chsiching.worddrill.R
import com.github.chsiching.worddrill.data.settings.NavStyle
import com.github.chsiching.worddrill.data.settings.ThemeMode
import com.github.chsiching.worddrill.ui.theme.LocalThemeRevealTrigger
import com.github.chsiching.worddrill.ui.theme.wordDrillColors
import com.github.chsiching.worddrill.ui.theme.wordDrillTypography
import kotlinx.coroutines.delay

/**
 * 「我的」Tab（Ticket #8 + #9 + #10 + #16 重写）：统计卡片 + 分组设置 + 数据导出/导入。
 *
 * 设计稿（#16）：
 * - 大标题「我的」
 * - 统计卡片：今日大数字（56sp）+ 当前词书进度条（spring 宽度过渡）+ 累计（24sp）
 * - 设置分三组：显示（隐藏音标）/ 导航（导航栏风格 + 简约导航）/ 通用（主题 + 导出 + 导入 + 关于）
 * - toggle 用 [Toggle]（黑色配色，spring knob 动画），非 Material3 绿色 Switch
 * - 底部 padding 120dp（避免被浮动导航遮挡）
 */
@Composable
fun MeScreen(
    onNavigateToRecycleBin: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MeViewModel = hiltViewModel(),
    settingsViewModel: SettingsViewModel = hiltViewModel(),
    exportImportViewModel: ExportImportViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val themeMode by settingsViewModel.themeMode.collectAsStateWithLifecycle()
    val hidePhonetic by settingsViewModel.hidePhonetic.collectAsStateWithLifecycle()
    val navStyle by settingsViewModel.navStyle.collectAsStateWithLifecycle()
    val compactNav by settingsViewModel.compactNav.collectAsStateWithLifecycle()
    val exportStatus by exportImportViewModel.status.collectAsStateWithLifecycle()
    var showAbout by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 120.dp),
    ) {
        // 大标题 + 右上角主题切换 icon（审核反馈 5）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, top = 24.dp, end = 24.dp, bottom = 28.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.tab_me),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            ThemeToggleIcon(
                themeMode = themeMode,
                onToggle = { targetMode ->
                    // 三态循环的目标主题（SYSTEM → LIGHT → DARK → SYSTEM）
                    settingsViewModel.setTheme(targetMode)
                },
            )
        }

        // 统计卡片
        StatsCard(
            state = state,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 0.dp),
        )

        Spacer(Modifier.size(24.dp))

        // 显示组
        SettingsGroup(label = stringResource(R.string.me_group_display)) {
            ToggleRow(
                label = stringResource(R.string.me_setting_hide_phonetic),
                on = hidePhonetic,
                onToggle = settingsViewModel::setHidePhonetic,
            )
        }

        Spacer(Modifier.size(16.dp))

        // 导航组
        SettingsGroup(label = stringResource(R.string.me_group_navigation)) {
            NavStyleRow(
                current = navStyle,
                onClick = settingsViewModel::cycleNavStyle,
            )
            InGroupSeparator()
            ToggleRow(
                label = stringResource(R.string.me_setting_compact_nav),
                on = compactNav,
                onToggle = settingsViewModel::setCompactNav,
            )
        }

        Spacer(Modifier.size(16.dp))

        // 通用组（审核反馈 5：主题切换移到右上角 icon，不再在通用组里占一行）
        SettingsGroup(label = stringResource(R.string.me_group_general)) {
            DataSection(
                status = exportStatus,
                viewModel = exportImportViewModel,
            )
            InGroupSeparator()
            DataRow(
                label = stringResource(R.string.me_setting_recycle_bin),
                onClick = onNavigateToRecycleBin,
            )
            InGroupSeparator()
            AboutRow(onClick = { showAbout = true })
        }
    }

    if (showAbout) {
        AboutDialog(onDismiss = { showAbout = false })
    }
}

/**
 * 统计卡片：今日大数字（56sp）+ 进度条（spring 宽度）+ 累计（24sp）。
 */
@Composable
private fun StatsCard(state: MeStatsUiState, modifier: Modifier = Modifier) {
    val typography = MaterialTheme.wordDrillTypography
    val colors = MaterialTheme.colorScheme
    Surface(
        color = colors.surface,
        shape = RoundedCornerShape(20.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // 今日 —— 最大数字
            Text(
                text = state.todayCount.toString(),
                style = typography.statNumberLarge.copy(color = colors.onSurface),
            )
            Text(
                text = stringResource(R.string.me_today),
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )

            // 进度条 —— 当前词书
            ProgressBar(
                bookName = if (state.bookName.isEmpty()) stringResource(R.string.me_no_current_book) else state.bookName,
                done = state.brushed,
                total = state.total,
                percent = state.percent,
                modifier = Modifier.padding(top = 28.dp),
            )

            // 累计 —— 次级统计（顶部分隔线 + 大数字 + 标签）
            Box(
                modifier = Modifier
                    .padding(top = 24.dp)
                    .fillMaxWidth(),
                contentAlignment = Alignment.TopCenter,
            ) {
                // 顶部分隔线（先画，下方 Column 覆盖中间留出文字）
                Box(
                    modifier = Modifier
                        .height(1.dp)
                        .fillMaxWidth()
                        .background(MaterialTheme.wordDrillColors.separator),
                )
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .padding(top = 20.dp)
                        .fillMaxWidth(),
                ) {
                    Text(
                        text = state.totalCount.toString(),
                        style = typography.statNumber.copy(color = colors.onSurface),
                    )
                    Text(
                        text = stringResource(R.string.me_total),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
    }
}

/** 进度条：书名 + X/Y + 条 + 百分比，spring 宽度过渡。 */
@Composable
private fun ProgressBar(
    bookName: String,
    done: Int,
    total: Int,
    percent: Int,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    // spring 宽度过渡：spec 0.5s spring。
    val animatedFraction by animateFloatAsState(
        targetValue = (percent / 100f).coerceIn(0f, 1f),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "progressWidth",
    )
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = bookName,
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant,
            )
            Text(
                text = "$done / $total",
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurface,
                fontWeight = FontWeight.Medium,
            )
        }
        Spacer(Modifier.size(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .background(MaterialTheme.wordDrillColors.progressTrack, RoundedCornerShape(3.dp)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedFraction)
                    .height(6.dp)
                    .background(colors.onSurface, RoundedCornerShape(3.dp)),
            )
        }
        Text(
            text = "$percent%",
            style = MaterialTheme.typography.labelSmall,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.End,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
        )
    }
}

/** 设置分组：标题 + surface 卡片容器。 */
@Composable
private fun SettingsGroup(label: String, content: @Composable () -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 12.dp, bottom = 8.dp),
        )
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(14.dp),
        ) {
            Column { content() }
        }
    }
}

/** 组内分隔线（0.5px separator，左右各留 16dp）。 */
@Composable
private fun InGroupSeparator() {
    Box(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.wordDrillColors.separator),
    )
}

/** Toggle 设置行：左侧文案，右侧 Toggle。 */
@Composable
private fun ToggleRow(label: String, on: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Toggle(on = on, onClick = { onToggle(!on) })
    }
}

/** 导航栏风格行：点击循环 浮动胶囊 ↔ 底部栏，右侧当前值 + chevron。 */
@Composable
private fun NavStyleRow(current: NavStyle, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.me_setting_nav_style),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = stringResource(
                if (current == NavStyle.PILL) R.string.me_setting_nav_style_pill else R.string.me_setting_nav_style_bar
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
    }
}

/** 主题行：点击弹选择对话框。 */
/**
 * 右上角主题切换 icon（审核反馈 5）。
 *
 * - 当前浅色 → 显示太阳 icon（点击切到下一态）
 * - 当前深色 → 显示月亮 icon
 * - 点击触发 [LocalThemeRevealTrigger]，以 icon 中心为圆心做 circular reveal 扩散
 * - SYSTEM 态：按系统当前深浅决定显示太阳还是月亮
 */
/**
 * 右上角主题切换 icon（审核反馈 5）。
 *
 * 点击：本地算「当前是否深色 → 目标 = 反过来」，传给 [LocalThemeRevealTrigger]
 * 触发 circular reveal；同时写 DataStore（cycleTheme）持久化。
 */
/**
 * 主题三态循环：SYSTEM → LIGHT → DARK → SYSTEM。
 * 纯函数，便于本地预算目标（reveal 需要点击瞬间知道目标）。
 */
private fun nextThemeMode(current: ThemeMode): ThemeMode = when (current) {
    ThemeMode.SYSTEM -> ThemeMode.LIGHT
    ThemeMode.LIGHT -> ThemeMode.DARK
    ThemeMode.DARK -> ThemeMode.SYSTEM
}

@Composable
private fun ThemeToggleIcon(
    themeMode: ThemeMode,
    onToggle: (targetMode: ThemeMode) -> Unit,
) {
    val trigger = LocalThemeRevealTrigger.current
    // 三态各有 icon：LIGHT=太阳，DARK=月亮，SYSTEM=自动（A 带圈）
    val iconVector = when (themeMode) {
        ThemeMode.LIGHT -> Icons.Outlined.LightMode
        ThemeMode.DARK -> Icons.Outlined.DarkMode
        ThemeMode.SYSTEM -> Icons.Outlined.BrightnessAuto
    }

    var iconPos by remember { mutableStateOf(Offset.Zero) }
    var iconSize by remember { mutableStateOf(0) }

    Box(
        modifier = Modifier
            .onGloballyPositioned { coords ->
                iconPos = coords.positionInWindow()
                iconSize = coords.size.width
            },
        contentAlignment = Alignment.Center,
    ) {
        IconButton(
            onClick = {
                val center = Offset(
                    iconPos.x + iconSize / 2f,
                    iconPos.y + iconSize / 2f,
                )
                val target = nextThemeMode(themeMode)
                trigger(center) { onToggle(target) }
            },
        ) {
            Icon(
                imageVector = iconVector,
                contentDescription = stringResource(R.string.me_theme),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

/**
 * 数据导出/导入区（Ticket #10）：嵌在通用组里，两项各一行。
 */
@Composable
private fun DataSection(
    status: ExportImportStatus,
    viewModel: ExportImportViewModel,
) {
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

    DataRow(
        label = stringResource(R.string.me_setting_export),
        onClick = { showExportDialog = true },
        enabled = status !is ExportImportStatus.Working,
    )
    InGroupSeparator()
    DataRow(
        label = stringResource(R.string.me_setting_import),
        onClick = { showImportDialog = true },
        enabled = status !is ExportImportStatus.Working,
    )
    if (status !is ExportImportStatus.Idle) {
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

@Composable
private fun DataRow(label: String, onClick: () -> Unit, enabled: Boolean = true) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .then(if (!enabled) Modifier.alpha(0.5f) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
    }
}

/** 关于行：右侧版本号。 */
@Composable
private fun AboutRow(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.me_setting_about),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = BuildConfig.VERSION_NAME,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 导出/导入状态行。 */
@Composable
private fun StatusLine(status: ExportImportStatus, onClear: () -> Unit) {
    when (status) {
        ExportImportStatus.Idle -> Unit
        ExportImportStatus.Working -> {
            Text(
                text = stringResource(R.string.me_working),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        is ExportImportStatus.Done -> {
            Text(
                text = stringResource(status.messageRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            LaunchedEffect(status) {
                delay(2_000)
                onClear()
            }
        }
        is ExportImportStatus.Failed -> {
            val message = status.detail?.let { "${stringResource(status.messageRes)}：$it" }
                ?: stringResource(status.messageRes)
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            LaunchedEffect(status) {
                delay(4_000)
                onClear()
            }
        }
    }
}

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
