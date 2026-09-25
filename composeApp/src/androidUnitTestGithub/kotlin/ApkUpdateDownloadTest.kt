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
        try {
            client.downloadFromFastestSource(
                source = "https://github.com/vrcm-team/VRCM/releases/download/9.9.9/VRCM-v9.9.9.apk",
                target = target,
                verify = { check(it.readBytes().contentEquals(apk)) { "Not the APK" } },
                onProgress = { _, _ -> },
            )

            assertContentEquals(apk, target.readBytes())
        } finally {
            target.delete()
            client.close()
        }
    }
}

private const val APK_CONTENT_TYPE = "application/vnd.android.package-archive"
