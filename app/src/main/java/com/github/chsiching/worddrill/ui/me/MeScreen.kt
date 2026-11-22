package com.github.chsiching.worddrill.ui.me

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.BrightnessAuto
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.chsiching.worddrill.BuildConfig
import com.github.chsiching.worddrill.R
import com.github.chsiching.worddrill.data.settings.DeckOrdering
import com.github.chsiching.worddrill.data.settings.DwellInterval
import com.github.chsiching.worddrill.data.settings.NavStyle
import com.github.chsiching.worddrill.data.settings.PhoneticPreference
import com.github.chsiching.worddrill.data.settings.ThemeMode
import com.github.chsiching.worddrill.ui.theme.DialogButton
import com.github.chsiching.worddrill.ui.theme.DialogButtonStyle
import com.github.chsiching.worddrill.ui.theme.DialogTextField
import com.github.chsiching.worddrill.ui.theme.LocalThemeRevealTrigger
import com.github.chsiching.worddrill.ui.theme.WordDrillDialog
import com.github.chsiching.worddrill.ui.theme.wordDrillColors
import com.github.chsiching.worddrill.ui.theme.wordDrillTypography
import com.github.chsiching.worddrill.update.UpdateDialogHost
import com.github.chsiching.worddrill.update.UpdateUiState
import com.github.chsiching.worddrill.update.UpdateViewModel
import kotlinx.coroutines.delay

