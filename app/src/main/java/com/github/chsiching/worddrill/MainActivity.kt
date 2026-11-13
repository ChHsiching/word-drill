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
        // Ticket #24: the SplashScreen must be installed before super.onCreate (required by the compat library).
        // The splash theme sets the system icon to a transparent drawable; after startup the system switches back to Theme.WordDrill.
        //
        // setKeepOnScreenCondition: during cold start, Compose first-frame composition + the WordDrillRoot subtree
        // (Hilt/Room/NavHost) initialization takes ~1-2s; during that period the system splash keeps showing a solid background color,
        // avoiding a jarring blank gap between the splash disappearing and the animation starting.
        // Once the animation's first frame is drawn (animReady set to true), the system splash hands off to the overlay.
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
            // renderedTheme tracks themeMode (the two sync up after DataStore's async refresh).
            // During the reveal animation the DataStore write completes, themeMode changes, renderedTheme follows, and the underlying layer switches.
            // The snapshot overlay covers the old theme, and the animation reveals the new theme underneath.
            var renderedTheme by remember { mutableStateOf(themeMode) }

            // When themeMode changes, sync it to renderedTheme (themeMode updates after the DataStore write; this keeps up)
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
                    // Ticket #24 splash animation overlay: covers the main content during the animation,
                    // and reveals the layer underneath once finished (the theme has stabilized).
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
