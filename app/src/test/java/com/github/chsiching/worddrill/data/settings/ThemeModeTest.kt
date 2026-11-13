package com.github.chsiching.worddrill.data.settings

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Pure-function decoding for [ThemeMode.fromStorageName] (Ticket #9).
 *
 * Covers: round trips for legal values, null (key missing), and corrupt or unknown values
 * falling back to SYSTEM (so the Flow does not crash).
 * Uses Truth assertThat (repo convention, pitfall H: do not use kotlin.assert).
 */
class ThemeModeTest {

    @Test
    fun fromStorageName_recognizesAllModes() {
        assertThat(ThemeMode.fromStorageName("LIGHT")).isEqualTo(ThemeMode.LIGHT)
        assertThat(ThemeMode.fromStorageName("DARK")).isEqualTo(ThemeMode.DARK)
        assertThat(ThemeMode.fromStorageName("SYSTEM")).isEqualTo(ThemeMode.SYSTEM)
    }

    @Test
    fun fromStorageName_defaultsToSystem_whenKeyMissing() {
        // DataStore returns null when the key is missing: fall back to SYSTEM (the default preference).
        assertThat(ThemeMode.fromStorageName(null)).isEqualTo(ThemeMode.SYSTEM)
    }

    @Test
    fun fromStorageName_fallsBackToSystem_onCorruptValue() {
        // Corrupt value / a new enum name from a future version / residue from an old version: no exception, fall back to SYSTEM.
        assertThat(ThemeMode.fromStorageName("DARK_MODE")).isEqualTo(ThemeMode.SYSTEM)
        assertThat(ThemeMode.fromStorageName("light")).isEqualTo(ThemeMode.SYSTEM) // case-sensitive
        assertThat(ThemeMode.fromStorageName("")).isEqualTo(ThemeMode.SYSTEM)
    }

    @Test
    fun name_isRoundTrippable_throughFromStorageName() {
        // Persistence uses name() and reads back through fromStorageName: every enum round trips without losing information.
        for (mode in ThemeMode.entries) {
            assertThat(ThemeMode.fromStorageName(mode.name)).isEqualTo(mode)
        }
    }
}
