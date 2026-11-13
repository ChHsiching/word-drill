package com.github.chsiching.worddrill.data.settings

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Pure-function JVM tests for [NavStyle.fromStorageName] (Ticket #16).
 *
 * Same as [ThemeMode]: unknown or corrupt values fall back safely to the default
 * [NavStyle.PILL] instead of throwing.
 */
class NavStyleTest {

    @Test
    fun fromStorageName_returnsPill_whenNameIsPill() {
        assertThat(NavStyle.fromStorageName("PILL")).isEqualTo(NavStyle.PILL)
    }

    @Test
    fun fromStorageName_returnsBar_whenNameIsBar() {
        assertThat(NavStyle.fromStorageName("BAR")).isEqualTo(NavStyle.BAR)
    }

    @Test
    fun fromStorageName_defaultsToPill_whenNull() {
        assertThat(NavStyle.fromStorageName(null)).isEqualTo(NavStyle.PILL)
    }

    @Test
    fun fromStorageName_defaultsToPill_whenUnknown() {
        assertThat(NavStyle.fromStorageName("garbage")).isEqualTo(NavStyle.PILL)
        assertThat(NavStyle.fromStorageName("")).isEqualTo(NavStyle.PILL)
        assertThat(NavStyle.fromStorageName("pill")).isEqualTo(NavStyle.PILL) // case-sensitive
    }
}
