package com.github.chsiching.worddrill.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// ═══════════════════════════════════════════════════════════
// Ticket #15 — 纯黑白灰配色 token（来源：designs/worddrill-ui/index.html 的 :root）
// 与 Material3 ColorScheme 的映射见 Theme.kt；超出 M3 slot 的语义 token
// （chipBg / separator / progressTrack）通过 [WordDrillColors] 暴露。
// ═══════════════════════════════════════════════════════════

// ── Light ───────────────────────────────────────────────────
val BgLight = Color(0xFFFAFAFA)
val SurfaceLight = Color(0xFFFFFFFF)
val SurfaceElevatedLight = Color(0xFFFFFFFF)
val TextPrimaryLight = Color(0xFF1A1A1A)
val TextSecondaryLight = Color(0xFF86868B)
val TextTertiaryLight = Color(0xFFAEAEB2)
val SeparatorLight = Color(0x0F000000) // rgba(0,0,0,0.06)
val SeparatorStrongLight = Color(0x1F000000) // rgba(0,0,0,0.12)
val ProgressTrackLight = Color(0xFFE8E8ED)
val ChipBgLight = Color(0xFFF5F5F7)

// ── Dark ────────────────────────────────────────────────────
val BgDark = Color(0xFF000000)
val SurfaceDark = Color(0xFF1C1C1E)
val SurfaceElevatedDark = Color(0xFF2C2C2E)
val TextPrimaryDark = Color(0xFFFFFFFF)
val TextSecondaryDark = Color(0xFF98989F)
val TextTertiaryDark = Color(0xFF636366)
val SeparatorDark = Color(0x14FFFFFF) // rgba(255,255,255,0.08)
val SeparatorStrongDark = Color(0x26FFFFFF) // rgba(255,255,255,0.15)
val ProgressTrackDark = Color(0xFF38383A)
val ChipBgDark = Color(0xFF2C2C2E)

/**
 * 语义 token：超出 Material3 [ColorScheme] 的设计稿独有色（chip 背景 / 分隔线 / 进度条轨道）。
 *
 * 13c 在卡片分割线、词书选中态、进度条轨道上会用到这些。颜色不通过 M3 ColorScheme
 * 表达，避免强行往不相关的 slot（如 outlineVariant）里塞，污染语义。
 */
data class WordDrillColors(
    val chipBg: Color,
    val separator: Color,
    val separatorStrong: Color,
    val progressTrack: Color,
)

val LightWordDrillColors = WordDrillColors(
    chipBg = ChipBgLight,
    separator = SeparatorLight,
    separatorStrong = SeparatorStrongLight,
    progressTrack = ProgressTrackLight,
)

val DarkWordDrillColors = WordDrillColors(
    chipBg = ChipBgDark,
    separator = SeparatorDark,
    separatorStrong = SeparatorStrongDark,
    progressTrack = ProgressTrackDark,
)

val LocalWordDrillColors = staticCompositionLocalOf<WordDrillColors> {
    error("WordDrillColors not provided. Wrap content in WordDrillTheme.")
}

/** 取当前主题的扩展语义 token（chipBg / separator / progressTrack）。 */
val MaterialTheme.wordDrillColors: WordDrillColors
    @Composable
    @ReadOnlyComposable
    get() = LocalWordDrillColors.current

// ── Material3 ColorScheme 映射 ──────────────────────────────
// 把纯黑白灰 token 映射到 M3 slot，让 Scaffold/TopAppBar/ListItem/Button 等
// M3 组件无需改代码就拿到正确颜色。映射原则：M3 primary/onPrimary 等组件色
// 在纯黑白系统里 = textPrimary 反色块（按钮、选中态、进度条填充都用它）。

val LightColors = lightColorScheme(
    primary = TextPrimaryLight,
    onPrimary = BgLight,
    primaryContainer = ChipBgLight,
    onPrimaryContainer = TextPrimaryLight,
    secondary = TextSecondaryLight,
    onSecondary = BgLight,
    secondaryContainer = ChipBgLight,
    onSecondaryContainer = TextPrimaryLight,
    tertiary = TextTertiaryLight,
    onTertiary = BgLight,
    tertiaryContainer = ChipBgLight,
    onTertiaryContainer = TextPrimaryLight,
    background = BgLight,
    onBackground = TextPrimaryLight,
    surface = SurfaceLight,
    onSurface = TextPrimaryLight,
    surfaceVariant = SurfaceLight,
    onSurfaceVariant = TextSecondaryLight,
    surfaceTint = TextPrimaryLight,
    inverseSurface = TextPrimaryLight,
    inverseOnSurface = BgLight,
    outline = SeparatorStrongLight,
    outlineVariant = SeparatorLight,
    scrim = Color.Black,
    // Surface container hierarchy — pure grayscale to override M3 default tonal palette.
    // NavigationBar / Surface(tonalElevation>0) read surfaceContainer; without these they
    // fall back to a default M3 tint (purple-ish), breaking the monochrome spec.
    surfaceBright = SurfaceLight,
    surfaceDim = BgLight,
    surfaceContainer = SurfaceLight,
    surfaceContainerHigh = ChipBgLight,
    surfaceContainerHighest = ChipBgLight,
    surfaceContainerLow = BgLight,
    surfaceContainerLowest = SurfaceLight,
    error = Color(0xFFB3261E), // 错误色保留语义红（出错提示），不在黑白灰系统内
    onError = Color.White,
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
)

val DarkColors = darkColorScheme(
    primary = TextPrimaryDark,
    onPrimary = BgDark,
    primaryContainer = ChipBgDark,
    onPrimaryContainer = TextPrimaryDark,
    secondary = TextSecondaryDark,
    onSecondary = BgDark,
    secondaryContainer = ChipBgDark,
    onSecondaryContainer = TextPrimaryDark,
    tertiary = TextTertiaryDark,
    onTertiary = BgDark,
    tertiaryContainer = ChipBgDark,
    onTertiaryContainer = TextPrimaryDark,
    background = BgDark,
    onBackground = TextPrimaryDark,
    surface = SurfaceDark,
    onSurface = TextPrimaryDark,
    surfaceVariant = SurfaceDark,
    onSurfaceVariant = TextSecondaryDark,
    surfaceTint = TextPrimaryDark,
    inverseSurface = BgDark,
    inverseOnSurface = TextPrimaryDark,
    outline = SeparatorStrongDark,
    outlineVariant = SeparatorDark,
    scrim = Color.Black,
    // Surface container hierarchy — pure grayscale to override M3 default tonal palette.
    surfaceBright = SurfaceElevatedDark,
    surfaceDim = BgDark,
    surfaceContainer = SurfaceDark,
    surfaceContainerHigh = SurfaceElevatedDark,
    surfaceContainerHighest = SurfaceElevatedDark,
    surfaceContainerLow = BgDark,
    surfaceContainerLowest = BgDark,
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),
)
