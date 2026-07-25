package com.github.chsiching.worddrill.ui.navigation

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * [selectedTabIndexForRoute] 单测：导航栏高亮归属（修复「进回收站高亮『刷』」的 bug）。
 *
 * 覆盖：
 * - 三个顶层 route 精确匹配各自 Tab
 * - 二级页 library/{bookId} → Library Tab（pre-existing 同类 bug 一并覆盖）
 * - 二级页 recycle_bin → Me Tab（本次修复的目标）
 * - 未知 route / null → null（不高亮任一项）
 */
class SelectedTabIndexForRouteTest {

    @Test
    fun exactMatch_drill() {
        assertThat(selectedTabIndexForRoute("drill"))
            .isEqualTo(topDestinations.indexOfFirst { it.route == "drill" })
    }

    @Test
    fun exactMatch_library() {
        assertThat(selectedTabIndexForRoute("library"))
            .isEqualTo(topDestinations.indexOfFirst { it.route == "library" })
    }

    @Test
    fun exactMatch_me() {
        assertThat(selectedTabIndexForRoute("me"))
            .isEqualTo(topDestinations.indexOfFirst { it.route == "me" })
    }

    @Test
    fun wordListRoute_mapsToLibrary() {
        // library/{bookId} 二级页应归属 Library Tab（之前 fallback 到 0/Drill 是 bug）
        val idx = selectedTabIndexForRoute("library/123")
        assertThat(idx).isEqualTo(topDestinations.indexOfFirst { it.route == "library" })
        assertThat(idx).isNotEqualTo(topDestinations.indexOfFirst { it.route == "drill" })
    }

    @Test
    fun recycleBinRoute_mapsToMe() {
        // recycle_bin 从「我的」进入，应归属 Me Tab（本次修复核心）
        val idx = selectedTabIndexForRoute("recycle_bin")
        assertThat(idx).isEqualTo(topDestinations.indexOfFirst { it.route == "me" })
        assertThat(idx).isNotEqualTo(topDestinations.indexOfFirst { it.route == "drill" })
    }

    @Test
    fun unknownRoute_returnsNull() {
        // 未知 route 不高亮任一项（好过错误高亮第一个）
        assertThat(selectedTabIndexForRoute("unknown")).isNull()
    }

    @Test
    fun nullRoute_returnsNull() {
        assertThat(selectedTabIndexForRoute(null)).isNull()
    }
}
