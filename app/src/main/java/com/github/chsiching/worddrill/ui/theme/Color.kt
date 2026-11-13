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
// Ticket #15 pure black/white/gray color tokens (source: :root in designs/worddrill-ui/index.html)
// The mapping to the Material3 ColorScheme lives in Theme.kt; semantic tokens beyond the
// M3 slots (chipBg / separator / progressTrack) are exposed via [WordDrillColors].
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

// ── E-style dialog destructive button (Ticket #25): light red fill + red text ──
// The design spec dialog-5-full.html uses err = '#FF453A', light fill = rgba(255,69,58,0.12),
// same color in light and dark mode (iOS system red). alpha 0.12 becomes 0x1F.
val DestructiveFg = Color(0xFFFF453A)
val DestructiveBg = Color(0x1FFF453A)

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
 * Semantic tokens: design-spec-only colors beyond Material3 [ColorScheme] (chip background /
 * separators / progress bar track).
 *
 * 13c uses these for card dividers, the selected book state, and progress bar tracks. These
 * colors are not expressed through the M3 ColorScheme, to avoid forcing them into unrelated
 * slots (like outlineVariant) and polluting the semantics.
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

/** Extended semantic tokens for the current theme (chipBg / separator / progressTrack). */
val MaterialTheme.wordDrillColors: WordDrillColors
    @Composable
    @ReadOnlyComposable
    get() = LocalWordDrillColors.current

// ── Material3 ColorScheme mapping ───────────────────────────
// Maps the pure black/white/gray tokens to M3 slots so that M3 components such as
// Scaffold/TopAppBar/ListItem/Button get the right colors without code changes.
// Mapping rule: in a pure black-and-white system, M3 component colors such as
// primary/onPrimary are textPrimary paired with its inverse (buttons, selected states,
// and progress bar fills all use it).

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
    error = Color(0xFFB3261E), // error keeps a semantic red (error hints), outside the black/white/gray system
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