@Composable
fun MeScreen(
    onNavigateToRecycleBin: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MeViewModel = hiltViewModel(),
    settingsViewModel: SettingsViewModel = hiltViewModel(),
    exportImportViewModel: ExportImportViewModel = hiltViewModel(),
    updateViewModel: UpdateViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val themeMode by settingsViewModel.themeMode.collectAsStateWithLifecycle()
    val hidePhonetic by settingsViewModel.hidePhonetic.collectAsStateWithLifecycle()
    val phoneticPreference by settingsViewModel.phoneticPreference.collectAsStateWithLifecycle()
    val navStyle by settingsViewModel.navStyle.collectAsStateWithLifecycle()
    val compactNav by settingsViewModel.compactNav.collectAsStateWithLifecycle()
    val dwellInterval by settingsViewModel.dwellInterval.collectAsStateWithLifecycle()
    val deckOrdering by settingsViewModel.deckOrdering.collectAsStateWithLifecycle()
    val exportStatus by exportImportViewModel.status.collectAsStateWithLifecycle()
    val updateState by updateViewModel.state.collectAsStateWithLifecycle()
    var showAbout by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 120.dp),
    ) {
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
                    settingsViewModel.setTheme(targetMode)
                },
            )
        }

        StatsCard(
            state = state,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 0.dp),
        )

        Spacer(Modifier.size(24.dp))

        SettingsGroup(label = stringResource(R.string.me_group_display)) {
            ToggleRow(
                label = stringResource(R.string.me_setting_hide_phonetic),
                on = hidePhonetic,
                onToggle = settingsViewModel::setHidePhonetic,
            )
            InGroupSeparator()
            PhoneticPreferenceRow(
                current = phoneticPreference,
                onClick = settingsViewModel::cyclePhoneticPreference,
            )
            InGroupSeparator()
            DwellIntervalRow(
                value = dwellInterval,
                onValueChange = settingsViewModel::setDwellInterval,
            )
            InGroupSeparator()
            DeckOrderingRow(
                current = deckOrdering,
                onClick = settingsViewModel::cycleDeckOrdering,
            )
        }

        Spacer(Modifier.size(16.dp))

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
            DataRow(
                label = stringResource(R.string.me_setting_check_update),
                onClick = updateViewModel::manualCheck,
            )
            InGroupSeparator()
            AboutRow(onClick = { showAbout = true })
        }
    }

    // The update dialog for a manual check with something to install; the launch
    // check's dialog still comes from UpdateGate in MainActivity.
    UpdateDialogHost(viewModel = updateViewModel)

    // Manual-check results (up to date / network error) in the standard dialog.
    when (val current = updateState) {
        is UpdateUiState.UpToDate -> UpdateResultDialog(
            message = stringResource(R.string.update_up_to_date),
            onDismiss = updateViewModel::dismissNotice,
        )
        is UpdateUiState.Notice -> UpdateResultDialog(
            message = stringResource(current.messageRes),
            onDismiss = updateViewModel::dismissNotice,
        )
        else -> Unit
    }

    if (showAbout) {
        AboutDialog(onDismiss = { showAbout = false })
    }
}

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

            ProgressBar(
                bookName = if (state.bookName.isEmpty()) stringResource(R.string.me_no_current_book) else state.bookName,
                done = state.brushed,
                total = state.total,
                percent = state.percent,
                modifier = Modifier.padding(top = 28.dp),
            )

            // Per-book completion count (GLOSSARY.md); only a book with at least one
            // finished pass has anything to say here.
            if (state.completionCount > 0) {
                Text(
                    text = stringResource(R.string.me_completion_count, state.completionCount),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }

            Box(
                modifier = Modifier
                    .padding(top = 24.dp)
                    .fillMaxWidth(),
                contentAlignment = Alignment.TopCenter,
            ) {
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

@Composable
private fun ProgressBar(
    bookName: String,
    done: Int,
    total: Int,
    percent: Double,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val animatedFraction by animateFloatAsState(
        targetValue = (percent / 100.0).toFloat().coerceIn(0f, 1f),
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
            text = MeStats.formatPercent(percent),
            style = MaterialTheme.typography.labelSmall,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.End,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
        )
    }
}

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

/** Settings row with a label, the current value, and a chevron; tapping cycles the
 *  setting. Shared anatomy of the nav-style, deck-ordering, and phonetic-preference
 *  rows. */
@Composable
private fun SettingValueRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
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

@Composable
private fun NavStyleRow(current: NavStyle, onClick: () -> Unit) {
    SettingValueRow(
        label = stringResource(R.string.me_setting_nav_style),
        value = stringResource(
            if (current == NavStyle.PILL) R.string.me_setting_nav_style_pill else R.string.me_setting_nav_style_bar
        ),
        onClick = onClick,
    )
}

/** Deck ordering (GLOSSARY.md, ADR 0002): global alphabetical / shuffled two-state
 *  row in the display group. Tapping cycles; the change takes effect on the next
 *  book open. */
@Composable
private fun DeckOrderingRow(current: DeckOrdering, onClick: () -> Unit) {
    SettingValueRow(
        label = stringResource(R.string.me_setting_deck_ordering),
        value = stringResource(
            if (current == DeckOrdering.ALPHABETICAL) {
                R.string.me_setting_deck_ordering_alphabetical
            } else {
                R.string.me_setting_deck_ordering_shuffled
            }
        ),
        onClick = onClick,
    )
}

/** Phonetic preference (GLOSSARY.md, issue #58): global UK / US / both row in the
 *  display group, next to the hide-phonetic toggle. Tapping cycles; the card
 *  resolves variants per word at render time, falling back to the available
 *  variant (ADR 0003). */
@Composable
private fun PhoneticPreferenceRow(current: PhoneticPreference, onClick: () -> Unit) {
    SettingValueRow(
        label = stringResource(R.string.me_setting_phonetic_preference),
        value = stringResource(
            when (current) {
                PhoneticPreference.UK -> R.string.me_setting_phonetic_preference_uk
                PhoneticPreference.US -> R.string.me_setting_phonetic_preference_us
                PhoneticPreference.BOTH -> R.string.me_setting_phonetic_preference_both
            }
        ),
        onClick = onClick,
    )
}

/** Test tag on the dwell-interval slider so instrumented tests can locate the control. */
internal const val DwellSliderTestTag = "dwellIntervalSlider"

/** Value row for the auto-advance dwell interval; tapping expands an inline slider
 *  inside the same group card. Drags apply instantly - no dialog, no draft. */
@Composable
private fun DwellIntervalRow(value: Float, onValueChange: (Float) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        animationSpec = spring(),
        label = "dwellChevronRotation",
    )
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.me_setting_dwell_interval),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(R.string.me_setting_dwell_value, value),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(18.dp)
                    .rotate(chevronRotation),
            )
        }
        if (expanded) {
            Column(
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 4.dp),
            ) {
                DwellSlider(value = value, onValueChange = onValueChange)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = stringResource(R.string.me_setting_dwell_hint_min),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                    Text(
                        text = stringResource(R.string.me_setting_dwell_hint_max),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
                Text(
                    text = stringResource(R.string.me_setting_dwell_caption),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.padding(top = 8.dp, bottom = 12.dp),
                )
            }
        }
    }
}

