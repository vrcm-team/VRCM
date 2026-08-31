package io.github.vrcmteam.vrcm.network.api.avatars

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
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

class AvatarModerationApiTest {
    @Test
    fun getAvatarModerationsUsesTheAuthenticatedUserEndpoint() = runBlocking {
        var method: HttpMethod? = null
        var path: String? = null
        val client = testClient { request ->
            method = request.method
            path = request.url.encodedPath
            respond(
                content = """[
                    {
                        "avatarModerationType":"block",
                        "created":"2026-01-01T00:00:00.000Z",
                        "targetAvatarId":"avtr_blocked"
                    }
                ]""",
                status = HttpStatusCode.OK,
                headers = jsonHeaders,
            )
        }

        val result = AvatarModerationApi(client).getAvatarModerations()

        assertEquals(HttpMethod.Get, method)
        assertEquals("/api/1/auth/user/avatarmoderations", path)
        assertEquals("block", result.single().avatarModerationType)
        assertEquals("avtr_blocked", result.single().targetAvatarId)
        client.close()
    }

    @Test
    fun blockAvatarPostsTheRequiredModerationPayload() = runBlocking {
        var method: HttpMethod? = null
        var path: String? = null
        var body = ""
        val client = testClient { request ->
            method = request.method
            path = request.url.encodedPath
            body = (request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()
            respond(
                content = """{
                    "avatarModerationType":"block",
                    "created":1788105600000,
                    "targetAvatarId":"avtr_target"
                }""",
                status = HttpStatusCode.OK,
                headers = jsonHeaders,
            )
        }

        val result = AvatarModerationApi(client).blockAvatar("avtr_target")

        assertEquals(HttpMethod.Post, method)
        assertEquals("/api/1/auth/user/avatarmoderations", path)
        assertEquals(
            buildJsonObject {
                put("avatarModerationType", "block")
                put("targetAvatarId", "avtr_target")
            },
            Json.parseToJsonElement(body),
        )
        assertEquals("avtr_target", result.targetAvatarId)
        client.close()
    }

    @Test
    fun unblockAvatarDeletesTheMatchingBlockModeration() = runBlocking {
        var method: HttpMethod? = null
        var path: String? = null
        var targetAvatarId: String? = null
        var moderationType: String? = null
        val client = testClient { request ->
            method = request.method
            path = request.url.encodedPath
            targetAvatarId = request.url.parameters["targetAvatarId"]
            moderationType = request.url.parameters["avatarModerationType"]
            respond(
                content = """{"ok":true}""",
                status = HttpStatusCode.OK,
                headers = jsonHeaders,
            )
        }

        AvatarModerationApi(client).unblockAvatar("avtr_target")

        assertEquals(HttpMethod.Delete, method)
        assertEquals("/api/1/auth/user/avatarmoderations", path)
        assertEquals("avtr_target", targetAvatarId)
        assertEquals("block", moderationType)
        client.close()
    }

    private fun testClient(
        handler: suspend MockRequestHandleScope.(
            io.ktor.client.request.HttpRequestData,
        ) -> io.ktor.client.request.HttpResponseData,
    ): HttpClient = HttpClient(MockEngine) {
        engine { addHandler(handler) }
        defaultRequest { url("https://api.vrchat.cloud/api/1/") }
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    }

    private companion object {
        val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")
    }
}
