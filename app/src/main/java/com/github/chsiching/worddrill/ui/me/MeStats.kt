package com.github.chsiching.worddrill.ui.me

import java.time.Instant
import java.time.ZoneId

internal object MeStats {

    fun startOfTodayMillis(
        now: Instant = Instant.now(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): Long = now.atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()

    /** Brushed/total as a percentage. A deck emptied by skipping every word
     *  (total 0, brushed > 0) counts as complete; nothing brushed at all is 0. */
    fun progressPercent(brushed: Int, total: Int): Double {
        if (total <= 0) return if (brushed > 0) 100.0 else 0.0
        return (brushed * 100.0 / total).coerceIn(0.0, 100.0)
    }

    /** One decimal inside the open interval (0, 100); exactly 0 and 100 as integers. */
    fun formatPercent(percent: Double): String = when {
        percent == 0.0 -> "0%"
        percent == 100.0 -> "100%"
        else -> String.format(java.util.Locale.ROOT, "%.1f%%", percent)
    }
}
