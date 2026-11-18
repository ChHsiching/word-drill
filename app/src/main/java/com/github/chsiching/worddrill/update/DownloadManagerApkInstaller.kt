package com.github.chsiching.worddrill.update

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.github.chsiching.worddrill.R
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Real [ApkInstaller]: DownloadManager enqueues the APK into the app's external files
 * dir (no storage permission needed) and reports progress in its system notification;
 * on completion a registered receiver fires the ACTION_VIEW install intent with a
 * FileProvider content URI — the plain file path would trip FileUriExposedException
 * and be unreadable to the installer on API 24+.
 */
@Singleton
class DownloadManagerApkInstaller @Inject constructor(
    @ApplicationContext private val context: Context,
) : ApkInstaller {

    private var downloadId: Long = -1
    private var completionReceiver: BroadcastReceiver? = null

    override fun canInstallApk(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            context.packageManager.canRequestPackageInstalls()

    override fun downloadAndInstall(apkUrl: String, version: String, onFailed: () -> Unit) {
        val request = DownloadManager.Request(Uri.parse(apkUrl))
            .setTitle(context.getString(R.string.update_notification_title, version))
            .setMimeType(APK_MIME)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(
                context,
                android.os.Environment.DIRECTORY_DOWNLOADS,
                "worddrill-$version.apk",
            )
        val manager = context.getSystemService(DownloadManager::class.java) ?: return onFailed()
        val id = manager.enqueue(request)

        // A previous interrupted download's receiver would now be orphaned; drop it.
        unregisterCompletionReceiver()
        downloadId = id
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                if (intent.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return
                if (intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1) != id) return
                unregisterCompletionReceiver()
                when (queryStatus(id)) {
                    DownloadManager.STATUS_SUCCESSFUL -> launchInstaller(localFileOf(id))
                    else -> onFailed()
                }
            }
        }
        completionReceiver = receiver
        androidx.core.content.ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    private fun queryStatus(id: Long): Int {
        val manager = context.getSystemService(DownloadManager::class.java)
        val query = DownloadManager.Query().setFilterById(id)
        manager?.query(query)?.use { cursor ->
            if (cursor.moveToFirst()) {
                return cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            }
        }
        return DownloadManager.STATUS_FAILED
    }

    private fun localFileOf(id: Long): File? {
        val manager = context.getSystemService(DownloadManager::class.java)
        val query = DownloadManager.Query().setFilterById(id)
        manager?.query(query)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val uri = Uri.parse(
                    cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI))
                )
                return File(uri.path ?: return null)
            }
        }
        return null
    }

    private fun launchInstaller(apk: File?) {
        val file = apk ?: return
        val contentUri = FileProvider.getUriForFile(context, "$packageName.fileProvider", file)
        val install = Intent(Intent.ACTION_VIEW)
            .setDataAndType(contentUri, APK_MIME)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            // Broadcast context: the install activity must not need our task stack.
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(install)
    }

    private fun unregisterCompletionReceiver() {
        completionReceiver?.let { context.unregisterReceiver(it) }
        completionReceiver = null
    }

    private val packageName: String get() = context.packageName

    companion object {
        private const val APK_MIME = "application/vnd.android.package-archive"

        /** Intent for the unknown-app-sources settings page of this app. */
        fun installPermissionIntent(context: Context): Intent =
            Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}"),
            )
    }
}
