package com.github.chsiching.worddrill.ui.me

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.chsiching.worddrill.data.settings.NavStyle
import com.github.chsiching.worddrill.data.settings.SettingsRepository
import com.github.chsiching.worddrill.data.settings.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 「我的」Tab 的软件设置 ViewModel（Ticket #9 + #16）。
 *
 * 与 [MeViewModel]（统计聚合）分开：设置与统计是两个关注点，各自独立 observe Flow，
 * 避免设置变更触发统计的 combine 重订阅。
 *
 * 主题：[themeMode] 订阅 [SettingsRepository.themePreference]，切换时落库，
 * [com.github.chsiching.worddrill.MainActivity] 同样订阅该 Flow → 全局配色立即生效。
 *
 * Ticket #16 新增 UI 设置：隐藏音标 / 导航栏风格 / 简约导航。三者同样持久化到 DataStore，
 * 由 [com.github.chsiching.worddrill.ui.navigation.WordDrillRoot] 订阅并驱动导航栏与卡片渲染。
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
) : ViewModel() {

    val themeMode: StateFlow<ThemeMode> = settings.themePreference.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ThemeMode.SYSTEM,
    )

    val hidePhonetic: StateFlow<Boolean> = settings.hidePhonetic.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = false,
    )

    val navStyle: StateFlow<NavStyle> = settings.navStyle.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = NavStyle.PILL,
    )

    val compactNav: StateFlow<Boolean> = settings.compactNav.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = false,
    )

    /** 用户选了新主题 → 落库，重启保持。 */
    fun setTheme(mode: ThemeMode) {
        viewModelScope.launch { settings.setTheme(mode) }
    }

    fun setHidePhonetic(value: Boolean) {
        viewModelScope.launch { settings.setHidePhonetic(value) }
    }

    fun setNavStyle(style: NavStyle) {
        viewModelScope.launch { settings.setNavStyle(style) }
    }

    /** 在 [NavStyle.PILL] / [NavStyle.BAR] 之间循环切换。 */
    fun cycleNavStyle() {
        val next = if (navStyle.value == NavStyle.PILL) NavStyle.BAR else NavStyle.PILL
        setNavStyle(next)
    }

    fun setCompactNav(value: Boolean) {
        viewModelScope.launch { settings.setCompactNav(value) }
    }
}
