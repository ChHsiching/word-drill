package com.github.chsiching.worddrill.update

import com.github.chsiching.worddrill.R
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

class GitHubUpdateCheckerTest {

    private val currentVersion = "0.2.1"

    private fun latestReleaseJson(
        tag: String = "v0.3.0",
        body: String = "修复与改进",
        assets: List<Pair<String, String>> = listOf(
            "worddrill-v0.3.0.apk" to "https://example.com/worddrill-v0.3.0.apk",
        ),
    ): String {
        val assetJson = assets.joinToString(",") { (name, url) ->
            """{"name":"$name","browser_download_url":"$url"}"""
        }
        return """{"tag_name":"$tag","body":"$body","assets":[$assetJson]}"""
    }

    private fun checker(body: String): UpdateChecker =
        GitHubUpdateChecker(currentVersion = currentVersion, fetchBody = { body })

    @Test
    fun newerRelease_returnsUpdateAvailableWithParsedFields() = runTest {
        val result = checker(latestReleaseJson()).checkForUpdate()

        val available = result as UpdateCheckResult.UpdateAvailable
        assertThat(available.release.version).isEqualTo("0.3.0")
        assertThat(available.release.notes).isEqualTo("修复与改进")
        assertThat(available.release.apkUrl)
            .isEqualTo("https://example.com/worddrill-v0.3.0.apk")
    }

    @Test
    fun sameVersion_returnsUpToDate() = runTest {
        val result = checker(latestReleaseJson(tag = "v0.2.1")).checkForUpdate()

        assertThat(result).isEqualTo(UpdateCheckResult.UpToDate)
    }

    @Test
    fun olderRelease_returnsUpToDate() = runTest {
        val result = checker(latestReleaseJson(tag = "v0.1.9")).checkForUpdate()

        assertThat(result).isEqualTo(UpdateCheckResult.UpToDate)
    }

    @Test
    fun releaseWithoutApkAsset_returnsUpToDate() = runTest {
        // A newer tag with no APK asset is not actionable; the flow stays silent.
        val result = checker(
            latestReleaseJson(assets = listOf("sources.zip" to "https://example.com/s.zip"))
        ).checkForUpdate()

        assertThat(result).isEqualTo(UpdateCheckResult.UpToDate)
    }

    @Test
    fun dnsFailure_mapsToDnsErrorMessage() = runTest {
        val result = failingChecker(java.net.UnknownHostException("api.github.com")).checkForUpdate()

        assertThat(result)
            .isEqualTo(UpdateCheckResult.CheckFailed(R.string.update_error_dns))
    }

    @Test
    fun connectionOrTlsReset_mapsToInterruptedMessage() = runTest {
        val tls = failingChecker(
            javax.net.ssl.SSLException("Connection reset by peer during handshake")
        ).checkForUpdate()
        val reset = failingChecker(java.net.SocketException("Connection reset")).checkForUpdate()

        assertThat(tls)
            .isEqualTo(UpdateCheckResult.CheckFailed(R.string.update_error_interrupted))
        assertThat(reset)
            .isEqualTo(UpdateCheckResult.CheckFailed(R.string.update_error_interrupted))
    }

    @Test
    fun otherFailures_mapToGenericMessage() = runTest {
        val io = failingChecker(java.io.IOException("unexpected stream state")).checkForUpdate()
        val json = failingChecker(org.json.JSONException("bad body")).checkForUpdate()

        assertThat(io)
            .isEqualTo(UpdateCheckResult.CheckFailed(R.string.update_error_generic))
        assertThat(json)
            .isEqualTo(UpdateCheckResult.CheckFailed(R.string.update_error_generic))
    }

    private fun failingChecker(error: Exception): UpdateChecker =
        GitHubUpdateChecker(currentVersion = currentVersion, fetchBody = { throw error })
}
