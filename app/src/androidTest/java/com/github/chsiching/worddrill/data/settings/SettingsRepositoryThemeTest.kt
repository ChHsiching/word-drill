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

@RunWith(AndroidJUnit4::class)
class SettingsRepositoryThemeTest {

    private val context: android.content.Context
        get() = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() = runBlocking {
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
        val mode = SettingsRepository(context).themePreference.first()
        assertThat(mode).isEqualTo(ThemeMode.SYSTEM)
        Unit
    }

    @Test
    fun setTheme_persistsAcrossRepositoryInstances() = runBlocking {
        SettingsRepository(context).setTheme(ThemeMode.DARK)
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
