package com.github.chsiching.worddrill

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.chsiching.worddrill.data.settings.SettingsRepository
import com.github.chsiching.worddrill.data.settings.ThemeMode
import com.github.chsiching.worddrill.ui.navigation.WordDrillRoot
import com.github.chsiching.worddrill.ui.splash.AnimatedSplashOverlay
import com.github.chsiching.worddrill.ui.theme.ThemeRevealContent
import com.github.chsiching.worddrill.ui.theme.WordDrillTheme
import dagger.hilt.android.AndroidEntryPoint
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

@AndroidEntryPoint(ComponentActivity::class)
class MainActivity : Hilt_MainActivity() {

    @Inject
    lateinit var settings: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        // Ticket #24：必须在 super.onCreate 前安装 SplashScreen（compat 库要求）。
        // splash 主题把系统 icon 设为透明 drawable，启动后系统切回 Theme.WordDrill。
        //
        // setKeepOnScreenCondition：冷启动期间 Compose 首帧组合 + WordDrillRoot 子树
        // （Hilt/Room/NavHost）初始化需要 ~1-2s，此期间系统 splash 持续显示纯背景色，
        // 避免出现「splash 消失 → overlay 空白 → 动画才开始」的割裂空白期。
        // 动画首帧绘制后（animReady 置 true）系统 splash 交棒给 overlay。
        val animReady = AtomicBoolean(false)
        val splashScreen = installSplashScreen()
        splashScreen.setKeepOnScreenCondition { !animReady.get() }
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
                    // Ticket #24 启动动画 overlay：动画期间盖住主内容，
                    // 完成后揭示底层（主题已稳定）。
                    var splashDone by remember { mutableStateOf(false) }
                    Box(Modifier.fillMaxSize()) {
                        WordDrillRoot()
                        if (!splashDone) {
                            AnimatedSplashOverlay(
                                darkTheme = darkBg,
                                onReady = { animReady.set(true) },
                                onFinished = { splashDone = true },
                            )
                        }
                    }
                }
            }
        }
    }
}
