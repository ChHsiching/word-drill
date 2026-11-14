package com.github.chsiching.worddrill.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.github.chsiching.worddrill.data.settings.ThemeMode

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
