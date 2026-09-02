package io.github.vrcmteam.vrcm.network.api.invite

import io.github.vrcmteam.vrcm.network.api.invite.data.InviteMessageType
import io.github.vrcmteam.vrcm.network.supports.VRCApiException
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.ContentType
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class InviteMessageApiTest {
    @Test
    fun listUsesTheExactPathForEverySupportedMessageType() = runBlocking {
        val requests = mutableListOf<Pair<HttpMethod, String>>()
        val client = testClient { request ->
            requests += request.method to request.url.encodedPath
            TestResponse("[]")
        }

        try {
            InviteMessageType.entries.forEach { messageType ->
                InviteApi(client).getInviteMessages("usr_current", messageType)
            }

            assertEquals(
                listOf(
                    HttpMethod.Get to "/api/1/message/usr_current/message",
                    HttpMethod.Get to "/api/1/message/usr_current/response",
                    HttpMethod.Get to "/api/1/message/usr_current/request",
                    HttpMethod.Get to "/api/1/message/usr_current/requestResponse",
                ),
                requests,
            )
        } finally {
            client.close()
        }
    }

    @Test
    fun updateSendsOnlyTheMessageAndDecodesTheReturnedCooldownState() = runBlocking {
        var method: HttpMethod? = null
        var path = ""
        var body = ""
        var contentType: ContentType? = null
        val unicodeMessage = "今晚见 👋"
        val client = testClient { request ->
            method = request.method
            path = request.url.encodedPath
            body = request.bodyText()
            contentType = request.body.contentType
            TestResponse(
                """[{
                    "canBeUpdated":false,
                    "id":"msg_3",
                    "message":"今晚见 👋",
                    "messageType":"requestResponse",
                    "remainingCooldownMinutes":60,
                    "slot":3,
                    "updatedAt":"2026-08-31T03:00:00.000Z"
                }]"""
            )
        }

        try {
            val result = InviteApi(client).updateInviteMessage(
                userId = "usr_current",
                messageType = InviteMessageType.RequestResponse,
                slot = 3,
                message = unicodeMessage,
            )

            assertEquals(HttpMethod.Put, method)
            assertEquals("/api/1/message/usr_current/requestResponse/3", path)
            assertEquals(ContentType.Application.Json, contentType)
            assertEquals(
                unicodeMessage,
                Json.parseToJsonElement(body).jsonObject.getValue("message").jsonPrimitive.content,
            )
            assertEquals(unicodeMessage, result.single().message)
            assertEquals(InviteMessageType.RequestResponse, result.single().messageType)
            assertEquals(60, result.single().remainingCooldownMinutes)
            assertFalse(result.single().canBeUpdated)
        } finally {
            client.close()
        }
    }

    @Test
    fun resetUsesDeleteAndReturnsTheRefreshedCollection() = runBlocking {
        var method: HttpMethod? = null
        var path = ""
        val client = testClient { request ->
            method = request.method
            path = request.url.encodedPath
            TestResponse(
                """[{
                    "canBeUpdated":true,
                    "id":"msg_11",
                    "message":"Default",
                    "messageType":"response",
                    "remainingCooldownMinutes":0,
                    "slot":11,
                    "updatedAt":"2026-08-31T03:00:00.000Z"
                }]"""
            )
        }

        try {
            val result = InviteApi(client).resetInviteMessage(
                userId = "usr_current",
                messageType = InviteMessageType.Response,
                slot = 11,
            )

            assertEquals(HttpMethod.Delete, method)
            assertEquals("/api/1/message/usr_current/response/11", path)
            assertEquals("Default", result.single().message)
        } finally {
            client.close()
        }
    }

    @Test
    fun updateRejectsInvalidInputBeforeSendingAndPreservesRateLimitErrors() = runBlocking {
        var requestCount = 0
        val client = testClient { request ->
            requestCount++
            TestResponse(
                body = """{"error":{"message":"Please wait","status_code":429}}""",
                status = HttpStatusCode.TooManyRequests,
            )
        }

        try {
            val api = InviteApi(client)
            assertFailsWith<IllegalArgumentException> {
                api.updateInviteMessage("usr_current", InviteMessageType.Message, -1, "Hello")
            }
            assertFailsWith<IllegalArgumentException> {
                api.updateInviteMessage("usr_current", InviteMessageType.Message, 12, "Hello")
            }
            assertFailsWith<IllegalArgumentException> {
                api.updateInviteMessage("usr_current", InviteMessageType.Message, 0, "   ")
            }
            assertFailsWith<IllegalArgumentException> {
                api.updateInviteMessage(
                    "usr_current",
                    InviteMessageType.Message,
                    0,
                    "x".repeat(InviteApi.MAX_INVITE_MESSAGE_CODE_POINTS + 1),
                )
            }
            assertEquals(0, requestCount)

            val error = assertFailsWith<VRCApiException> {
                api.updateInviteMessage("usr_current", InviteMessageType.Message, 0, "Hello")
            }
            assertEquals(429, error.code)
            assertEquals(1, requestCount)
        } finally {
            client.close()
        }
    }

    @Test
    fun unicodeLimitCountsSurrogatePairsAsSingleCodePoints() = runBlocking {
        var requestCount = 0
        val client = testClient {
            requestCount++
            TestResponse("[]")
        }
        val emoji = "\uD83D\uDE00"

        try {
            InviteApi(client).updateInviteMessage(
                "usr_current",
                InviteMessageType.Message,
                0,
                emoji.repeat(64),
            )
            assertEquals(1, requestCount)

            assertFailsWith<IllegalArgumentException> {
                InviteApi(client).updateInviteMessage(
                    "usr_current",
                    InviteMessageType.Message,
                    0,
                    emoji.repeat(65),
                )
            }
            assertEquals(1, requestCount)
            assertEquals(1, "\uD83D".inviteMessageCodePointCount())
            assertEquals(1, emoji.inviteMessageCodePointCount())
        } finally {
            client.close()
        }
    }

    private fun testClient(
        response: (HttpRequestData) -> TestResponse,
    ) = HttpClient(MockEngine) {
        engine {
            addHandler { request ->
                response(request).let { configured ->
                    respond(
                        content = configured.body,
                        status = configured.status,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
            }
        }
        defaultRequest { url("https://api.vrchat.cloud/api/1/") }
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    private fun HttpRequestData.bodyText(): String =
        (body as OutgoingContent.ByteArrayContent).bytes().decodeToString()

    private data class TestResponse(
        val body: String,
        val status: HttpStatusCode = HttpStatusCode.OK,
    )
}
