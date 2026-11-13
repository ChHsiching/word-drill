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
 * Software-settings ViewModel of the "Me" tab (Ticket #9 + #16).
 *
 * Separate from [MeViewModel] (stats aggregation): settings and stats are two concerns;
 * each observes its own Flow, so a settings change does not re-subscribe the stats
 * combine.
 *
 * Theme: [themeMode] subscribes to [SettingsRepository.themePreference]; a switch is
 * persisted, and [com.github.chsiching.worddrill.MainActivity] observes the same Flow, so
 * the global palette takes effect immediately.
 *
 * Ticket #16 adds UI settings: hide phonetics / nav bar style / compact navigation. All
 * three are also persisted to DataStore and observed by
 * [com.github.chsiching.worddrill.ui.navigation.WordDrillRoot] to drive the nav bar and
 * card rendering.
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

    /** User picked a new theme: persist it so it survives restart. */
    fun setTheme(mode: ThemeMode) {
        viewModelScope.launch { settings.setTheme(mode) }
    }

    /**
     * Cycles through the three [ThemeMode] states (review feedback 4: tapping the theme
     * row cycles; no more dialog). Order: LIGHT, DARK, SYSTEM, back to LIGHT...
     */
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

    /** Cycles between [NavStyle.PILL] and [NavStyle.BAR]. */
    fun cycleNavStyle() {
        val next = if (navStyle.value == NavStyle.PILL) NavStyle.BAR else NavStyle.PILL
        setNavStyle(next)
    }

    fun setCompactNav(value: Boolean) {
        viewModelScope.launch { settings.setCompactNav(value) }
    }
}
