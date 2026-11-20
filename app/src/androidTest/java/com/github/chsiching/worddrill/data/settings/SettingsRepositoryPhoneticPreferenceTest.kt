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
class SettingsRepositoryPhoneticPreferenceTest {

    private val context: android.content.Context
        get() = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() = runBlocking {
        SettingsRepository(context).setPhoneticPreference(PhoneticPreference.US)
        Unit
    }

    @After
    fun tearDown() = runBlocking {
        SettingsRepository(context).setPhoneticPreference(PhoneticPreference.US)
        Unit
    }

    @Test
    fun phoneticPreference_defaultsToUs_whenNeverSet() = runBlocking {
        assertThat(SettingsRepository(context).phoneticPreference.first())
            .isEqualTo(PhoneticPreference.US)
        Unit
    }

    @Test
    fun setPhoneticPreference_persistsAcrossRepositoryInstances() = runBlocking {
        SettingsRepository(context).setPhoneticPreference(PhoneticPreference.BOTH)
        assertThat(SettingsRepository(context).phoneticPreference.first())
            .isEqualTo(PhoneticPreference.BOTH)

        SettingsRepository(context).setPhoneticPreference(PhoneticPreference.UK)
        assertThat(SettingsRepository(context).phoneticPreference.first())
            .isEqualTo(PhoneticPreference.UK)
        Unit
    }
}
