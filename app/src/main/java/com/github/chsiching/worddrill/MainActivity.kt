package com.github.chsiching.worddrill

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.chsiching.worddrill.data.settings.SettingsRepository
import com.github.chsiching.worddrill.data.settings.ThemeMode
import com.github.chsiching.worddrill.ui.navigation.WordDrillRoot
import com.github.chsiching.worddrill.ui.theme.ThemeRevealContent
import com.github.chsiching.worddrill.ui.theme.WordDrillTheme
import dagger.hilt.android.AndroidEntryPoint
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

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
            // renderedTheme 跟踪 themeMode（DataStore 异步刷新后两者同步）。
            // reveal 动画期间 DataStore 写完 → themeMode 变 → renderedTheme 跟着变 → 底层切。
            // 截图 overlay 盖住旧主题，动画揭示新主题底层。
            var renderedTheme by remember { mutableStateOf(themeMode) }

            // themeMode 变了就同步到 renderedTheme（DataStore 写完后 themeMode 更新，这里跟上）
            androidx.compose.runtime.LaunchedEffect(themeMode) {
                renderedTheme = themeMode
            }

            WordDrillTheme(themeMode = renderedTheme) {
                val darkBg = when (renderedTheme) {
                    ThemeMode.LIGHT -> false
                    ThemeMode.DARK -> true
                    ThemeMode.SYSTEM -> isSystemInDarkTheme()
                }
                SideEffect {
                    WindowCompat.getInsetsController(window, window.decorView)
                        .isAppearanceLightStatusBars = !darkBg
                }

                ThemeRevealContent(
                    onThemeApplied = {},
                ) {
                    WordDrillRoot()
                }
            }
        }
    }
}
