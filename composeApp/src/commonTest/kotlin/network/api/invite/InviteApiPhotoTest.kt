package io.github.vrcmteam.vrcm.network.api.invite

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.readRemaining
import kotlinx.coroutines.runBlocking
import kotlinx.io.readByteArray
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class InviteApiPhotoTest {
    @Test
    fun photoInviteUsesJsonDataAndPngImageParts() = runBlocking {
        var body = ""
        var path = ""
        val client = HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    path = request.url.encodedPath
                    val multipart = request.body as MultiPartFormDataContent
                    val channel = ByteChannel()
                    multipart.writeTo(channel)
                    body = channel.readRemaining().readByteArray().decodeToString()
                    respond(
                        content = "{}",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
            }
            defaultRequest { url("https://api.vrchat.cloud/api/1/") }
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }

        InviteApi(client).inviteUserWithPhoto(
            userId = "usr_friend",
            instanceId = "12345~region(use)",
            imageBytes = PNG,
            messageSlot = 4,
        )

        assertEquals("/api/1/invite/usr_friend/photo", path)
        assertContains(body, "name=data")
        assertContains(body, "application/json")
        assertContains(body, "\"instanceId\":\"12345~region(use)\"")
        assertContains(body, "\"messageSlot\":4")
        assertContains(body, "name=image")
        assertContains(body, "image/png")
        assertContains(body, "filename=\"image.png\"")
        client.close()
    }

    @Test
    fun photoInviteRejectsInvalidInputBeforeNetworkCall() = runBlocking {
        var requestCount = 0
        val client = HttpClient(MockEngine) {
            engine {
                addHandler {
                    requestCount++
                    respond("{}", HttpStatusCode.OK)
                }
            }
        }
        val api = InviteApi(client)
        assertFailsWith<IllegalArgumentException> {
            api.inviteUserWithPhoto("usr_friend", "offline", PNG)
        }
        assertFailsWith<IllegalArgumentException> {
            api.inviteUserWithPhoto("usr_friend", "private", PNG)
        }
        assertFailsWith<IllegalArgumentException> {
            api.inviteUserWithPhoto("usr_friend", "wrld_world:12345", PNG)
        }
        assertEquals(0, requestCount)
        client.close()
    }

    private companion object {
        val PNG = byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
            0x01, 0x02,
        )
    }
}
