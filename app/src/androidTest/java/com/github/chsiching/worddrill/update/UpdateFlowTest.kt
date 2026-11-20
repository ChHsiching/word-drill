package com.github.chsiching.worddrill.update

import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.chsiching.worddrill.R
import com.github.chsiching.worddrill.ui.theme.WordDrillTheme
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Dialog-path coverage of the update flow with a fake checker and installer:
 * no real network or DownloadManager is touched on the emulator.
 *
 * Compose dialogs compose in their own window, whose attachment races with
 * waitForIdle; every presence/absence check therefore goes through the
 * awaitShown/awaitGone waitUntil helpers instead of a bare assert.
 */
@RunWith(AndroidJUnit4::class)
class UpdateFlowTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val release = ReleaseInfo(
        version = "0.3.0",
        notes = "修复与改进\n新增词书导入",
        apkUrl = "https://example.com/worddrill-v0.3.0.apk",
    )

    private class FakeChecker(var result: UpdateCheckResult) : UpdateChecker {
        override suspend fun checkForUpdate() = result
    }

    private class FakeInstaller(var canInstall: Boolean = true) : ApkInstaller {
        val downloads = mutableListOf<Pair<String, String>>()

        override fun canInstallApk() = canInstall

        override fun downloadAndInstall(apkUrl: String, version: String, onFailed: () -> Unit) {
            downloads += apkUrl to version
        }
    }

    private fun setContentWith(
        checkerResult: UpdateCheckResult,
        installer: FakeInstaller,
        onPermissionRequest: () -> Unit = {},
    ): UpdateViewModel {
        val vm = UpdateViewModel(FakeChecker(checkerResult), installer, UpdateCheckCache())
        composeRule.setContent {
            WordDrillTheme {
                Surface {
                    UpdateContent(
                        viewModel = vm,
                        onRequestInstallPermission = onPermissionRequest,
                    )
                }
            }
        }
        composeRule.waitForIdle()
        vm.checkForUpdate()
        composeRule.waitForIdle()
        return vm
    }

    private fun awaitShown(text: String, substring: Boolean = true) {
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText(text, substring = substring)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText(text, substring = substring).assertIsDisplayed()
    }

    private fun awaitGone(text: String, substring: Boolean = true) {
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText(text, substring = substring)
                .fetchSemanticsNodes().isEmpty()
        }
        composeRule.onNodeWithText(text, substring = substring).assertDoesNotExist()
    }

    @Test
    fun newerRelease_showsDialogWithVersionAndNotes() {
        setContentWith(UpdateCheckResult.UpdateAvailable(release), FakeInstaller())

        awaitShown("发现新版本", substring = false)
        awaitShown("版本 0.3.0", substring = false)
        awaitShown("修复与改进")
        awaitShown("立即更新", substring = false)
        awaitShown("暂不更新", substring = false)
    }

    @Test
    fun updateNow_withInstallPermission_closesDialogAndStartsDownload() {
        val installer = FakeInstaller(canInstall = true)
        setContentWith(UpdateCheckResult.UpdateAvailable(release), installer)
        awaitShown("立即更新", substring = false)

        composeRule.onNodeWithText("立即更新").performClick()

        assertThat(installer.downloads).containsExactly(release.apkUrl to release.version)
        awaitGone("发现新版本", substring = false)
    }

    @Test
    fun updateNow_withoutPermission_requestsPermission_thenDownloadsAfterGrant() {
        val installer = FakeInstaller(canInstall = false)
        var permissionRequests = 0
        val vm = setContentWith(
            UpdateCheckResult.UpdateAvailable(release),
            installer,
            onPermissionRequest = { permissionRequests++ },
        )
        awaitShown("立即更新", substring = false)

        composeRule.onNodeWithText("立即更新").performClick()

        // The dialog stays while the system permission screen is up; nothing downloads.
        assertThat(permissionRequests).isEqualTo(1)
        assertThat(installer.downloads).isEmpty()
        awaitShown("发现新版本", substring = false)

        // User granted in settings and came back; production wires this to the
        // activity result, tests call it directly.
        installer.canInstall = true
        composeRule.runOnIdle { vm.onInstallPermissionResult() }

        assertThat(installer.downloads).containsExactly(release.apkUrl to release.version)
        awaitGone("发现新版本", substring = false)
    }

    @Test
    fun later_dismissesDialog_andDoesNotReappearOnRecheck() {
        val vm = setContentWith(UpdateCheckResult.UpdateAvailable(release), FakeInstaller())
        awaitShown("暂不更新", substring = false)

        composeRule.onNodeWithText("暂不更新").performClick()

        awaitGone("发现新版本", substring = false)

        // Same session: a re-check must not reopen the dialog.
        vm.checkForUpdate()
        awaitGone("发现新版本", substring = false)
    }

    @Test
    fun upToDate_showsNoUi() {
        setContentWith(UpdateCheckResult.UpToDate, FakeInstaller())

        composeRule.onNodeWithText("发现新版本").assertDoesNotExist()
        composeRule.onNodeWithText("无法连接 GitHub").assertDoesNotExist()
    }

    @Test
    fun launchCheckFailure_showsNoUi() {
        // One setContent for the whole test (the activity rejects a second), so the
        // host composes whatever ViewModel the holder points at, one per failure id.
        val checker = FakeChecker(UpdateCheckResult.CheckFailed(R.string.update_error_dns))
        val vmHolder = mutableStateOf(UpdateViewModel(checker, FakeInstaller(), UpdateCheckCache()))
        composeRule.setContent {
            WordDrillTheme {
                Surface {
                    val vm by vmHolder
                    UpdateContent(
                        viewModel = vm,
                        onRequestInstallPermission = {},
                    )
                }
            }
        }
        composeRule.waitForIdle()

        listOf(
            R.string.update_error_dns,
            R.string.update_error_interrupted,
            R.string.update_error_generic,
        ).forEach { messageRes ->
            checker.result = UpdateCheckResult.CheckFailed(messageRes)
            composeRule.runOnIdle {
                vmHolder.value = UpdateViewModel(checker, FakeInstaller(), UpdateCheckCache())
            }
            composeRule.runOnIdle { vmHolder.value.checkForUpdate() }
            composeRule.waitForIdle()

            // Background check: no dialog and no banner unless a newer release
            // was found; failures surface only on the manual check path.
            composeRule.onNodeWithText("发现新版本").assertDoesNotExist()
            composeRule.onNodeWithText("无法解析域名", substring = false).assertDoesNotExist()
            composeRule.onNodeWithText("连接被中断，可能是网络限制", substring = false).assertDoesNotExist()
            composeRule.onNodeWithText("无法连接 GitHub", substring = false).assertDoesNotExist()
        }
    }

    @Test
    fun noticeDismiss_removesBanner() {
        val vm = setContentWith(
            UpdateCheckResult.CheckFailed(R.string.update_error_dns),
            FakeInstaller(),
        )
        composeRule.runOnIdle { vm.manualCheck() }
        awaitShown("无法解析域名", substring = false)

        composeRule.onNodeWithContentDescription("关闭").performClick()

        awaitGone("无法解析域名", substring = false)
    }
}
