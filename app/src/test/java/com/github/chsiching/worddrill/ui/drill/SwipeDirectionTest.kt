package com.github.chsiching.worddrill.ui.drill

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * 向右滑（前进）计数逻辑的纯函数单测。规格验收：
 * - 向右滑切下一张 → 计数 +1（应记录 swipe_log）
 * - 向左滑切上一张 → 不计数
 * - 到头（已停在边界，页码不变）→ 不计数
 *
 * 拆成纯函数便于 JVM 单测（无 Android 依赖、无 Pager 手势耦合）。
 * 用从 0 起的页码语义：page 索引向右（前进）递增。
 */
class SwipeDirectionTest {

    // ---- 向右滑（前进）：页码递增 → 计数 ----

    @Test
    fun forwardSwipe_increasesPage_logsSwipe() {
        // 0 → 1：向右滑到下一张，应计数
        assertThat(shouldLogSwipe(previousPage = 0, currentPage = 1)).isTrue()
    }

    @Test
    fun forwardSwipe_multipleSteps_logsSwipe() {
        // 跳页前进也算（极端快滑），只要 page 增加就是前进方向
        assertThat(shouldLogSwipe(previousPage = 2, currentPage = 5)).isTrue()
    }

    // ---- 向左滑（回看）：页码递减 → 不计数 ----

    @Test
    fun backwardSwipe_decreasesPage_doesNotLog() {
        // 2 → 1：向左滑回看上一张，不计数
        assertThat(shouldLogSwipe(previousPage = 2, currentPage = 1)).isFalse()
    }

    @Test
    fun backwardSwipe_toStart_doesNotLog() {
        // 1 → 0：向左滑到第一张，仍属回看方向，不计数
        assertThat(shouldLogSwipe(previousPage = 1, currentPage = 0)).isFalse()
    }

    // ---- 到头 / 无位移：页码不变 → 不计数（含停在第一张/最后一张继续滑）----

    @Test
    fun noMovement_samePage_doesNotLog() {
        // 页码未变（停在边界继续滑、或 settled 重复回调）：不计数
        assertThat(shouldLogSwipe(previousPage = 0, currentPage = 0)).isFalse()
    }

    @Test
    fun stuckAtFirst_samePage_doesNotLog() {
        // 已在第一张(0)还往前滑，Pager 阻挡页码不变：不计数
        assertThat(shouldLogSwipe(previousPage = 0, currentPage = 0)).isFalse()
    }

    @Test
    fun stuckAtLast_samePage_doesNotLog() {
        // 已在最后一张(N)还往后滑，Pager 阻挡页码不变：不计数
        assertThat(shouldLogSwipe(previousPage = 9, currentPage = 9)).isFalse()
    }
}
