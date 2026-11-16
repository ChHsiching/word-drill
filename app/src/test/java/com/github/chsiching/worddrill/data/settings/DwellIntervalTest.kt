package com.github.chsiching.worddrill.data.settings

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DwellIntervalTest {

    @Test
    fun default_isFiveSeconds() {
        assertThat(DwellInterval.DEFAULT_SECONDS).isEqualTo(5f)
    }

    @Test
    fun normalize_keepsValue_whenAlreadyOnStep() {
        assertThat(DwellInterval.normalize(1f)).isEqualTo(1f)
        assertThat(DwellInterval.normalize(5f)).isEqualTo(5f)
        assertThat(DwellInterval.normalize(12.5f)).isEqualTo(12.5f)
        assertThat(DwellInterval.normalize(30f)).isEqualTo(30f)
    }

    @Test
    fun normalize_clampsBelowRange_toOneSecond() {
        assertThat(DwellInterval.normalize(0.5f)).isEqualTo(1f)
        assertThat(DwellInterval.normalize(0f)).isEqualTo(1f)
        assertThat(DwellInterval.normalize(-3f)).isEqualTo(1f)
    }

    @Test
    fun normalize_clampsAboveRange_toThirtySeconds() {
        assertThat(DwellInterval.normalize(30.5f)).isEqualTo(30f)
        assertThat(DwellInterval.normalize(99f)).isEqualTo(30f)
    }

    @Test
    fun normalize_snapsToNearestHalfSecond() {
        assertThat(DwellInterval.normalize(5.2f)).isEqualTo(5f)
        assertThat(DwellInterval.normalize(5.3f)).isEqualTo(5.5f)
        assertThat(DwellInterval.normalize(7.74f)).isEqualTo(7.5f)
        assertThat(DwellInterval.normalize(7.76f)).isEqualTo(8f)
    }

    @Test
    fun normalize_snapsThenClamps_atBothBounds() {
        assertThat(DwellInterval.normalize(1.2f)).isEqualTo(1f)
        assertThat(DwellInterval.normalize(1.3f)).isEqualTo(1.5f)
        assertThat(DwellInterval.normalize(29.9f)).isEqualTo(30f)
        assertThat(DwellInterval.normalize(0.7f)).isEqualTo(1f)
    }
}
