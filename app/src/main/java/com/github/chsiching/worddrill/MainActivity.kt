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
        // The SplashScreen compat library requires installSplashScreen() before super.onCreate.
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
            var renderedTheme by remember { mutableStateOf(themeMode) }

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
