package com.github.chsiching.worddrill.ui.me

import java.time.Instant
import java.time.ZoneId

internal object MeStats {

    fun startOfTodayMillis(
        now: Instant = Instant.now(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): Long = now.atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()

    fun progressPercent(brushed: Int, total: Int): Int {
        if (total <= 0) return 0
        val percent = brushed.toLong() * 100L / total.toLong()
        return percent.toInt().coerceIn(0, 100)
    }
}
