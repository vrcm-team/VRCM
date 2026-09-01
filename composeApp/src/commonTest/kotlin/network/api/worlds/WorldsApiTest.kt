package io.github.vrcmteam.vrcm.network.api.worlds

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WorldsApiTest {
    @Test
    fun publicationEndpointsUseThePublishResourceAndExpectedMethods() = runBlocking {
        val requests = mutableListOf<Pair<HttpMethod, String>>()
        val client = HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    requests += request.method to request.url.encodedPath
                    respond(
                        content = if (request.method == HttpMethod.Get) {
                            """{"canPublish":true}"""
                        } else {
                            ""
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
        val api = WorldsApi(client)

        val status = api.getWorldPublishStatus("wrld_owned")
        api.publishWorld("wrld_owned")
        api.unpublishWorld("wrld_owned")

        assertTrue(status.canPublish)
        assertEquals(
            listOf(
                HttpMethod.Get to "/api/1/worlds/wrld_owned/publish",
                HttpMethod.Put to "/api/1/worlds/wrld_owned/publish",
                HttpMethod.Delete to "/api/1/worlds/wrld_owned/publish",
            ),
            requests,
        )
        client.close()
    }
}
