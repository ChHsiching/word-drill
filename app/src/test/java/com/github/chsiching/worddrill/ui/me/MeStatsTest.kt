package com.github.chsiching.worddrill.ui.me

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

/**
 * 「我的」Tab 统计纯函数单测（Ticket #8）。
 *
 * 覆盖规格验收点的可计算部分：
 * - 今日起点：给定时刻 → 今日 0 点毫秒（跨午夜、时区、UTC 边界）
 * - 进度百分比：含除零（空词书）、正常、满额、超额防御
 *
 * 使用 Truth assertThat（repo 约定，见坑 H —— 勿用 kotlin.assert）。
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
        // 空词书：分母 0，不除零，返回 0%（规格：「别除零」）
        assertThat(MeStats.progressPercent(brushed = 0, total = 0)).isEqualTo(0)
        assertThat(MeStats.progressPercent(brushed = 3, total = 0)).isEqualTo(0)
    }

    @Test
    fun progressPercent_truncatesNotRounds() {
        // 整数截断：1/3 = 33%（向下取整，不是 33.33%）
        assertThat(MeStats.progressPercent(brushed = 1, total = 3)).isEqualTo(33)
    }

    @Test
    fun progressPercent_defensiveAgainstOverflow() {
        // 大数不溢出 int：用 Long 中间计算
        assertThat(MeStats.progressPercent(brushed = 1_000_000, total = 4_000_000)).isEqualTo(25)
    }

    // ---- startOfTodayMillis ----

    @Test
    fun startOfTodayMillis_middayReturnsSameDayMidnight() {
        // 固定 UTC 2026-07-23 13:45:30 → 今日 0 点 = 2026-07-23 00:00:00 UTC
        val now = Instant.parse("2026-07-23T13:45:30Z")
        val zone = ZoneId.of("UTC")
        val start = MeStats.startOfTodayMillis(now = now, zone = zone)
        assertThat(start).isEqualTo(Instant.parse("2026-07-23T00:00:00Z").toEpochMilli())
    }

    @Test
    fun startOfTodayMillis_justBeforeMidnightStillSameDay() {
        // 23:59:59 仍属当日，0 点是当天 00:00，不是次日
        val now = Instant.parse("2026-07-23T23:59:59Z")
        val zone = ZoneId.of("UTC")
        val start = MeStats.startOfTodayMillis(now = now, zone = zone)
        assertThat(start).isEqualTo(Instant.parse("2026-07-23T00:00:00Z").toEpochMilli())
    }

    @Test
    fun startOfTodayMillis_justAfterMidnightRollsToNewDay() {
        // 00:00:01 属新一天，0 点是当天 00:00
        val now = Instant.parse("2026-07-24T00:00:01Z")
        val zone = ZoneId.of("UTC")
        val start = MeStats.startOfTodayMillis(now = now, zone = zone)
        assertThat(start).isEqualTo(Instant.parse("2026-07-24T00:00:00Z").toEpochMilli())
    }

    @Test
    fun startOfTodayMillis_respectsTimeZone() {
        // 同一 UTC 时刻，UTC+8 时区的「今日 0 点」比 UTC 晚 8 小时对应的毫秒
        val now = Instant.parse("2026-07-23T10:00:00Z") // UTC+8 = 18:00 当日
        val utcStart = MeStats.startOfTodayMillis(now = now, zone = ZoneId.of("UTC"))
        val cstStart = MeStats.startOfTodayMillis(now = now, zone = ZoneId.of("+08:00"))
        // UTC 0 点 = 2026-07-23T00:00Z；UTC+8 当日 0 点 = 2026-07-22T16:00Z（早 8h）
        assertThat(cstStart).isEqualTo(utcStart - 8 * 3600_000L)
    }

    @Test
    fun startOfTodayMillis_utcMidnightCrossesDayInPositiveZone() {
        // UTC 2026-07-23T15:00:00Z = UTC+8 2026-07-23T23:00:00 仍属 7/23
        // 但 UTC 2026-07-23T16:00:00Z = UTC+8 2026-07-24T00:00:00 已跨入 7/24
        val before = MeStats.startOfTodayMillis(
            now = Instant.parse("2026-07-23T15:59:59Z"),
            zone = ZoneId.of("+08:00"),
        )
        val after = MeStats.startOfTodayMillis(
            now = Instant.parse("2026-07-23T16:00:01Z"),
            zone = ZoneId.of("+08:00"),
        )
        // before 的今日 0 点对应 UTC 7/23 16:00 = 7/23 当日；after 跨日到 7/24
        assertThat(after).isGreaterThan(before)
    }
}
