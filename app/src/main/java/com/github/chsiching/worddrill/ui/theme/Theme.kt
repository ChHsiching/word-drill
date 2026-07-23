package com.github.chsiching.worddrill.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.github.chsiching.worddrill.data.settings.ThemeMode

private val LightColors = lightColorScheme(
    primary = PrimaryLight,
    onPrimary = OnPrimaryLight,
    surface = SurfaceLight,
    onSurface = OnSurfaceLight,
)

private val DarkColors = darkColorScheme(
    primary = PrimaryDark,
    onPrimary = OnPrimaryDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
)

/**
 * 应用主题（Ticket #9）：根据 [themeMode] 选择 Material3 配色方案。
 *
 * - [ThemeMode.LIGHT] / [ThemeMode.DARK]：强制浅色 / 深色，覆盖系统设置。
 * - [ThemeMode.SYSTEM]：跟随系统深浅色（[isSystemInDarkTheme]）。
 *
 * 支持 Android 12+ 的动态取色（Material You）。调用方（[com.github.chsiching.worddrill.MainActivity]）
 * 订阅 [com.github.chsiching.worddrill.data.settings.SettingsRepository.themePreference]
 * 并把结果传入此处；切换后全局配色立即生效。
 */
@Composable
fun WordDrillTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content,
    )
}
