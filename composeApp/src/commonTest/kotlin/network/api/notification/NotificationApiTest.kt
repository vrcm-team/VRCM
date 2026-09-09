package io.github.vrcmteam.vrcm.network.api.notification

import io.github.vrcmteam.vrcm.presentation.screens.home.data.NotificationItemData
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.readRemaining
import kotlinx.io.readByteArray
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NotificationApiTest {
    @Test
    fun currentNotificationDeletionUsesDeleteEndpoint() = runBlocking {
        val requests = mutableListOf<Pair<HttpMethod, String>>()
        val client = notificationClient(onRequest = { method, path -> requests += method to path })

        NotificationApi(client).deleteNotificationV2("not_boop")

        assertEquals(listOf(HttpMethod.Delete to "/api/1/notifications/not_boop"), requests)
        client.close()
    }

    @Test
    fun legacyNotificationHideKeepsPutEndpoint() = runBlocking {
        val requests = mutableListOf<Pair<HttpMethod, String>>()
        val client = notificationClient(onRequest = { method, path -> requests += method to path })

        NotificationApi(client).deleteNotification("not_friend_request")

        assertEquals(
            listOf(HttpMethod.Put to "/api/1/auth/user/notifications/not_friend_request/hide"),
            requests,
        )
        client.close()
    }

    @Test
    fun pipelineMarkAsReadUsesPostSeeEndpoint() = runBlocking {
        val requests = mutableListOf<Pair<HttpMethod, String>>()
        val client = notificationClient(onRequest = { method, path -> requests += method to path })

        NotificationApi(client).markPipelineNotificationAsRead("not_unread")

        assertEquals(
            listOf(HttpMethod.Post to "/api/1/notifications/not_unread/see"),
            requests,
        )
        client.close()
    }

    @Test
    fun legacyMarkAsReadKeepsPutSeeEndpoint() = runBlocking {
        val requests = mutableListOf<Pair<HttpMethod, String>>()
        val client = notificationClient(onRequest = { method, path -> requests += method to path })

        NotificationApi(client).markLegacyNotificationAsRead("not_friend_request")

        assertEquals(
            listOf(HttpMethod.Put to "/api/1/auth/user/notifications/not_friend_request/see"),
            requests,
        )
        client.close()
    }

    @Test
    fun invitePhotoResponseUsesOfficialMultipartContract() = runBlocking {
        var capturedPath = ""
        var capturedBody = ""
        val client = HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    capturedPath = request.url.encodedPath
                    val body = request.body as MultiPartFormDataContent
                    val channel = ByteChannel()
                    body.writeTo(channel)
                    capturedBody = channel.readRemaining().readByteArray().decodeToString()
                    respond(
                        content = "{\"id\":\"not_response\"}",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
            }
            defaultRequest { url("https://api.vrchat.cloud/api/1/") }
        }

        io.github.vrcmteam.vrcm.network.api.invite.InviteApi(client).respondInviteWithPhoto(
            notificationId = "not_response",
            responseSlot = 3,
            imageBytes = byteArrayOf(
                0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
            ),
        )

        assertEquals("/api/1/invite/not_response/response/photo", capturedPath)
        assertTrue(capturedBody.contains("name=data"))
        assertTrue(capturedBody.contains("{\"responseSlot\":3}"))
        assertTrue(capturedBody.contains("name=image; filename=\"image.png\""))
        assertTrue(capturedBody.contains("Content-Type: image/png"))
        client.close()
    }

    @Test
    fun invitePhotoResponseRejectsInvalidPngAndSlotBeforeSending() = runBlocking {
        var requestCount = 0
        val client = HttpClient(MockEngine) {
            engine {
                addHandler {
                    requestCount++
                    respond("{}", HttpStatusCode.OK)
                }
            }
        }

        kotlin.test.assertFailsWith<IllegalArgumentException> {
            io.github.vrcmteam.vrcm.network.api.invite.InviteApi(client)
                .respondInviteWithPhoto("not_response", 12, byteArrayOf())
        }
        kotlin.test.assertEquals(0, requestCount)
        client.close()
    }

    @Test
    fun notificationResponseUsesStructuredJsonForServerSuppliedValues() = runBlocking {
        val requests = mutableListOf<Pair<HttpMethod, String>>()
        var capturedRequest: HttpRequestData? = null
        val client = notificationClient(
            onRequest = { method, path -> requests += method to path },
            onRequestData = { capturedRequest = it },
        )

        NotificationApi(client).responseNotification(
            "not_action",
            NotificationItemData.ActionData(
                data = "approve \"owner\"\nnext",
                type = "accept",
            ),
        )

        val request = checkNotNull(capturedRequest)
        val outgoingBody = request.body as OutgoingContent.ByteArrayContent
        val body = outgoingBody.bytes().decodeToString()
        val json = Json.parseToJsonElement(body).jsonObject
        assertEquals(listOf(HttpMethod.Post to "/api/1/notifications/not_action/respond"), requests)
        assertEquals(setOf("responseData", "responseType"), json.keys)
        assertEquals("approve \"owner\"\nnext", json.getValue("responseData").jsonPrimitive.content)
        assertEquals("accept", json.getValue("responseType").jsonPrimitive.content)
        assertEquals(true, outgoingBody.contentType?.toString()?.startsWith("application/json"))
        client.close()
    }

    private fun notificationClient(
        onRequest: (HttpMethod, String) -> Unit,
        onRequestData: (HttpRequestData) -> Unit = {},
    ) = HttpClient(MockEngine) {
        engine {
            addHandler { request ->
                onRequest(request.method, request.url.encodedPath)
                onRequestData(request)
                respond(
                    content = """{"success":{"message":"ok","status_code":200}}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            }
        }
        defaultRequest { url("https://api.vrchat.cloud/api/1/") }
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    }
}
