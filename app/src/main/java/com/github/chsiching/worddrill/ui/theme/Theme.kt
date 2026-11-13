package com.github.chsiching.worddrill.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.github.chsiching.worddrill.data.settings.ThemeMode

/**
 * App theme (Ticket #9 + #15).
 *
 * - [themeMode]: [ThemeMode.LIGHT] / [ThemeMode.DARK] / [ThemeMode.SYSTEM] (follows the system).
 * - **Dynamic color is disabled** (Material You): the design spec requires pure black/white/gray
 *   and must not be tinted by the system wallpaper.
 * - Color tokens live in [Color.kt]; extended semantic tokens are provided via [LocalWordDrillColors].
 * - The typography spec lives in [Type.kt].
 *
 * The caller ([com.github.chsiching.worddrill.MainActivity]) subscribes to
 * [com.github.chsiching.worddrill.data.settings.SettingsRepository.themePreference]
 * and passes the result here; after a switch the global palette takes effect immediately.
 */
@Composable
fun WordDrillTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val colorScheme = if (darkTheme) DarkColors else LightColors
    val extended = if (darkTheme) DarkWordDrillColors else LightWordDrillColors
    val typography = provideWordDrillTypography()
    CompositionLocalProvider(
        LocalWordDrillColors provides extended,
        LocalWordDrillTypography provides typography,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            content = content,
        )
    }
}
