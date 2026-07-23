package com.github.chsiching.worddrill.ui.me

import java.time.Instant
import java.time.ZoneId

/**
 * 「我的」Tab 统计的纯计算逻辑（Ticket #8）。
 *
 * 拆成纯函数便于 JVM 单测（无 Android 依赖、无时钟副作用耦合）。
 * - [startOfTodayMillis]：今日 0 点的 epoch 毫秒，用于「今日刷卡数」筛选。
 * - [progressPercent]：当前词书进度百分比，分母为 0 时返回 0（避免除零）。
 */
internal object MeStats {

    /**
     * 今日 0 点（指定时区）的 epoch 毫秒。
     * 注入 [now] 与 [zone] 便于单测固定时间，生产传 [Instant.now] / [ZoneId.systemDefault]。
     */
    fun startOfTodayMillis(
        now: Instant = Instant.now(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): Long = now.atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()

    /** 当前词书进度百分比：已刷 [brushed] / 总 [total]，0-100 整数。分母 0 返回 0。 */
    fun progressPercent(brushed: Int, total: Int): Int {
        if (total <= 0) return 0
        val percent = brushed.toLong() * 100L / total.toLong()
        return percent.toInt().coerceIn(0, 100)
    }
}
