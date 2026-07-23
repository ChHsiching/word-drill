package com.github.chsiching.worddrill.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.github.chsiching.worddrill.data.settings.ThemeMode

/**
 * 应用主题（Ticket #9 + #15）。
 *
 * - [themeMode]：[ThemeMode.LIGHT] / [ThemeMode.DARK] / [ThemeMode.SYSTEM]（跟随系统）。
 * - **关闭动态取色**（Material You）：设计稿要求纯黑白灰，不能被系统壁纸染色。
 * - 配色 token 见 [Color.kt]；扩展语义 token 通过 [LocalWordDrillColors] 提供。
 * - 字体规范见 [Type.kt]。
 *
 * 调用方（[com.github.chsiching.worddrill.MainActivity]）订阅
 * [com.github.chsiching.worddrill.data.settings.SettingsRepository.themePreference]
 * 并把结果传入此处；切换后全局配色立即生效。
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
