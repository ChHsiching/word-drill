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

    fun setTheme(mode: ThemeMode) {
        viewModelScope.launch { settings.setTheme(mode) }
    }

    fun cycleTheme() {
        val next = when (themeMode.value) {
            ThemeMode.LIGHT -> ThemeMode.DARK
            ThemeMode.DARK -> ThemeMode.SYSTEM
            ThemeMode.SYSTEM -> ThemeMode.LIGHT
        }
        setTheme(next)
    }

    fun setHidePhonetic(value: Boolean) {
        viewModelScope.launch { settings.setHidePhonetic(value) }
    }

    fun setNavStyle(style: NavStyle) {
        viewModelScope.launch { settings.setNavStyle(style) }
    }

    fun cycleNavStyle() {
        val next = if (navStyle.value == NavStyle.PILL) NavStyle.BAR else NavStyle.PILL
        setNavStyle(next)
    }

    fun setCompactNav(value: Boolean) {
        viewModelScope.launch { settings.setCompactNav(value) }
    }
}
