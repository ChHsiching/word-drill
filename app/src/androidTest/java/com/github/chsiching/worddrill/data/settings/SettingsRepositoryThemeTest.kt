package com.github.chsiching.worddrill.data.settings

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * [SettingsRepository] 主题偏好的持久化（Ticket #9 AC：重启 App 保持上次选择）。
 *
 * 用真 Context（instrumented）写真 DataStore；为模拟"重启"，清空单例缓存后 new 一个
 * 新 [SettingsRepository] 实例（同一文件），读回应是写入值。
 *
 * 用 Truth assertThat（repo 约定，坑 H）；测试方法用块体而非 `= runBlocking {}`
 * 表达式体（坑 J：最后一条语句需返回 Unit）。
 */
@RunWith(AndroidJUnit4::class)
class SettingsRepositoryThemeTest {

    private val context: android.content.Context
        get() = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() = runBlocking {
        // 隔离：清掉上轮测试写入的 theme_mode，保证每个测试干净起点
        SettingsRepository(context).setTheme(ThemeMode.SYSTEM)
        Unit
    }

    @After
    fun tearDown() = runBlocking {
        SettingsRepository(context).setTheme(ThemeMode.SYSTEM)
        Unit
    }

    @Test
    fun themePreference_defaultsToSystem_whenNeverSet() = runBlocking {
        // 全新状态（已重置为 SYSTEM）读回应是 SYSTEM
        val mode = SettingsRepository(context).themePreference.first()
        assertThat(mode).isEqualTo(ThemeMode.SYSTEM)
        Unit
    }

    @Test
    fun setTheme_persistsAcrossRepositoryInstances() = runBlocking {
        // 写入 DARK
        SettingsRepository(context).setTheme(ThemeMode.DARK)
        // 模拟"重启 App"：new 一个新实例（同一 DataStore 文件），读回应是 DARK
        val reloaded = SettingsRepository(context).themePreference.first()
        assertThat(reloaded).isEqualTo(ThemeMode.DARK)
        Unit
    }

    @Test
    fun setTheme_lightRoundTripsAcrossInstances() = runBlocking {
        SettingsRepository(context).setTheme(ThemeMode.LIGHT)
        assertThat(SettingsRepository(context).themePreference.first()).isEqualTo(ThemeMode.LIGHT)
        Unit
    }
}
