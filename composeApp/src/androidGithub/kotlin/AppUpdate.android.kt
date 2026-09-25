package io.github.vrcmteam.vrcm

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import androidx.core.content.FileProvider
import io.github.vrcmteam.vrcm.core.shared.AppConst
import io.github.vrcmteam.vrcm.service.isNewerVersion
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.head
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.Url
import io.ktor.http.contentLength
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.utils.io.readAvailable
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * GitHub 渠道的应用内更新：从 GitHub 或镜像下载新版本 APK，确认是本应用、同一签名后交给系统安装器覆盖安装。
 * 安装包放在应用私有的缓存目录，校验过的文件不会被其他应用替换。
 */
internal class ApkUpdateInstaller(context: Context) : AppUpdateInstaller {
    private val context = context.applicationContext

    override suspend fun download(
        tagName: String,
        downloadUrls: List<String>,
        onProgress: (downloadedBytes: Long, totalBytes: Long?) -> Unit,
    ): Result<DownloadedAppUpdate> = withContext(Dispatchers.IO) {
        try {
            val source = downloadUrls.firstOrNull { it.endsWith(".apk", ignoreCase = true) }
                ?: error("This release has no Android APK")
            val directory = File(context.cacheDir, UPDATE_DIRECTORY)
            check(directory.isDirectory || directory.mkdirs()) { "Unable to create $directory" }
            val apk = File(directory, apkFileName(tagName))
            // 只留这个版本的安装包
            directory.listFiles()?.forEach { if (it != apk) it.delete() }
            // 上次下好但在安装器里取消了的，校验通过就直接用
            if (apk.isFile && runCatching { verifyApk(apk) }.isSuccess) {
                onProgress(apk.length(), apk.length())
            } else {
                HttpClient(OkHttp) {
                    install(HttpTimeout) {
                        connectTimeoutMillis = CONNECT_TIMEOUT_MILLIS
                        // 这么久收不到数据就当这个源断了，换下一个
                        socketTimeoutMillis = STALL_TIMEOUT_MILLIS
                    }
                }.use { client -> client.downloadFromFastestSource(source, apk, ::verifyApk, onProgress) }
            }
            Result.success(DownloadedAppUpdate(apk.path))
        } catch (cause: CancellationException) {
            throw cause
        } catch (cause: Exception) {
            Result.failure(cause)
        }
    }

    override fun install(update: DownloadedAppUpdate): Result<Unit> = runCatching {
        val apk = File(update.path)
        // 缓存可能被系统清理掉，这时要重新下载
        check(apk.isFile) { "Downloaded APK no longer exists" }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        context.startActivity(
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, APK_MIME_TYPE)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            },
        )
    }

    /**
     * 缓存目录里只留比当前版本新、还没装的安装包：安装器可能还在读它，用户也可能稍后再装。
     * 另外删掉 1.1.2、1.1.3 用 DownloadManager 下到外部存储的安装包；文件没了，系统会自己清掉对应的下载记录。
     */
    override fun deleteObsoletePackages() {
        File(context.cacheDir, UPDATE_DIRECTORY).listFiles()?.forEach { file ->
            val tagName = file.name.removeSurrounding(APK_NAME_PREFIX, APK_NAME_SUFFIX)
            val pendingInstall = tagName != file.name && isNewerVersion(tagName, AppConst.APP_VERSION)
            if (!pendingInstall) file.delete()
        }
        val legacyRecord = context.getSharedPreferences(LEGACY_UPDATE_PREFS, Context.MODE_PRIVATE)
        // 用过旧版应用内更新才有这份记录；没有就不碰外部存储，免得凭空建出下载目录
        if (legacyRecord.all.isEmpty()) return
        context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?.listFiles { file -> file.name.startsWith(APK_NAME_PREFIX) && file.name.endsWith(APK_NAME_SUFFIX) }
            ?.forEach(File::delete)
        context.deleteSharedPreferences(LEGACY_UPDATE_PREFS)
    }

    /** 下到的必须是本应用、并且和已安装的版本同一签名，否则系统不能覆盖安装。 */
    private fun verifyApk(apk: File) {
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
}

