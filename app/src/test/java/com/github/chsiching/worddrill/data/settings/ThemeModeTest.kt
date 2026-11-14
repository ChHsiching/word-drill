package com.github.chsiching.worddrill.data.settings

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ThemeModeTest {

    @Test
    fun fromStorageName_recognizesAllModes() {
        assertThat(ThemeMode.fromStorageName("LIGHT")).isEqualTo(ThemeMode.LIGHT)
        assertThat(ThemeMode.fromStorageName("DARK")).isEqualTo(ThemeMode.DARK)
        assertThat(ThemeMode.fromStorageName("SYSTEM")).isEqualTo(ThemeMode.SYSTEM)
    }

    @Test
    fun fromStorageName_defaultsToSystem_whenKeyMissing() {
        assertThat(ThemeMode.fromStorageName(null)).isEqualTo(ThemeMode.SYSTEM)
    }

    @Test
    fun fromStorageName_fallsBackToSystem_onCorruptValue() {
        assertThat(ThemeMode.fromStorageName("DARK_MODE")).isEqualTo(ThemeMode.SYSTEM)
        assertThat(ThemeMode.fromStorageName("light")).isEqualTo(ThemeMode.SYSTEM)
        assertThat(ThemeMode.fromStorageName("")).isEqualTo(ThemeMode.SYSTEM)
    }

    @Test
    fun name_isRoundTrippable_throughFromStorageName() {
        for (mode in ThemeMode.entries) {
            assertThat(ThemeMode.fromStorageName(mode.name)).isEqualTo(mode)
        }
    }
}