/** Custom slider in the app's own control grammar (approved prototype): the 6 dp stats
 *  progress track (progressTrack base + onSurface fill) with a switch-knob thumb
 *  (surface fill, hairline border, whisper shadow) that grows slightly while pressed. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DwellSlider(value: Float, onValueChange: (Float) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val thumbScale by animateFloatAsState(
        targetValue = if (pressed) 1.12f else 1f,
        animationSpec = spring(),
        label = "dwellThumbScale",
    )
    val shadowColor = Color.Black.copy(alpha = 0.12f)
    Slider(
        value = value,
        onValueChange = onValueChange,
        valueRange = DwellInterval.MIN_SECONDS..DwellInterval.MAX_SECONDS,
        steps = DwellInterval.STEPS_BETWEEN_BOUNDS,
        interactionSource = interactionSource,
        thumb = {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .shadow(2.dp, CircleShape, ambientColor = shadowColor, spotColor = shadowColor)
                    .background(colors.surface, CircleShape)
                    .border(1.dp, MaterialTheme.wordDrillColors.separatorStrong, CircleShape)
                    .scale(thumbScale),
            )
        },
        track = { state ->
            val fraction = (
                (state.value - DwellInterval.MIN_SECONDS) /
                    (DwellInterval.MAX_SECONDS - DwellInterval.MIN_SECONDS)
                ).coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .background(MaterialTheme.wordDrillColors.progressTrack, RoundedCornerShape(3.dp)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction)
                        .height(6.dp)
                        .background(colors.onSurface, RoundedCornerShape(3.dp)),
                )
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .testTag(DwellSliderTestTag),
    )
}

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

/** Manual check-update result (up to date / network error) in the app's standard dialog. */
@Composable
private fun UpdateResultDialog(message: String, onDismiss: () -> Unit) {
    WordDrillDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.me_setting_check_update),
        message = message,
        buttons = {
            DialogButton(
                text = stringResource(R.string.library_confirm),
                onClick = onDismiss,
                style = DialogButtonStyle.Primary,
            )
        },
    )
}

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
            // Structured label/value facts (owner direction: no prose blobs), left
            // aligned while the title and version stay centered. The count value's
            // lines are broken manually in the string resource.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AboutFact(
                    label = stringResource(R.string.me_about_fact_source_label),
                    lines = listOf(stringResource(R.string.me_about_fact_source_value)),
                )
                AboutFact(
                    label = stringResource(R.string.me_about_fact_form_label),
                    lines = listOf(stringResource(R.string.me_about_fact_form_value)),
                )
                AboutFact(
                    label = stringResource(R.string.me_about_fact_count_label),
                    // Manually broken short lines (owner direction): each line is
                    // its own Text so no line can auto-wrap into an orphan.
                    lines = listOf(
                        stringResource(R.string.me_about_fact_count_line1),
                        stringResource(R.string.me_about_fact_count_line2),
                        stringResource(R.string.me_about_fact_count_line3),
                    ),
                )
            }
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

/** One label/value pair of the About dialog's facts block; the value is one or
 *  more manually broken lines, each its own Text. */
@Composable
private fun AboutFact(label: String, lines: List<String>) {
    Column {
        Text(
            text = label,
            fontSize = 11.sp,
            letterSpacing = 0.5.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        lines.forEachIndexed { index, line ->
            Text(
                text = line,
                fontSize = 13.sp,
                lineHeight = (13 * 1.55).sp,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = if (index == 0) Modifier.padding(top = 3.dp) else Modifier,
            )
        }
    }
}
