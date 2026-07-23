package com.github.chsiching.worddrill.data.settings

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * [ThemeMode.fromStorageName] 纯函数解码（Ticket #9）。
 *
 * 覆盖：合法值往返、null（键不存在）、损坏/未知值回退 SYSTEM（避免 Flow 崩）。
 * 用 Truth assertThat（repo 约定，坑 H：勿用 kotlin.assert）。
 */
class ThemeModeTest {

    @Test
    fun fromStorageName_recognizesAllModes() {
        assertThat(ThemeMode.fromStorageName("LIGHT")).isEqualTo(ThemeMode.LIGHT)
        assertThat(ThemeMode.fromStorageName("DARK")).isEqualTo(ThemeMode.DARK)
        assertThat(ThemeMode.fromStorageName("SYSTEM")).isEqualTo(ThemeMode.SYSTEM)
    }

    @Test
    fun fromStorageName_defaultsToSystem_whenKeyMissing() {
        // 键不存在时 DataStore 给 null：回退 SYSTEM（默认偏好）
        assertThat(ThemeMode.fromStorageName(null)).isEqualTo(ThemeMode.SYSTEM)
    }

    @Test
    fun fromStorageName_fallsBackToSystem_onCorruptValue() {
        // 损坏 / 未来版本新枚举名 / 旧版本残留 → 不抛异常，回退 SYSTEM
        assertThat(ThemeMode.fromStorageName("DARK_MODE")).isEqualTo(ThemeMode.SYSTEM)
        assertThat(ThemeMode.fromStorageName("light")).isEqualTo(ThemeMode.SYSTEM) // 大小写敏感
        assertThat(ThemeMode.fromStorageName("")).isEqualTo(ThemeMode.SYSTEM)
    }

    @Test
    fun name_isRoundTrippable_throughFromStorageName() {
        // 持久化用 name()，读回用 fromStorageName：所有枚举往返不丢信息
        for (mode in ThemeMode.entries) {
            assertThat(ThemeMode.fromStorageName(mode.name)).isEqualTo(mode)
        }
    }
}
