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
class SettingsRepositoryDwellTest {

    private val context: android.content.Context
        get() = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() = runBlocking {
        SettingsRepository(context).setDwellInterval(DwellInterval.DEFAULT_SECONDS)
        Unit
    }

    @After
    fun tearDown() = runBlocking {
        SettingsRepository(context).setDwellInterval(DwellInterval.DEFAULT_SECONDS)
        Unit
    }

    @Test
    fun dwellInterval_readsFiveSeconds_afterResetToDefault() = runBlocking {
        val value = SettingsRepository(context).dwellInterval.first()
        assertThat(value).isEqualTo(5f)
        Unit
    }

    @Test
    fun setDwellInterval_persistsAcrossRepositoryInstances() = runBlocking {
        SettingsRepository(context).setDwellInterval(12.5f)
        val reloaded = SettingsRepository(context).dwellInterval.first()
        assertThat(reloaded).isEqualTo(12.5f)
        Unit
    }

    @Test
    fun setDwellInterval_normalizesOutOfRangeWrites() = runBlocking {
        SettingsRepository(context).setDwellInterval(0.2f)
        assertThat(SettingsRepository(context).dwellInterval.first()).isEqualTo(1f)
        SettingsRepository(context).setDwellInterval(47f)
        assertThat(SettingsRepository(context).dwellInterval.first()).isEqualTo(30f)
        SettingsRepository(context).setDwellInterval(5.3f)
        assertThat(SettingsRepository(context).dwellInterval.first()).isEqualTo(5.5f)
        Unit
    }
}
