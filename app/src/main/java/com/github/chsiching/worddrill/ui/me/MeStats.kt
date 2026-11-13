package com.github.chsiching.worddrill.ui.me

import java.time.Instant
import java.time.ZoneId

/**
 * Pure computation logic for the "Me" tab stats (Ticket #8).
 *
 * Split into pure functions for easy JVM unit testing (no Android dependency, no clock
 * side effects).
 * - [startOfTodayMillis]: epoch millis of today's start of day, used to filter "cards
 *   drilled today".
 * - [progressPercent]: current book progress percent; returns 0 when the denominator is 0
 *   (avoids division by zero).
 */
internal object MeStats {

    /**
     * Epoch millis of today's start of day (in the given time zone).
     * [now] and [zone] are injected so unit tests can pin the time; production passes
     * [Instant.now] / [ZoneId.systemDefault].
     */
    fun startOfTodayMillis(
        now: Instant = Instant.now(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): Long = now.atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()

    /** Current book progress percent: brushed [brushed] / total [total], an integer 0-100. Returns 0 when the denominator is 0. */
    fun progressPercent(brushed: Int, total: Int): Int {
        if (total <= 0) return 0
        val percent = brushed.toLong() * 100L / total.toLong()
        return percent.toInt().coerceIn(0, 100)
    }
}
