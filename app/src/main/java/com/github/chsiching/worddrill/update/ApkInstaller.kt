package com.github.chsiching.worddrill.update

/**
 * The download/install edge consumed by [UpdateViewModel]: download the APK via the
 * system DownloadManager (progress shows in its notification) and, on completion,
 * launch the package installer. Fakes cover it in tests without touching the network
 * or the system downloader.
 */
interface ApkInstaller {
    fun canInstallApk(): Boolean

    /**
     * @param onFailed invoked when the system download fails; the APK install intent
     * fires by itself when the download completes.
     */
    fun downloadAndInstall(apkUrl: String, version: String, onFailed: () -> Unit)
}
