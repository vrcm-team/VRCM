package io.github.vrcmteam.vrcm.network.api.playermoderation

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
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PlayerChatboxModerationApiTest {
    @Test
    fun pairedOverrideFlowUsesDocumentedRequestsAndPayloads() = runTest {
        val requests = mutableListOf<HttpRequestData>()
        val client = HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    requests += request
                    respond(
                        content = when (request.method) {
                            HttpMethod.Get -> "[${moderationJson("muteChat")}]"
                            HttpMethod.Post -> moderationJson("unmuteChat")
                            else -> SUCCESS_JSON
                        },
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
            }
            defaultRequest { url("https://api.vrchat.cloud/api/1/") }
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
        val api = PlayerChatboxModerationApi(client)

        try {
            val existing = api.getForTarget(TARGET_USER_ID)
            api.remove(TARGET_USER_ID, ChatboxModerationType.MuteChat)
            val created = api.moderate(TARGET_USER_ID, ChatboxModerationType.UnmuteChat)

            assertEquals("muteChat", existing.single().type)
            assertEquals(TARGET_USER_ID, existing.single().targetUserId)
            assertEquals("unmuteChat", created.type)

            assertEquals(HttpMethod.Get, requests[0].method)
            assertEquals("/api/1/auth/user/playermoderations", requests[0].url.encodedPath)
            assertEquals(TARGET_USER_ID, requests[0].url.parameters["targetUserId"])
            assertNull(requests[0].url.parameters["type"])

            assertEquals(HttpMethod.Put, requests[1].method)
            assertEquals("/api/1/auth/user/unplayermoderate", requests[1].url.encodedPath)
            assertEquals(
                """{"moderated":"$TARGET_USER_ID","type":"muteChat"}""",
                requests[1].bodyText(),
            )

            assertEquals(HttpMethod.Post, requests[2].method)
            assertEquals("/api/1/auth/user/playermoderations", requests[2].url.encodedPath)
            assertEquals(
                """{"moderated":"$TARGET_USER_ID","type":"unmuteChat"}""",
                requests[2].bodyText(),
            )
        } finally {
            client.close()
        }
    }

    private fun HttpRequestData.bodyText(): String =
        (body as OutgoingContent.ByteArrayContent).bytes().decodeToString()

    private fun moderationJson(type: String) = """
        {
          "created":"2026-08-31T00:00:00.000Z",
          "id":"pmod_1",
          "sourceDisplayName":"Current User",
          "sourceUserId":"usr_current",
          "targetDisplayName":"Target User",
          "targetUserId":"$TARGET_USER_ID",
          "type":"$type"
        }
    """.trimIndent()

    private companion object {
        const val TARGET_USER_ID = "usr_target"
        const val SUCCESS_JSON =
            """{"success":{"message":"removed","status_code":200}}"""
    }
}
