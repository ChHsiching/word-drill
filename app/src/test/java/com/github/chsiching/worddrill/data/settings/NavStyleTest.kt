package com.github.chsiching.worddrill.data.settings

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * [NavStyle.fromStorageName] 的纯函数 JVM 单测（Ticket #16）。
 *
 * 与 [ThemeMode] 一致：未知/损坏值安全回退到默认 [NavStyle.PILL]，不抛异常。
 */
class NavStyleTest {

    @Test
    fun fromStorageName_returnsPill_whenNameIsPill() {
        assertThat(NavStyle.fromStorageName("PILL")).isEqualTo(NavStyle.PILL)
    }

    @Test
    fun fromStorageName_returnsBar_whenNameIsBar() {
        assertThat(NavStyle.fromStorageName("BAR")).isEqualTo(NavStyle.BAR)
    }

    @Test
    fun fromStorageName_defaultsToPill_whenNull() {
        assertThat(NavStyle.fromStorageName(null)).isEqualTo(NavStyle.PILL)
    }

    @Test
    fun fromStorageName_defaultsToPill_whenUnknown() {
        assertThat(NavStyle.fromStorageName("garbage")).isEqualTo(NavStyle.PILL)
        assertThat(NavStyle.fromStorageName("")).isEqualTo(NavStyle.PILL)
        assertThat(NavStyle.fromStorageName("pill")).isEqualTo(NavStyle.PILL) // 大小写敏感
    }
}
