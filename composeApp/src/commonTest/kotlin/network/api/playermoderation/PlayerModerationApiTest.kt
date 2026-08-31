package io.github.vrcmteam.vrcm.network.api.playermoderation

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlayerModerationApiTest {
    @Test
    fun getAllRequestsTheCompleteRecordCollectionWithoutFilters() = runTest {
        lateinit var capturedRequest: HttpRequestData
        val client = HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    capturedRequest = request
                    respond(
                        content = """
                            [{
                              "created":"2026-08-31T00:00:00.000Z",
                              "id":"pmod_1",
                              "sourceDisplayName":"Current User",
                              "sourceUserId":"usr_current",
                              "targetDisplayName":"Target User",
                              "targetUserId":"usr_target",
                              "type":"mute"
                            },{
                              "sourceDisplayName":"Current User",
                              "sourceUserId":"usr_current",
                              "targetDisplayName":"Target Without Metadata",
                              "targetUserId":"usr_target_without_metadata",
                              "type":"futureType"
                            }]
                        """.trimIndent(),
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

        try {
            val result = PlayerModerationApi(client).getAll()

            assertEquals("/api/1/auth/user/playermoderations", capturedRequest.url.encodedPath)
            assertTrue(capturedRequest.url.parameters.isEmpty())
            assertEquals("pmod_1", result.first().id)
            assertEquals("mute", result.first().type)
            assertEquals("", result.last().id)
            assertEquals("", result.last().created)
            assertEquals("futureType", result.last().type)
        } finally {
            client.close()
        }
    }
}
