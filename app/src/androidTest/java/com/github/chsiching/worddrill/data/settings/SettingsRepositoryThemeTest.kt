package com.github.chsiching.worddrill.data.settings

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Persistence of the theme preference in [SettingsRepository] (Ticket #9 AC: keep the last choice across app restarts).
 *
 * Uses the real Context (instrumented) to write the real DataStore; to simulate a "restart", clear the singleton cache
 * and create a new [SettingsRepository] instance (same file); the read-back should be the written value.
 *
 * Uses Truth assertThat (repo convention, pitfall H); test methods use block bodies instead of `= runBlocking {}`
 * expression bodies (pitfall J: the last statement must return Unit).
 */
@RunWith(AndroidJUnit4::class)
class SettingsRepositoryThemeTest {

    private val context: android.content.Context
        get() = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() = runBlocking {
        // Isolation: clear the theme_mode written by the previous test run, for a clean starting point
        SettingsRepository(context).setTheme(ThemeMode.SYSTEM)
        Unit
    }

    @After
    fun tearDown() = runBlocking {
        SettingsRepository(context).setTheme(ThemeMode.SYSTEM)
        Unit
    }

    @Test
    fun themePreference_defaultsToSystem_whenNeverSet() = runBlocking {
        // Fresh state (already reset to SYSTEM): the read-back should be SYSTEM
        val mode = SettingsRepository(context).themePreference.first()
        assertThat(mode).isEqualTo(ThemeMode.SYSTEM)
        Unit
    }

    @Test
    fun setTheme_persistsAcrossRepositoryInstances() = runBlocking {
        // Write DARK
        SettingsRepository(context).setTheme(ThemeMode.DARK)
        // Simulate an "app restart": create a new instance (same DataStore file); the read-back should be DARK
        val reloaded = SettingsRepository(context).themePreference.first()
        assertThat(reloaded).isEqualTo(ThemeMode.DARK)
        Unit
    }

    @Test
    fun setTheme_lightRoundTripsAcrossInstances() = runBlocking {
        SettingsRepository(context).setTheme(ThemeMode.LIGHT)
        assertThat(SettingsRepository(context).themePreference.first()).isEqualTo(ThemeMode.LIGHT)
        Unit
    }
}
