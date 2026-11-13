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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import com.github.chsiching.worddrill.ui.theme.DialogButton
import com.github.chsiching.worddrill.ui.theme.DialogButtonStyle
import com.github.chsiching.worddrill.ui.theme.DialogTextField
import com.github.chsiching.worddrill.ui.theme.LocalThemeRevealTrigger
import com.github.chsiching.worddrill.ui.theme.WordDrillDialog
import com.github.chsiching.worddrill.ui.theme.wordDrillColors
import com.github.chsiching.worddrill.ui.theme.wordDrillTypography
import kotlinx.coroutines.delay

/**
 * "Me" tab (Ticket #8 + #9 + #10 + #16 rewrite): stats card + grouped settings + data
 * export/import.
 *
 * Design spec (#16):
 * - Large "Me" title
 * - Stats card: today's big number (56sp) + current book progress bar (spring width
 *   transition) + lifetime count (24sp)
 * - Settings in three groups: Display (hide phonetics) / Navigation (nav bar style +
 *   compact navigation) / General (theme + export + import + about)
 * - Toggles use [Toggle] (black palette, spring knob animation), not the Material3 green
 *   Switch
 * - Bottom padding 120dp (keeps content clear of the floating nav)
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
        // Large title + theme-switch icon at the top right (review feedback 5)
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
                    // Target theme of the three-state cycle (SYSTEM, LIGHT, DARK, back to SYSTEM)
                    settingsViewModel.setTheme(targetMode)
                },
            )
        }

        // Stats card
        StatsCard(
            state = state,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 0.dp),
        )

        Spacer(Modifier.size(24.dp))

        // Display group
        SettingsGroup(label = stringResource(R.string.me_group_display)) {
            ToggleRow(
                label = stringResource(R.string.me_setting_hide_phonetic),
                on = hidePhonetic,
                onToggle = settingsViewModel::setHidePhonetic,
            )
        }

        Spacer(Modifier.size(16.dp))

        // Navigation group
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

        // General group (review feedback 5: theme switching moved to the top-right icon; no longer a row in this group)
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
 * Stats card: today's big number (56sp) + progress bar (spring width) + lifetime count (24sp).
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
            // Today: the biggest number
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

            // Progress bar: current book
            ProgressBar(
                bookName = if (state.bookName.isEmpty()) stringResource(R.string.me_no_current_book) else state.bookName,
                done = state.brushed,
                total = state.total,
                percent = state.percent,
                modifier = Modifier.padding(top = 28.dp),
            )

            // Lifetime count: secondary stat (top divider + big number + label)
            Box(
                modifier = Modifier
                    .padding(top = 24.dp)
                    .fillMaxWidth(),
                contentAlignment = Alignment.TopCenter,
            ) {
                // Top divider (drawn first; the Column below covers the middle to leave room for text)
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

/** Progress bar: book name + X/Y + bar + percent, spring width transition. */
@Composable
private fun ProgressBar(
    bookName: String,
    done: Int,
    total: Int,
    percent: Int,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    // Spring width transition: spec calls for a 0.5s spring.
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

/** Settings group: title + surface card container. */
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

/** In-group divider (0.5px separator, 16dp inset on each side). */
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

/** Toggle settings row: label on the left, Toggle on the right. */
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

/** Nav bar style row: tap cycles floating pill and bottom bar; current value + chevron on the right. */
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

/** Theme row: tap opens a selection dialog. */
/**
 * Top-right theme-switch icon (review feedback 5).
 *
 * - Currently light: sun icon (tap switches to the next state)
 * - Currently dark: moon icon
 * - Tap triggers [LocalThemeRevealTrigger] for a circular reveal expanding from the icon
 *   center
 * - SYSTEM state: sun or moon depending on the system's current dark/light setting
 */
/**
 * Top-right theme-switch icon (review feedback 5).
 *
 * Tap: compute locally "currently dark? target = the opposite", pass it to
 * [LocalThemeRevealTrigger] to fire the circular reveal; also write DataStore
 * (cycleTheme) to persist.
 */
/**
 * Theme three-state cycle: SYSTEM, LIGHT, DARK, back to SYSTEM.
 * Pure function, so the target can be computed locally (the reveal must know the target
 * the instant it is tapped).
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
    // One icon per state: LIGHT=sun, DARK=moon, SYSTEM=auto (circled A)
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
 * Data export/import section (Ticket #10): embedded in the General group, one row each.
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
        WordDrillDialog(
            onDismissRequest = { showExportDialog = false },
            title = stringResource(R.string.me_export_title),
            message = stringResource(R.string.me_export_message),
            content = {
                DialogTextField(
                    value = nicknameInput,
                    onValueChange = { nicknameInput = it },
                    label = stringResource(R.string.me_export_nickname_hint),
                    singleLine = true,
                )
            },
            buttons = {
                DialogButton(
                    text = stringResource(R.string.me_cancel),
                    onClick = { showExportDialog = false },
                    style = DialogButtonStyle.Cancel,
                )
                DialogButton(
                    text = stringResource(R.string.me_export_confirm),
                    onClick = {
                        showExportDialog = false
                        pendingNickname = nicknameInput.trim().ifEmpty { null }
                        exportLauncher.launch("worddrill-backup.json")
                    },
                    style = DialogButtonStyle.Primary,
                )
            },
        )
    }

    if (showImportDialog) {
        WordDrillDialog(
            onDismissRequest = { showImportDialog = false },
            title = stringResource(R.string.me_import_title),
            message = stringResource(R.string.me_import_message),
            buttons = {
                DialogButton(
                    text = stringResource(R.string.me_cancel),
                    onClick = { showImportDialog = false },
                    style = DialogButtonStyle.Cancel,
                )
                DialogButton(
                    text = stringResource(R.string.me_import_confirm),
                    onClick = {
                        showImportDialog = false
                        importLauncher.launch(arrayOf("application/json", "*/*"))
                    },
                    style = DialogButtonStyle.Primary,
                )
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

/** About row: version number on the right. */
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

/** Export/import status line. */
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
    WordDrillDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.me_about_title),
        message = stringResource(R.string.me_about_app_name),
        content = {
            Text(
                text = stringResource(R.string.me_about_version, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        buttons = {
            DialogButton(
                text = stringResource(R.string.me_about_close),
                onClick = onDismiss,
                style = DialogButtonStyle.Primary,
            )
        },
    )
}
