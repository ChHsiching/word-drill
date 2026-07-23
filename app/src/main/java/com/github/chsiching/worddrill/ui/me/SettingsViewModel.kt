package com.github.chsiching.worddrill.ui.me

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.chsiching.worddrill.data.settings.SettingsRepository
import com.github.chsiching.worddrill.data.settings.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 「我的」Tab 的软件设置 ViewModel（Ticket #9）。
 *
 * 与 [MeViewModel]（统计聚合）分开：设置与统计是两个关注点，各自独立 observe Flow，
 * 避免设置变更触发统计的 combine 重订阅。
 *
 * 主题：[themeMode] 订阅 [SettingsRepository.themePreference]，切换时落库，
 * [com.github.chsiching.worddrill.MainActivity] 同样订阅该 Flow → 全局配色立即生效。
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

    /** 用户选了新主题 → 落库，重启保持。 */
    fun setTheme(mode: ThemeMode) {
        viewModelScope.launch { settings.setTheme(mode) }
    }
}
