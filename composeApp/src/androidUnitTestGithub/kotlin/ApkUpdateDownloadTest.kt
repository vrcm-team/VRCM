package io.github.vrcmteam.vrcm

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ApkUpdateDownloadTest {
    @Test
    fun movesToTheNextSourceWhenTheFastestOneDoesNotServeTheApk() = runBlocking {
        val apk = byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0x14, 0x00)
        val directDownloadStarted = CompletableDeferred<Unit>()
        val client = HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    when (request.url.host) {
                        // 直连最先响应探测，真正下载时却回了一张 200 的人机验证网页
                        "github.com" -> if (request.method == HttpMethod.Head) {
                            respond("", headers = headersOf(HttpHeaders.ContentType, APK_CONTENT_TYPE))
                        } else {
                            directDownloadStarted.complete(Unit)
                            respond("<html>challenge</html>", headers = headersOf(HttpHeaders.ContentType, "text/html"))
                        }
                        // 镜像等直连开始下载后才响应探测，保证它排在直连后面
                        "ghfast.top" -> {
                            if (request.method == HttpMethod.Head) directDownloadStarted.await()
                            respond(apk, headers = headersOf(HttpHeaders.ContentType, APK_CONTENT_TYPE))
                        }
                        else -> respondError(HttpStatusCode.BadGateway)
                    }
                }
            }
        }
        val target = File.createTempFile("update", ".apk")
        var verified = false
        val progress = mutableListOf<Long>()
        try {
            client.downloadFromFastestSource(
                source = "https://github.com/vrcm-team/VRCM/releases/download/9.9.9/VRCM-v9.9.9.apk",
                target = target,
                verify = {
                    check(it.readBytes().contentEquals(apk)) { "Not the APK" }
                    verified = true
                },
                onProgress = { downloaded, _ ->
                    if (downloaded == apk.size.toLong()) assertTrue(verified)
                    progress += downloaded
                },
            )

            assertContentEquals(apk, target.readBytes())
            assertEquals(1, progress.count { it == apk.size.toLong() })
        } finally {
            target.delete()
            client.close()
        }
    }

    @Test
    fun stopsRetryingWhenDownloadedApkHasTheWrongIdentity() = runBlocking {
        val apk = byteArrayOf(0x50, 0x4B, 0x03, 0x04)
        val directProbeFinished = CompletableDeferred<Unit>()
        val downloadedHosts = mutableListOf<String>()
        val client = HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    when (request.url.host) {
                        "github.com" -> if (request.method == HttpMethod.Head) {
                            directProbeFinished.complete(Unit)
                            respond("", headers = headersOf(HttpHeaders.ContentType, APK_CONTENT_TYPE))
                        } else {
                            downloadedHosts += request.url.host
                            respond(apk, headers = headersOf(HttpHeaders.ContentType, APK_CONTENT_TYPE))
                        }
                        else -> {
                            if (request.method == HttpMethod.Head) directProbeFinished.await()
                            else downloadedHosts += request.url.host
                            respond(apk, headers = headersOf(HttpHeaders.ContentType, APK_CONTENT_TYPE))
                        }
                    }
                }
            }
        }
        val target = File.createTempFile("update", ".apk")
        try {
            assertFailsWith<ApkIdentityException> {
                client.downloadFromFastestSource(
                    source = "https://github.com/vrcm-team/VRCM/releases/download/9.9.9/VRCM-v9.9.9.apk",
                    target = target,
                    verify = { throw ApkIdentityException("wrong package") },
                    onProgress = { _, _ -> },
                )
            }
            assertEquals(1, downloadedHosts.size)
        } finally {
            target.delete()
            client.close()
        }
    }
}

private const val APK_CONTENT_TYPE = "application/vnd.android.package-archive"
