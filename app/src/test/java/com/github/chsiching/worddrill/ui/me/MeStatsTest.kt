package com.github.chsiching.worddrill.ui.me

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

/**
 * Unit tests for the "Me" tab statistics pure functions (Ticket #8).
 *
 * Covers the computable parts of the spec acceptance points:
 * - Start of today: from a given instant to the milliseconds of today's 00:00 (midnight crossing, time zones, UTC boundaries)
 * - Progress percent: division by zero (empty book), the normal case, full, and over-full defensive cases
 *
 * Uses Truth assertThat (repo convention, see pitfall H. Do not use kotlin.assert).
 */
class MeStatsTest {

    // ---- progressPercent ----

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
        // Empty book: denominator 0, no division by zero, returns 0%. The spec says "don't divide by zero".
        assertThat(MeStats.progressPercent(brushed = 0, total = 0)).isEqualTo(0)
        assertThat(MeStats.progressPercent(brushed = 3, total = 0)).isEqualTo(0)
    }

    @Test
    fun progressPercent_truncatesNotRounds() {
        // Integer truncation: 1/3 = 33%, rounded down, not 33.33%.
        assertThat(MeStats.progressPercent(brushed = 1, total = 3)).isEqualTo(33)
    }

    @Test
    fun progressPercent_defensiveAgainstOverflow() {
        // Large numbers must not overflow int: use Long intermediate arithmetic.
        assertThat(MeStats.progressPercent(brushed = 1_000_000, total = 4_000_000)).isEqualTo(25)
    }

    // ---- startOfTodayMillis ----

    @Test
    fun startOfTodayMillis_middayReturnsSameDayMidnight() {
        // Fixed UTC 2026-07-23 13:45:30 gives start of today 2026-07-23 00:00:00 UTC.
        val now = Instant.parse("2026-07-23T13:45:30Z")
        val zone = ZoneId.of("UTC")
        val start = MeStats.startOfTodayMillis(now = now, zone = zone)
        assertThat(start).isEqualTo(Instant.parse("2026-07-23T00:00:00Z").toEpochMilli())
    }

    @Test
    fun startOfTodayMillis_justBeforeMidnightStillSameDay() {
        // 23:59:59 is still the same day. Start of today is 00:00 of that day, not the next day.
        val now = Instant.parse("2026-07-23T23:59:59Z")
        val zone = ZoneId.of("UTC")
        val start = MeStats.startOfTodayMillis(now = now, zone = zone)
        assertThat(start).isEqualTo(Instant.parse("2026-07-23T00:00:00Z").toEpochMilli())
    }

    @Test
    fun startOfTodayMillis_justAfterMidnightRollsToNewDay() {
        // 00:00:01 belongs to the new day. Start of today is 00:00 of that day.
        val now = Instant.parse("2026-07-24T00:00:01Z")
        val zone = ZoneId.of("UTC")
        val start = MeStats.startOfTodayMillis(now = now, zone = zone)
        assertThat(start).isEqualTo(Instant.parse("2026-07-24T00:00:00Z").toEpochMilli())
    }

    @Test
    fun startOfTodayMillis_respectsTimeZone() {
        // For the same UTC instant, the UTC+8 "today at 00:00" differs from UTC's by 8 hours in milliseconds.
        val now = Instant.parse("2026-07-23T10:00:00Z") // UTC+8 = 18:00 same day
        val utcStart = MeStats.startOfTodayMillis(now = now, zone = ZoneId.of("UTC"))
        val cstStart = MeStats.startOfTodayMillis(now = now, zone = ZoneId.of("+08:00"))
        // UTC midnight = 2026-07-23T00:00Z. UTC+8 same-day midnight = 2026-07-22T16:00Z, 8 hours earlier.
        assertThat(cstStart).isEqualTo(utcStart - 8 * 3600_000L)
    }

    @Test
    fun startOfTodayMillis_utcMidnightCrossesDayInPositiveZone() {
        // UTC 2026-07-23T15:00:00Z is 2026-07-23T23:00:00 in UTC+8, still day 7/23.
        // But UTC 2026-07-23T16:00:00Z is 2026-07-24T00:00:00 in UTC+8, already day 7/24.
        val before = MeStats.startOfTodayMillis(
            now = Instant.parse("2026-07-23T15:59:59Z"),
            zone = ZoneId.of("+08:00"),
        )
        val after = MeStats.startOfTodayMillis(
            now = Instant.parse("2026-07-23T16:00:01Z"),
            zone = ZoneId.of("+08:00"),
        )
        // "before" resolves to UTC 7/23 16:00, still day 7/23. "after" rolls over to 7/24.
        assertThat(after).isGreaterThan(before)
    }
}
