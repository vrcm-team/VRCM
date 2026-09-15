package io.github.vrcmteam.vrcm.network.api.users

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class UsersApiRepresentedGroupTest {
    @Test
    fun representedGroupUsesTheDedicatedEndpoint() = runBlocking {
        var capturedRequest: HttpRequestData? = null
        val client = testClient { request ->
            capturedRequest = request
            """
                {
                  "id":"membership_showcase",
                  "groupId":"grp_showcase",
                  "name":"Displayed group",
                  "isRepresenting":true
                }
            """.trimIndent()
        }

        try {
            val group = UsersApi(client).getRepresentedGroup("usr_target")

            val request = checkNotNull(capturedRequest)
            assertEquals(HttpMethod.Get, request.method)
            assertEquals("/users/usr_target/groups/represented", request.url.encodedPath)
            assertEquals("grp_showcase", group?.groupId)
            assertEquals(true, group?.isRepresenting)
        } finally {
            client.close()
        }
    }

    @Test
    fun missingRepresentedGroupIsTreatedAsEmpty() = runBlocking {
        val client = HttpClient(MockEngine) {
            engine {
                addHandler {
                    respond(
                        content = "",
                        status = HttpStatusCode.NotFound,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
            }
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }

        try {
            assertNull(UsersApi(client).getRepresentedGroup("usr_target"))
        } finally {
            client.close()
        }
    }

    private fun testClient(responseBody: (HttpRequestData) -> String) =
        HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    respond(
                        content = responseBody(request),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
            }
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
}
