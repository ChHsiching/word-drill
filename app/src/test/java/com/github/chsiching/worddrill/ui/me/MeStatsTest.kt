package com.github.chsiching.worddrill.ui.me

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class MeStatsTest {

    @Test
    fun progressPercent_normalRatio() {
        assertThat(MeStats.progressPercent(brushed = 1, total = 4)).isEqualTo(25)
        assertThat(MeStats.progressPercent(brushed = 3, total = 4)).isEqualTo(75)
    }

    @Test
    fun progressPercent_full() {
        assertThat(MeStats.progressPercent(brushed = 10, total = 10)).isEqualTo(100)
    }

    @Test
    fun progressPercent_zeroDenominator_returnsZero_notCrash() {
        assertThat(MeStats.progressPercent(brushed = 0, total = 0)).isEqualTo(0)
        assertThat(MeStats.progressPercent(brushed = 3, total = 0)).isEqualTo(0)
    }

    @Test
    fun progressPercent_truncatesNotRounds() {
        assertThat(MeStats.progressPercent(brushed = 1, total = 3)).isEqualTo(33)
    }

    @Test
    fun progressPercent_defensiveAgainstOverflow() {
        assertThat(MeStats.progressPercent(brushed = 1_000_000, total = 4_000_000)).isEqualTo(25)
    }

    @Test
    fun startOfTodayMillis_middayReturnsSameDayMidnight() {
        val now = Instant.parse("2026-07-23T13:45:30Z")
        val zone = ZoneId.of("UTC")
        val start = MeStats.startOfTodayMillis(now = now, zone = zone)
        assertThat(start).isEqualTo(Instant.parse("2026-07-23T00:00:00Z").toEpochMilli())
    }

    @Test
    fun startOfTodayMillis_justBeforeMidnightStillSameDay() {
        val now = Instant.parse("2026-07-23T23:59:59Z")
        val zone = ZoneId.of("UTC")
        val start = MeStats.startOfTodayMillis(now = now, zone = zone)
        assertThat(start).isEqualTo(Instant.parse("2026-07-23T00:00:00Z").toEpochMilli())
    }

    @Test
    fun startOfTodayMillis_justAfterMidnightRollsToNewDay() {
        val now = Instant.parse("2026-07-24T00:00:01Z")
        val zone = ZoneId.of("UTC")
        val start = MeStats.startOfTodayMillis(now = now, zone = zone)
        assertThat(start).isEqualTo(Instant.parse("2026-07-24T00:00:00Z").toEpochMilli())
    }

    @Test
    fun startOfTodayMillis_respectsTimeZone() {
        val now = Instant.parse("2026-07-23T10:00:00Z")
        val utcStart = MeStats.startOfTodayMillis(now = now, zone = ZoneId.of("UTC"))
        val cstStart = MeStats.startOfTodayMillis(now = now, zone = ZoneId.of("+08:00"))
        assertThat(cstStart).isEqualTo(utcStart - 8 * 3600_000L)
    }

    @Test
    fun startOfTodayMillis_utcMidnightCrossesDayInPositiveZone() {
        val before = MeStats.startOfTodayMillis(
            now = Instant.parse("2026-07-23T15:59:59Z"),
            zone = ZoneId.of("+08:00"),
        )
        val after = MeStats.startOfTodayMillis(
            now = Instant.parse("2026-07-23T16:00:01Z"),
            zone = ZoneId.of("+08:00"),
        )
        assertThat(after).isGreaterThan(before)
    }
}
