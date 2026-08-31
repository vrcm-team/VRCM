package io.github.vrcmteam.vrcm.network.api.playermoderation

import io.github.vrcmteam.vrcm.network.api.playermoderation.data.PlayerModerationType
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
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PlayerModerationApiTest {
    @Test
    fun getUsesOptionalTypeFilterAndDecodesRecords() = runBlocking {
        val requests = mutableListOf<Pair<String?, String>>()
        val client = testClient { request ->
            requests += request.url.parameters["type"] to request.url.encodedPath
            """[{"id":"pmod_1","targetUserId":"usr_target","type":"mute","future":true}]"""
        }

        try {
            val all = PlayerModerationApi(client).get()
            val muted = PlayerModerationApi(client).get(PlayerModerationType.Mute)

            assertEquals(
                listOf(
                    null to "/api/1/auth/user/playermoderations",
                    "mute" to "/api/1/auth/user/playermoderations",
                ),
                requests,
            )
            assertEquals("usr_target", all.single().targetUserId)
            assertEquals("mute", muted.single().type)
        } finally {
            client.close()
        }
    }

    @Test
    fun removeUsesPutEndpointAndModeratedRequestField() = runBlocking {
        var method: HttpMethod? = null
        var path = ""
        var body = ""
        val client = testClient { request ->
            method = request.method
            path = request.url.encodedPath
            body = request.bodyText()
            """{"success":{"message":"ok","status_code":200}}"""
        }

        try {
            PlayerModerationApi(client).remove("usr_target", PlayerModerationType.InteractOff)

            assertEquals(HttpMethod.Put, method)
            assertEquals("/api/1/auth/user/unplayermoderate", path)
            assertEquals(
                buildJsonObject {
                    put("moderated", "usr_target")
                    put("type", "interactOff")
                },
                Json.parseToJsonElement(body),
            )
        } finally {
            client.close()
        }
    }

    @Test
    fun removeRejectsInvalidTargetBeforeSendingRequest() = runBlocking {
        var requests = 0
        val client = testClient {
            requests++
            """{"success":{"message":"ok","status_code":200}}"""
        }

        try {
            assertFailsWith<IllegalArgumentException> {
                PlayerModerationApi(client).remove("usr_target/path", PlayerModerationType.Block)
            }
            assertEquals(0, requests)
        } finally {
            client.close()
        }
    }

    private fun testClient(responseBody: (HttpRequestData) -> String) = HttpClient(MockEngine) {
        engine {
            addHandler { request ->
                respond(
                    content = responseBody(request),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            }
        }
        defaultRequest { url("https://api.vrchat.cloud/api/1/") }
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    }

    private fun HttpRequestData.bodyText(): String =
        (body as OutgoingContent.ByteArrayContent).bytes().decodeToString()
}
