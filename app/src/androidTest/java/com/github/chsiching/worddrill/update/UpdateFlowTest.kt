package com.github.chsiching.worddrill.update

import androidx.compose.material3.Surface
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
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
        val vm = UpdateViewModel(FakeChecker(checkerResult), installer)
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

    @Test
    fun newerRelease_showsDialogWithVersionAndNotes() {
        setContentWith(UpdateCheckResult.UpdateAvailable(release), FakeInstaller())

        composeRule.onNodeWithText("发现新版本").assertIsDisplayed()
        composeRule.onNodeWithText("版本 0.3.0", substring = false).assertIsDisplayed()
        composeRule.onNodeWithText("修复与改进").assertIsDisplayed()
        composeRule.onNodeWithText("立即更新").assertIsDisplayed()
        composeRule.onNodeWithText("暂不更新").assertIsDisplayed()
    }

    @Test
    fun updateNow_withInstallPermission_closesDialogAndStartsDownload() {
        val installer = FakeInstaller(canInstall = true)
        setContentWith(UpdateCheckResult.UpdateAvailable(release), installer)

        composeRule.onNodeWithText("立即更新").performClick()
        composeRule.waitForIdle()

        assertThat(installer.downloads).containsExactly(release.apkUrl to release.version)
        composeRule.onNodeWithText("发现新版本").assertDoesNotExist()
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

        composeRule.onNodeWithText("立即更新").performClick()
        composeRule.waitForIdle()

        // The dialog stays while the system permission screen is up; nothing downloads.
        assertThat(permissionRequests).isEqualTo(1)
        assertThat(installer.downloads).isEmpty()
        composeRule.onNodeWithText("发现新版本").assertIsDisplayed()

        // User granted in settings and came back; production wires this to the
        // activity result, tests call it directly.
        installer.canInstall = true
        composeRule.runOnIdle { vm.onInstallPermissionResult() }
        composeRule.waitForIdle()

        assertThat(installer.downloads).containsExactly(release.apkUrl to release.version)
        composeRule.onNodeWithText("发现新版本").assertDoesNotExist()
    }

    @Test
    fun later_dismissesDialog_andDoesNotReappearOnRecheck() {
        val vm = setContentWith(UpdateCheckResult.UpdateAvailable(release), FakeInstaller())

        composeRule.onNodeWithText("暂不更新").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("发现新版本").assertDoesNotExist()

        // Same session: a re-check must not reopen the dialog.
        vm.checkForUpdate()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("发现新版本").assertDoesNotExist()
    }

    @Test
    fun upToDate_showsNoUi() {
        setContentWith(UpdateCheckResult.UpToDate, FakeInstaller())

        composeRule.onNodeWithText("发现新版本").assertDoesNotExist()
        composeRule.onNodeWithText("无法连接 GitHub").assertDoesNotExist()
    }

    @Test
    fun checkFailures_showInlineMappedMessages() {
        val cases = listOf(
            R.string.update_error_dns to "无法解析域名",
            R.string.update_error_interrupted to "连接被中断，可能是网络限制",
            R.string.update_error_generic to "无法连接 GitHub",
        )
        for ((errorRes, message) in cases) {
            setContentWith(UpdateCheckResult.CheckFailed(errorRes), FakeInstaller())

            composeRule.onNodeWithText(message, substring = false).assertIsDisplayed()
            composeRule.onNodeWithText("发现新版本").assertDoesNotExist()
        }
    }

    @Test
    fun noticeDismiss_removesBanner() {
        setContentWith(
            UpdateCheckResult.CheckFailed(R.string.update_error_dns),
            FakeInstaller(),
        )
        composeRule.onNodeWithText("无法解析域名", substring = false).assertIsDisplayed()

        composeRule.onNodeWithContentDescription("关闭").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("无法解析域名", substring = false).assertDoesNotExist()
    }
}
