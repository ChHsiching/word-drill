package com.github.chsiching.worddrill.update

import com.github.chsiching.worddrill.BuildConfig
import com.github.chsiching.worddrill.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** A published GitHub release, reduced to what the update flow needs. */
data class ReleaseInfo(
    val version: String,
    val notes: String,
    val apkUrl: String,
)

/** The seam the update UI consumes; fakes cover it in tests without network. */
interface UpdateChecker {
    suspend fun checkForUpdate(): UpdateCheckResult
}

sealed interface UpdateCheckResult {
    data class UpdateAvailable(val release: ReleaseInfo) : UpdateCheckResult

    /** Not newer, or newer but carrying no APK asset (nothing actionable). Stay silent. */
    data object UpToDate : UpdateCheckResult

    data class CheckFailed(val messageRes: Int) : UpdateCheckResult
}

/**
 * Fetches /releases/latest and compares its tag_name against the running version.
 * [fetchBody] is the injectable network edge; the real one is a plain HttpURLConnection
 * (the repo deliberately has no HTTP client dependency).
 */
class GitHubUpdateChecker(
    private val currentVersion: String = BuildConfig.VERSION_NAME,
    private val fetchBody: suspend () -> String =
        { withContext(Dispatchers.IO) { httpGetLatestRelease() } },
) : UpdateChecker {

    override suspend fun checkForUpdate(): UpdateCheckResult = try {
        val release = parseLatestRelease(fetchBody())
            ?: return UpdateCheckResult.UpToDate
        if (isNewerVersion(release.version, currentVersion)) {
            UpdateCheckResult.UpdateAvailable(release)
        } else {
            UpdateCheckResult.UpToDate
        }
    } catch (e: Exception) {
        UpdateCheckResult.CheckFailed(mapNetworkError(e))
    }
}

/** DNS failure, connection/TLS reset, everything else — the ticket's three buckets. */
@androidx.annotation.StringRes
internal fun mapNetworkError(e: Exception): Int = when (e) {
    is java.net.UnknownHostException -> R.string.update_error_dns
    is javax.net.ssl.SSLException, is java.net.SocketException ->
        R.string.update_error_interrupted
    else -> R.string.update_error_generic
}

private const val LATEST_RELEASE_API =
    "https://api.github.com/repos/ChHsiching/word-drill/releases/latest"

/**
 * Picks tag_name (v prefix stripped), the release notes body, and the first .apk asset
 * (release.yml publishes worddrill-<tag>.apk). Returns null when the release carries no
 * APK asset; malformed JSON throws JSONException, which the caller maps to an error.
 */
internal fun parseLatestRelease(body: String): ReleaseInfo? {
    val release = JSONObject(body)
    val version = release.optString("tag_name").removePrefix("v").removePrefix("V")
    if (version.isEmpty()) return null
    val assets = release.optJSONArray("assets") ?: return null
    for (i in 0 until assets.length()) {
        val asset = assets.optJSONObject(i) ?: continue
        if (asset.optString("name").endsWith(".apk")) {
            return ReleaseInfo(
                version = version,
                notes = release.optString("body"),
                apkUrl = asset.optString("browser_download_url"),
            )
        }
    }
    return null
}

internal fun httpGetLatestRelease(): String {
    val conn = URL(LATEST_RELEASE_API).openConnection() as HttpURLConnection
    try {
        conn.connectTimeout = 10_000
        conn.readTimeout = 15_000
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        return conn.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
    } finally {
        conn.disconnect()
    }
}