/**
 * 把 [source] 下载到 [target]。GitHub 直连在国内常常很慢，所以同时探测直连和各个镜像，按响应先后逐个下载；
 * 某个源中途断开、长时间没有数据，或者下到的文件通不过 [verify]，就换下一个源。全部失败时抛出汇总各源原因的异常。
 */
internal suspend fun HttpClient.downloadFromFastestSource(
    source: String,
    target: File,
    verify: (File) -> Unit,
    onProgress: (downloadedBytes: Long, totalBytes: Long?) -> Unit,
): Unit = coroutineScope {
    val candidates = Channel<String>(Channel.UNLIMITED)
    val probing = launch {
        downloadMirrors.map { prefix ->
            launch {
                val url = mirrorUrl(prefix, source)
                if (servesFile(url)) candidates.send(url)
            }
        }.joinAll()
        candidates.close()
    }
    val failures = mutableListOf<String>()
    for (url in candidates) {
        try {
            downloadTo(url, target, onProgress)
            verify(target)
            probing.cancel()
            return@coroutineScope
        } catch (cause: CancellationException) {
            throw cause
        } catch (cause: Exception) {
            target.delete()
            failures += "${Url(url).host}: ${cause.message ?: cause::class.simpleName}"
        }
    }
    throw IOException(
        if (failures.isEmpty()) "No APK download source is reachable"
        else "Every APK download source failed (${failures.joinToString("; ")})",
    )
}

/**
 * 下载源能否提供文件。有的镜像用不了时也回 200，内容却是一张网页（例如人机验证页），这种不算。
 * 最先响应的源会马上开始下载，超时只影响慢的源，所以给得宽，免得慢网络下把能用的源全部排除。
 */
private suspend fun HttpClient.servesFile(url: String): Boolean = try {
    val response = withTimeoutOrNull(PROBE_TIMEOUT_MILLIS) {
        head(url) { header(HttpHeaders.UserAgent, USER_AGENT) }
    }
    response != null && response.status.isSuccess() && response.contentType()?.match(ContentType.Text.Html) != true
} catch (cause: CancellationException) {
    throw cause
} catch (_: Exception) {
    false
}

private suspend fun HttpClient.downloadTo(
    url: String,
    target: File,
    onProgress: (downloadedBytes: Long, totalBytes: Long?) -> Unit,
) = prepareGet(url) { header(HttpHeaders.UserAgent, USER_AGENT) }.execute { response ->
    check(response.status.isSuccess()) { "HTTP ${response.status.value}" }
    val total = response.contentLength()?.takeIf { it > 0 }
    val body = response.bodyAsChannel()
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var downloaded = 0L
    var reported = 0L
    onProgress(0, total)
    target.outputStream().use { output ->
        while (true) {
            val read = body.readAvailable(buffer)
            if (read < 0) break
            output.write(buffer, 0, read)
            downloaded += read
            if (downloaded - reported >= PROGRESS_STEP_BYTES) {
                reported = downloaded
                onProgress(downloaded, total)
            }
        }
    }
    onProgress(downloaded, total)
}

private fun signatures(info: PackageInfo): Array<ByteArray> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        info.signingInfo?.apkContentsSigners.orEmpty().map { it.toByteArray() }.toTypedArray()
    } else {
        @Suppress("DEPRECATION")
        info.signatures.orEmpty().map { it.toByteArray() }.toTypedArray()
    }

/** 安装包按版本命名，启动清理时据此判断它是否已经装过。 */
private fun apkFileName(tagName: String) =
    APK_NAME_PREFIX + tagName.replace(Regex("[^A-Za-z0-9._-]"), "_") + APK_NAME_SUFFIX

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
private const val UPDATE_DIRECTORY = "updates"
private const val APK_NAME_PREFIX = "VRCM-"
private const val APK_NAME_SUFFIX = ".apk"

/** 1.1.2、1.1.3 的应用内更新在这里记下 DownloadManager 的下载任务。 */
private const val LEGACY_UPDATE_PREFS = "vrcm_update"
private const val USER_AGENT = "VRCM/${AppConst.APP_VERSION}"
private const val PROBE_TIMEOUT_MILLIS = 30_000L
private const val CONNECT_TIMEOUT_MILLIS = 10_000L
private const val STALL_TIMEOUT_MILLIS = 20_000L
private const val PROGRESS_STEP_BYTES = 64 * 1024L
