package com.github.chsiching.worddrill

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.chsiching.worddrill.data.settings.SettingsRepository
import com.github.chsiching.worddrill.data.settings.ThemeMode
import com.github.chsiching.worddrill.ui.navigation.WordDrillRoot
import com.github.chsiching.worddrill.ui.theme.BgDark
import com.github.chsiching.worddrill.ui.theme.BgLight
import com.github.chsiching.worddrill.ui.theme.ThemeRevealBox
import com.github.chsiching.worddrill.ui.theme.WordDrillTheme
import dagger.hilt.android.AndroidEntryPoint
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * 单 Activity 入口。承载 Compose 根 UI 与底部导航。
 * @AndroidEntryPoint 让 Hilt 能向此 Activity 及其挂载的 Composable 注入依赖。
 *
 * 注意：本项目未启用 Hilt Gradle Plugin（它要求 AGP 9，与规格的 AGP 8.13.x 冲突）。
 * 因此 @AndroidEntryPoint 需显式指定 base class，且 extend Hilt 生成的 Hilt_<类名>。
 *
 * Ticket #9：订阅 [SettingsRepository.themePreference] 并把当前 [ThemeMode] 传给
 * [WordDrillTheme]，用户在「我的」Tab 切换主题后全局配色立即生效。
 *
 * 审核反馈 5：主题切换用 circular reveal 扩散效果（ThemeRevealBox）。
 * - 用户点 MeScreen 右上角的日/月 icon → LocalThemeRevealTrigger 触发
 * - reveal 圆以 icon 为圆心扩展，中点时切 ColorScheme（瞬间切，被圆覆盖看不见接缝）
 * - targetBg：切到深色时圆是黑色（BgDark），切到浅色时圆是白色（BgLight）
 */
@AndroidEntryPoint(ComponentActivity::class)
class MainActivity : Hilt_MainActivity() {

    @Inject
    lateinit var settings: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val themeState = settings.themePreference.stateIn(
            scope = lifecycleScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ThemeMode.SYSTEM,
        )
        setContent {
            val themeMode by themeState.collectAsStateWithLifecycle()
            var renderedTheme by remember { mutableStateOf(themeMode) }

            WordDrillTheme(themeMode = renderedTheme) {
                // 目标背景色：当前是深色 → 切浅色 → 圆用白(BgLight)；当前浅 → 切深 → 圆用黑(BgDark)
                val currentlyDark = renderedTheme == ThemeMode.DARK ||
                    (renderedTheme == ThemeMode.SYSTEM && androidx.compose.foundation.isSystemInDarkTheme())
                val targetBg = if (currentlyDark) BgLight else BgDark

                ThemeRevealBox(
                    targetBgColor = targetBg,
                    onApplyTheme = { targetIsDark ->
                        // 圆到 50% 时真切：本地立即切（不等 DataStore）+ 异步写库
                        renderedTheme = if (targetIsDark) ThemeMode.DARK else ThemeMode.LIGHT
                    },
                ) {
                    WordDrillRoot()
                }
            }
        }
    }
}

