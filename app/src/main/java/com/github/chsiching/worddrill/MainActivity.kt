package com.github.chsiching.worddrill

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.chsiching.worddrill.data.settings.SettingsRepository
import com.github.chsiching.worddrill.data.settings.ThemeMode
import com.github.chsiching.worddrill.ui.navigation.WordDrillRoot
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
 * Ticket #9：在此订阅 [SettingsRepository.themePreference] 并把当前 [ThemeMode] 传给
 * [WordDrillTheme]，用户在「我的」Tab 切换主题后全局配色立即生效。
 */
@AndroidEntryPoint(ComponentActivity::class)
class MainActivity : Hilt_MainActivity() {

    @Inject
    lateinit var settings: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // WhileSubscribed：仅在 Activity 可见时订阅，配置变更后复用已有值
        val themeState = settings.themePreference.stateIn(
            scope = lifecycleScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ThemeMode.SYSTEM,
        )
        setContent {
            val themeMode by themeState.collectAsStateWithLifecycle()
            WordDrillTheme(themeMode = themeMode) {
                WordDrillRoot()
            }
        }
    }
}
