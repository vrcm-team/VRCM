package io.github.vrcmteam.vrcm.network.api.status

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class VrchatStatusApiTest {
    @Test
    fun fetchesPublicStatuspageAndDecodesStatus() = runBlocking {
        var requestedUrl = ""
        val client = HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    requestedUrl = request.url.toString()
                    respond(
                        content = """{"status":{"indicator":"minor","description":"Partial outage"}}""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
            }
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }

        val result = VrchatStatusApi(client).fetchStatus().getOrThrow()

        assertEquals("https://status.vrchat.com/api/v2/status.json", requestedUrl)
        assertEquals("minor", result.status.indicator)
        assertEquals("Partial outage", result.status.description)
        client.close()
    }
}
