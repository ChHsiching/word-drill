package com.github.chsiching.worddrill.update

import com.github.chsiching.worddrill.R
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UpdateViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val release = ReleaseInfo(
        version = "0.3.0",
        notes = "修复与改进",
        apkUrl = "https://example.com/worddrill-v0.3.0.apk",
    )

    private class FakeChecker(var result: UpdateCheckResult) : UpdateChecker {
        var calls = 0
        override suspend fun checkForUpdate(): UpdateCheckResult {
            calls++
            return result
        }
    }

    private class FakeInstaller(var canInstall: Boolean = true) : ApkInstaller {
        val downloads = mutableListOf<Pair<String, String>>()
        var failedCallback: (() -> Unit)? = null

        override fun canInstallApk() = canInstall

        override fun downloadAndInstall(apkUrl: String, version: String, onFailed: () -> Unit) {
            downloads += apkUrl to version
            failedCallback = onFailed
        }
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun check_newerRelease_showsDialogState() = runTest {
        val vm = UpdateViewModel(FakeChecker(UpdateCheckResult.UpdateAvailable(release)), FakeInstaller(), UpdateCheckCache())
        vm.checkForUpdate()
        advanceUntilIdle()

        assertThat(vm.state.value).isEqualTo(UpdateUiState.Available(release))
    }

    @Test
    fun check_upToDate_staysIdle() = runTest {
        val vm = UpdateViewModel(FakeChecker(UpdateCheckResult.UpToDate), FakeInstaller(), UpdateCheckCache())
        vm.checkForUpdate()
        advanceUntilIdle()

        assertThat(vm.state.value).isEqualTo(UpdateUiState.Idle)
    }

    @Test
    fun check_failure_staysSilent() = runTest {
        val vm = UpdateViewModel(
            FakeChecker(UpdateCheckResult.CheckFailed(R.string.update_error_dns)),
            FakeInstaller(),
            UpdateCheckCache(),
        )
        vm.checkForUpdate()
        advanceUntilIdle()

        // Background check: no UI unless a newer release was found (spec).
        assertThat(vm.state.value).isEqualTo(UpdateUiState.Idle)
    }

    @Test
    fun dismissDialog_staysDismissedForRestOfSession() = runTest {
        val vm = UpdateViewModel(
            FakeChecker(UpdateCheckResult.UpdateAvailable(release)),
            FakeInstaller(),
            UpdateCheckCache(),
        )
        vm.checkForUpdate()
        advanceUntilIdle()
        vm.dismissDialog()
        // A re-check within the same session (e.g. recomposition re-trigger) must not reopen.
        vm.checkForUpdate()
        advanceUntilIdle()

        assertThat(vm.state.value).isEqualTo(UpdateUiState.Idle)
    }

    @Test
    fun updateNow_withInstallPermission_closesDialogAndStartsDownload() = runTest {
        val installer = FakeInstaller(canInstall = true)
        val vm = UpdateViewModel(
            FakeChecker(UpdateCheckResult.UpdateAvailable(release)),
            installer,
            UpdateCheckCache(),
        )
        vm.checkForUpdate()
        advanceUntilIdle()

        vm.updateNow()
        advanceUntilIdle()

        assertThat(installer.downloads).containsExactly(release.apkUrl to release.version)
        assertThat(vm.state.value).isEqualTo(UpdateUiState.Idle)
    }

    @Test
    fun updateNow_withoutPermission_requestsPermission_thenDownloadsAfterGrant() = runTest {
        val installer = FakeInstaller(canInstall = false)
        val vm = UpdateViewModel(
            FakeChecker(UpdateCheckResult.UpdateAvailable(release)),
            installer,
            UpdateCheckCache(),
        )
        val events = mutableListOf<UpdateEvent>()
        val collector = launch(start = CoroutineStart.UNDISPATCHED) {
            vm.events.collect { events += it }
        }
        vm.checkForUpdate()
        advanceUntilIdle()

        vm.updateNow()
        advanceUntilIdle()

        // Permission missing: only the request is emitted, nothing downloads yet.
        assertThat(events).containsExactly(UpdateEvent.RequestInstallPermission)
        assertThat(installer.downloads).isEmpty()

        // User granted in system settings and returned.
        installer.canInstall = true
        vm.onInstallPermissionResult()
        advanceUntilIdle()

        assertThat(installer.downloads).containsExactly(release.apkUrl to release.version)
        assertThat(vm.state.value).isEqualTo(UpdateUiState.Idle)
        collector.cancel()
    }

    @Test
    fun downloadFailure_showsNotice() = runTest {
        val installer = FakeInstaller(canInstall = true)
        val vm = UpdateViewModel(
            FakeChecker(UpdateCheckResult.UpdateAvailable(release)),
            installer,
            UpdateCheckCache(),
        )
        vm.checkForUpdate()
        advanceUntilIdle()
        vm.updateNow()
        advanceUntilIdle()

        installer.failedCallback!!.invoke()

        assertThat(vm.state.value).isEqualTo(UpdateUiState.Notice(R.string.update_download_failed))
    }

    // --- Manual check-update row (#59) ---

    @Test
    fun manualCheck_upToDate_showsUpToDateState() = runTest {
        val vm = UpdateViewModel(FakeChecker(UpdateCheckResult.UpToDate), FakeInstaller(), UpdateCheckCache())
        vm.manualCheck()
        advanceUntilIdle()

        assertThat(vm.state.value).isEqualTo(UpdateUiState.UpToDate)
    }

    @Test
    fun manualCheck_updateAvailable_showsDialogState() = runTest {
        val vm = UpdateViewModel(
            FakeChecker(UpdateCheckResult.UpdateAvailable(release)),
            FakeInstaller(),
            UpdateCheckCache(),
        )
        vm.manualCheck()
        advanceUntilIdle()

        assertThat(vm.state.value).isEqualTo(UpdateUiState.Available(release))
    }

    @Test
    fun manualCheck_afterDismissal_showsDialogAgain() = runTest {
        val vm = UpdateViewModel(
            FakeChecker(UpdateCheckResult.UpdateAvailable(release)),
            FakeInstaller(),
            UpdateCheckCache(),
        )
        vm.checkForUpdate()
        advanceUntilIdle()
        vm.dismissDialog()

        // An explicit manual check overrides the launch dialog's dismissal.
        vm.manualCheck()
        advanceUntilIdle()

        assertThat(vm.state.value).isEqualTo(UpdateUiState.Available(release))
    }

    @Test
    fun manualCheck_failure_showsNoticeWithMappedMessage() = runTest {
        val vm = UpdateViewModel(
            FakeChecker(UpdateCheckResult.CheckFailed(R.string.update_error_dns)),
            FakeInstaller(),
            UpdateCheckCache(),
        )
        vm.manualCheck()
        advanceUntilIdle()

        assertThat(vm.state.value)
            .isEqualTo(UpdateUiState.Notice(R.string.update_error_dns))
    }

    // Reopened #59: the manual row always performs a real fetch, cached launch
    // result or not; a previous failure never blocks a retry.

    @Test
    fun manualCheck_afterAutomaticLaunchCheck_refetches() = runTest {
        val checker = FakeChecker(UpdateCheckResult.UpToDate)
        val cache = UpdateCheckCache()
        val launchVm = UpdateViewModel(checker, FakeInstaller(), cache)
        val meVm = UpdateViewModel(checker, FakeInstaller(), cache)
        launchVm.checkForUpdate()
        advanceUntilIdle()

        meVm.manualCheck()
        advanceUntilIdle()

        assertThat(checker.calls).isEqualTo(2)
        assertThat(meVm.state.value).isEqualTo(UpdateUiState.UpToDate)
    }

    @Test
    fun manualCheck_afterPreviousManualCheck_refetches() = runTest {
        val checker = FakeChecker(UpdateCheckResult.UpdateAvailable(release))
        val vm = UpdateViewModel(checker, FakeInstaller(), UpdateCheckCache())
        vm.manualCheck()
        advanceUntilIdle()
        vm.dismissDialog()

        vm.manualCheck()
        advanceUntilIdle()

        assertThat(checker.calls).isEqualTo(2)
        assertThat(vm.state.value).isEqualTo(UpdateUiState.Available(release))
    }

    @Test
    fun manualCheck_afterFailure_refetches() = runTest {
        val checker = FakeChecker(UpdateCheckResult.CheckFailed(R.string.update_error_dns))
        val vm = UpdateViewModel(checker, FakeInstaller(), UpdateCheckCache())
        vm.manualCheck()
        advanceUntilIdle()
        assertThat(vm.state.value).isEqualTo(UpdateUiState.Notice(R.string.update_error_dns))

        // The failure was not cached; a retry fetches again and can recover.
        checker.result = UpdateCheckResult.UpToDate
        vm.manualCheck()
        advanceUntilIdle()

        assertThat(checker.calls).isEqualTo(2)
        assertThat(vm.state.value).isEqualTo(UpdateUiState.UpToDate)
    }
}
