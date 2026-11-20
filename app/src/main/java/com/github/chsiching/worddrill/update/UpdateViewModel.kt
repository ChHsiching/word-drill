package com.github.chsiching.worddrill.update

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.chsiching.worddrill.R
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface UpdateUiState {
    /** Checking, up to date, or dismissed — nothing on screen. */
    data object Idle : UpdateUiState

    data class Available(val release: ReleaseInfo) : UpdateUiState

    /** Manual check found nothing newer: inline up-to-date line in the Me tab. */
    data object UpToDate : UpdateUiState

    /** Inline diagnostic banner (check or download failure). */
    data class Notice(val messageRes: Int) : UpdateUiState
}

sealed interface UpdateEvent {
    /** The install-packages permission is missing; the UI must open system settings. */
    data object RequestInstallPermission : UpdateEvent
}

@HiltViewModel
class UpdateViewModel @Inject constructor(
    private val checker: UpdateChecker,
    private val installer: ApkInstaller,
    private val cache: UpdateCheckCache,
) : ViewModel() {

    private val _state = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val state: StateFlow<UpdateUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<UpdateEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<UpdateEvent> = _events.asSharedFlow()

    private var checkStarted = false
    private var dismissed = false
    private var pendingDownload: ReleaseInfo? = null

    /** Launch check (post-splash). Rotation re-triggers this; it must run only once. */
    fun checkForUpdate() {
        if (checkStarted) return
        checkStarted = true
        viewModelScope.launch {
            when (val result = cache.getOrFetch(checker)) {
                is UpdateCheckResult.UpdateAvailable ->
                    if (!dismissed) _state.value = UpdateUiState.Available(result.release)
                UpdateCheckResult.UpToDate -> Unit // completely silent
                // Background check: no UI unless a newer release was found (spec).
                is UpdateCheckResult.CheckFailed -> Unit
            }
        }
    }

    /**
     * Me tab's check-update row (R.string.me_setting_check_update). Always performs
     * a real fetch — the launch check's cached result is never replayed and a
     * failure is never cached, so every tap (and every retry after a failure) hits
     * the network again. Unlike the silent launch check, every outcome is surfaced.
     */
    fun manualCheck() {
        viewModelScope.launch {
            when (val result = checker.checkForUpdate()) {
                is UpdateCheckResult.UpdateAvailable -> {
                    // An explicit check overrides an earlier dialog dismissal.
                    dismissed = false
                    _state.value = UpdateUiState.Available(result.release)
                }
                UpdateCheckResult.UpToDate -> _state.value = UpdateUiState.UpToDate
                is UpdateCheckResult.CheckFailed ->
                    _state.value = UpdateUiState.Notice(result.messageRes)
            }
        }
    }

    /** Update now (R.string.update_action_now). */
    fun updateNow() {
        val release = (_state.value as? UpdateUiState.Available)?.release ?: return
        if (!installer.canInstallApk()) {
            pendingDownload = release
            _events.tryEmit(UpdateEvent.RequestInstallPermission)
            return
        }
        startDownload(release)
    }

    /** Called when the user returns from the unknown-app-sources settings screen. */
    fun onInstallPermissionResult() {
        val release = pendingDownload ?: return
        if (installer.canInstallApk()) {
            pendingDownload = null
            startDownload(release)
        }
    }

    /** Later (R.string.update_action_later) / scrim tap: gone until the next launch. */
    fun dismissDialog() {
        dismissed = true
        _state.value = UpdateUiState.Idle
    }

    fun dismissNotice() {
        dismissed = true
        _state.value = UpdateUiState.Idle
    }

    private fun startDownload(release: ReleaseInfo) {
        // Progress lives in the DownloadManager notification; the dialog just closes.
        _state.value = UpdateUiState.Idle
        installer.downloadAndInstall(release.apkUrl, release.version) {
            _state.value = UpdateUiState.Notice(R.string.update_download_failed)
        }
    }
}
