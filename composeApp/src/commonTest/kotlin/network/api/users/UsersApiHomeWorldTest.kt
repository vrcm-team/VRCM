package io.github.vrcmteam.vrcm.network.api.users

import io.github.vrcmteam.vrcm.di.modules.createNetworkJson
import io.github.vrcmteam.vrcm.network.api.users.data.UpdateUserInfoData
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class UsersApiHomeWorldTest {
    @Test
    fun setHomeWorldOnlySendsTheRequestedLocation() = runBlocking {
        val request = captureUpdate(UpdateUserInfoData(homeLocation = "wrld_home"))

        assertEquals(HttpMethod.Put, request.method)
        assertEquals("/users/usr_owner", request.path)
        assertEquals("{\n    \"homeLocation\": \"wrld_home\"\n}", request.body)
    }

    @Test
    fun resetHomeWorldKeepsTheEmptyLocationInTheRequest() = runBlocking {
        val request = captureUpdate(UpdateUserInfoData(homeLocation = ""))

        assertEquals("{\n    \"homeLocation\": \"\"\n}", request.body)
    }

    private suspend fun captureUpdate(data: UpdateUserInfoData): CapturedRequest {
        lateinit var captured: CapturedRequest
        val client = HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    captured = CapturedRequest(
                        method = request.method,
                        path = request.url.encodedPath,
                        body = request.bodyText(),
                    )
                    respond(
                        content = "not used",
                        status = HttpStatusCode.InternalServerError,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
            }
            install(ContentNegotiation) { json(createNetworkJson()) }
        }
        try {
            runCatching { UsersApi(client).updateUserInfo("usr_owner", data) }
            return captured
        } finally {
            client.close()
        }
    }

    private fun HttpRequestData.bodyText(): String =
        (body as OutgoingContent.ByteArrayContent).bytes().decodeToString()

    private data class CapturedRequest(
        val method: HttpMethod,
        val path: String,
        val body: String,
    )
}
