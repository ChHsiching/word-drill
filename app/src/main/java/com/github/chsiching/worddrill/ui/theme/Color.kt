package com.github.chsiching.worddrill.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

val BgLight = Color(0xFFFAFAFA)
val SurfaceLight = Color(0xFFFFFFFF)
val SurfaceElevatedLight = Color(0xFFFFFFFF)
val TextPrimaryLight = Color(0xFF1A1A1A)
val TextSecondaryLight = Color(0xFF86868B)
val TextTertiaryLight = Color(0xFFAEAEB2)
val SeparatorLight = Color(0x0F000000)
val SeparatorStrongLight = Color(0x1F000000)
val ProgressTrackLight = Color(0xFFE8E8ED)
val ChipBgLight = Color(0xFFF5F5F7)

val DestructiveFg = Color(0xFFFF453A)
val DestructiveBg = Color(0x1FFF453A)

val BgDark = Color(0xFF000000)
val SurfaceDark = Color(0xFF1C1C1E)
val SurfaceElevatedDark = Color(0xFF2C2C2E)
val TextPrimaryDark = Color(0xFFFFFFFF)
val TextSecondaryDark = Color(0xFF98989F)
val TextTertiaryDark = Color(0xFF636366)
val SeparatorDark = Color(0x14FFFFFF)
val SeparatorStrongDark = Color(0x26FFFFFF)
val ProgressTrackDark = Color(0xFF38383A)
val ChipBgDark = Color(0xFF2C2C2E)

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

val MaterialTheme.wordDrillColors: WordDrillColors
    @Composable
    @ReadOnlyComposable
    get() = LocalWordDrillColors.current

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
    // Without these overrides, NavigationBar / Surface(tonalElevation>0) fall back to M3's
    // default purple tonal palette.
    surfaceBright = SurfaceLight,
    surfaceDim = BgLight,
    surfaceContainer = SurfaceLight,
    surfaceContainerHigh = ChipBgLight,
    surfaceContainerHighest = ChipBgLight,
    surfaceContainerLow = BgLight,
    surfaceContainerLowest = SurfaceLight,
    error = Color(0xFFB3261E),
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
    // See LightColors: overrides keep M3's default tonal palette out.
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
