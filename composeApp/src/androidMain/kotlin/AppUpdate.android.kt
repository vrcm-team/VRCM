package io.github.vrcmteam.vrcm

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.core.content.FileProvider
import io.github.vrcmteam.vrcm.core.shared.AppConst
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.head
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

internal suspend fun downloadAndInstallAppUpdate(
    context: Context,
    tagName: String,
    downloadUrls: List<String>,
    onProgress: (Float?) -> Unit,
): Result<Unit> = runCatching {
    val source = downloadUrls.firstOrNull { it.endsWith(".apk", ignoreCase = true) }
        ?: error("This release has no Android APK")
    onProgress(null)
    val fastestMirror = findFastestMirror(source) ?: error("No available APK download source")
    val appContext = context.applicationContext
    val fileName = "VRCM-${tagName.replace(Regex("[^A-Za-z0-9._-]"), "_")}.apk"
    val downloadDirectory = requireNotNull(appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)) {
        "External download directory is unavailable"
    }
    val target = File(downloadDirectory, fileName)
    check(!target.exists() || target.delete()) { "Unable to replace the previous APK" }
    val manager = appContext.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    val request = DownloadManager.Request(Uri.parse(mirrorUrl(fastestMirror, source)))
        .setTitle("VRCM $tagName")
        .setMimeType(APK_MIME_TYPE)
        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        .setAllowedOverMetered(true)
        .setAllowedOverRoaming(false)
        .setDestinationInExternalFilesDir(appContext, Environment.DIRECTORY_DOWNLOADS, fileName)
    val downloadId = manager.enqueue(request)
    appContext.getSharedPreferences(UPDATE_PREFS, Context.MODE_PRIVATE).edit()
        .putLong(DOWNLOAD_ID, downloadId)
        .putString(DOWNLOAD_FILE, target.path)
        .apply()
    awaitDownload(manager, downloadId, onProgress)
    verifyApk(appContext, target)
    openInstaller(appContext, target)
}

private suspend fun findFastestMirror(source: String): String? {
    val client = HttpClient {
        expectSuccess = false
        install(HttpTimeout) {
            requestTimeoutMillis = 8_000
            connectTimeoutMillis = 5_000
            socketTimeoutMillis = 8_000
        }
    }
    return try {
        coroutineScope {
            val winners = Channel<String>(Channel.UNLIMITED)
            downloadMirrors.forEach { prefix ->
                launch(Dispatchers.IO) {
                    runCatching {
                        client.head(mirrorUrl(prefix, source)) {
                            header(HttpHeaders.UserAgent, "VRCM/${AppConst.APP_VERSION}")
                        }
                    }.getOrNull()?.takeIf { it.status.isSuccess() }?.let { winners.send(prefix) }
                }
            }
            val winner = withTimeoutOrNull(8_000) { winners.receive() }
            coroutineContext.cancelChildren()
            winners.close()
            winner
        }
    } finally {
        client.close()
    }
}

private suspend fun awaitDownload(
    manager: DownloadManager,
    downloadId: Long,
    onProgress: (Float?) -> Unit,
) {
    while (true) {
        val state = withContext(Dispatchers.IO) {
            manager.query(DownloadManager.Query().setFilterById(downloadId)).use { cursor ->
                check(cursor.moveToFirst()) { "Download task disappeared" }
                Triple(
                    cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)),
                    cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)),
                    cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)),
                )
            }
        }
        onProgress(if (state.third > 0) (state.second.toFloat() / state.third).coerceIn(0f, 1f) else null)
        when (state.first) {
            DownloadManager.STATUS_SUCCESSFUL -> return
            DownloadManager.STATUS_FAILED -> error("APK download failed")
        }
        delay(500)
    }
}

private fun verifyApk(context: Context, apk: File) {
    check(apk.isFile && apk.length() > 0) { "Downloaded APK is empty" }
    val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        PackageManager.GET_SIGNING_CERTIFICATES
    } else {
        @Suppress("DEPRECATION")
        PackageManager.GET_SIGNATURES
    }
    val archive = context.packageManager.getPackageArchiveInfo(apk.path, flags)
    check(archive?.packageName == context.packageName) { "Downloaded APK package does not match" }
    val installed = context.packageManager.getPackageInfo(context.packageName, flags)
    check(signatures(archive).contentDeepEquals(signatures(installed))) { "Downloaded APK signature does not match" }
}

private fun signatures(info: PackageInfo): Array<ByteArray> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        info.signingInfo?.apkContentsSigners.orEmpty().map { it.toByteArray() }.toTypedArray()
    } else {
        @Suppress("DEPRECATION")
        info.signatures.orEmpty().map { it.toByteArray() }.toTypedArray()
    }

private fun openInstaller(context: Context, apk: File) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
    context.startActivity(
        Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, APK_MIME_TYPE)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        },
    )
}

class UpdateDownloadReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != DownloadManager.ACTION_NOTIFICATION_CLICKED) return
        val preferences = context.getSharedPreferences(UPDATE_PREFS, Context.MODE_PRIVATE)
        val expectedId = preferences.getLong(DOWNLOAD_ID, -1L)
        val clickedIds = intent.getLongArrayExtra(DownloadManager.EXTRA_NOTIFICATION_CLICK_DOWNLOAD_IDS)
        if (expectedId < 0 || clickedIds?.contains(expectedId) != true) return
        val apk = preferences.getString(DOWNLOAD_FILE, null)?.let(::File) ?: return
        runCatching {
            verifyApk(context, apk)
            openInstaller(context, apk)
        }
    }
}

private fun mirrorUrl(prefix: String, source: String) = if (prefix.isEmpty()) source else prefix + source
private val downloadMirrors = listOf(
    "",
    "https://ghfast.top/",
    "https://git.yylx.win/",
    "https://gh-proxy.com/",
    "https://ghfile.geekertao.top/",
    "https://gh-proxy.net/",
    "https://ghm.078465.xyz/",
    "https://gitproxy.127731.xyz/",
    "https://jiashu.1win.eu.org/",
    "https://github.tbedu.top/",
)
private const val APK_MIME_TYPE = "application/vnd.android.package-archive"
private const val UPDATE_PREFS = "vrcm_update"
private const val DOWNLOAD_ID = "download_id"
private const val DOWNLOAD_FILE = "download_file"
