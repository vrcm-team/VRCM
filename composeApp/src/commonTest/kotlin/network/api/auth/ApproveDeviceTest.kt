package io.github.vrcmteam.vrcm.network.api.auth

import io.github.vrcmteam.vrcm.di.modules.createNetworkJson
import io.github.vrcmteam.vrcm.network.supports.VRCApiException
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.*
import io.ktor.http.content.OutgoingContent
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.test.*

class ApproveDeviceTest {
    @Test
    fun postsUserCodeToApprovalEndpointAndRequiresSuccessResponse() = runBlocking {
        withResponse("""{"success":true}""") { client -> AuthApi(client).approveDevice("F36XWSHN") }
        withResponse("""{"success":false}""") { client ->
            assertFailsWith<IllegalStateException> { AuthApi(client).approveDevice("F36XWSHN") }
        }
    }

    @Test
    fun expiredCodeRemainsAnApiFailure() = runBlocking {
        withResponse("""{"error":{"message":"Device code not found or expired.","status_code":404}}""", HttpStatusCode.NotFound) { client ->
            val error = assertFailsWith<VRCApiException> { AuthApi(client).approveDevice("F36XWSHN") }
            assertEquals(404, error.code)
        }
    }

    private suspend fun withResponse(body: String, status: HttpStatusCode = HttpStatusCode.OK, block: suspend (HttpClient) -> Unit) {
        val client = HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    assertEquals(HttpMethod.Post, request.method)
                    assertEquals("/auth/device/approve", request.url.encodedPath)
                    val content = (request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()
                    assertEquals(Json.parseToJsonElement("""{"userCode":"F36XWSHN"}"""), Json.parseToJsonElement(content))
                    respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
                }
            }
            install(ContentNegotiation) { json(createNetworkJson()) }
        }
        try { block(client) } finally { client.close() }
    }
}
