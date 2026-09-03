package io.github.vrcmteam.vrcm.network.api.invite

import io.github.vrcmteam.vrcm.network.api.invite.data.InviteMessageType
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class InviteMessageSelectionApiTest {
    @Test
    fun selectedSlotsReachInviteAndRequestInviteEndpoints() = runBlocking {
        val requests = mutableListOf<HttpRequestData>()
        val client = testClient { request ->
            requests += request
            "{}"
        }
        val api = InviteApi(client)

        api.inviteUser("usr_friend", "wrld_test:instance", messageSlot = 7)
        api.requestInvite("usr_friend", requestSlot = 4)

        assertEquals(HttpMethod.Post, requests[0].method)
        assertEquals("/api/1/invite/usr_friend", requests[0].url.encodedPath)
        assertEquals("{\"instanceId\":\"wrld_test:instance\",\"messageSlot\":7}", requests[0].bodyText())
        assertEquals(HttpMethod.Post, requests[1].method)
        assertEquals("/api/1/requestInvite/usr_friend", requests[1].url.encodedPath)
        assertEquals("{\"requestSlot\":4}", requests[1].bodyText())

        client.close()
    }

    @Test
    fun messageAndRequestCollectionsUseTheirMatchingServerPaths() = runBlocking {
        val paths = mutableListOf<String>()
        val client = testClient { request ->
            paths += request.url.encodedPath
            val type = request.url.encodedPath.substringAfterLast('/')
            """[{"canBeUpdated":true,"id":"$type-2","message":"Hello","messageType":"$type","remainingCooldownMinutes":0,"slot":2,"updatedAt":"2026-09-01T00:00:00Z"}]"""
        }
        val api = InviteApi(client)

        val invite = api.getInviteMessages("usr_current", InviteMessageType.Message)
        val request = api.getInviteMessages("usr_current", InviteMessageType.Request)

        assertEquals(
            listOf(
                "/api/1/message/usr_current/message",
                "/api/1/message/usr_current/request",
            ),
            paths,
        )
        assertEquals(2, invite.single().slot)
        assertEquals(InviteMessageType.Request, request.single().messageType)

        client.close()
    }

    @Test
    fun invalidSlotsAreRejectedBeforeANetworkRequest() = runBlocking {
        var requestCount = 0
        val client = testClient {
            requestCount++
            "{}"
        }
        val api = InviteApi(client)

        assertFailsWith<IllegalArgumentException> {
            api.inviteUser("usr_friend", "wrld_test:instance", messageSlot = 12)
        }
        assertFailsWith<IllegalArgumentException> {
            api.inviteUser("usr_friend", "instance~region(use)")
        }
        assertFailsWith<IllegalArgumentException> {
            api.requestInvite("usr_friend", requestSlot = -1)
        }
        assertEquals(0, requestCount)

        client.close()
    }

    @Test
    fun malformedMessageCollectionsAreRejectedAtTheApiBoundary() = runBlocking {
        val invalidResponses = listOf(
            """[{"canBeUpdated":true,"id":"request-0","message":"Hello","messageType":"request","remainingCooldownMinutes":0,"slot":0,"updatedAt":"2026-09-01T00:00:00Z"}]""",
            """[{"canBeUpdated":true,"id":"message-12","message":"Hello","messageType":"message","remainingCooldownMinutes":0,"slot":12,"updatedAt":"2026-09-01T00:00:00Z"}]""",
            """[{"canBeUpdated":true,"id":"message-a","message":"Hello","messageType":"message","remainingCooldownMinutes":0,"slot":1,"updatedAt":"2026-09-01T00:00:00Z"},{"canBeUpdated":true,"id":"message-b","message":"Again","messageType":"message","remainingCooldownMinutes":0,"slot":1,"updatedAt":"2026-09-01T00:00:00Z"}]""",
        )

        invalidResponses.forEach { response ->
            val client = testClient { response }
            try {
                assertFailsWith<IllegalStateException> {
                    InviteApi(client).getInviteMessages("usr_current", InviteMessageType.Message)
                }
            } finally {
                client.close()
            }
        }
    }

    private fun testClient(response: (HttpRequestData) -> String) = HttpClient(MockEngine) {
        engine {
            addHandler { request ->
                respond(
                    content = response(request),
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            }
        }
        defaultRequest { url("https://api.vrchat.cloud/api/1/") }
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    private fun HttpRequestData.bodyText(): String =
        (body as OutgoingContent.ByteArrayContent).bytes().decodeToString()
}
