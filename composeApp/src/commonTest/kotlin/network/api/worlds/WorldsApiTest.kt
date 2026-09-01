package io.github.vrcmteam.vrcm.network.api.worlds

import io.github.vrcmteam.vrcm.network.api.worlds.data.WorldUpdateData
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
import kotlin.test.Test
import kotlin.test.assertEquals

class WorldsApiTest {
    @Test
    fun updateWorldOnlySendsImageUrlAndParsesServerThumbnail() = runBlocking {
        var request: HttpRequestData? = null
        var body = ""
        val client = HttpClient(MockEngine) {
            engine {
                addHandler { captured ->
                    request = captured
                    body = (captured.body as OutgoingContent.ByteArrayContent)
                        .bytes()
                        .decodeToString()
                    respond(
                        content = worldJson(),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
            }
            defaultRequest { url("https://api.vrchat.cloud/api/1/") }
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }

        val updated = WorldsApi(client).updateWorld(
            worldId = "wrld_owned",
            update = WorldUpdateData(imageUrl = "https://api.vrchat.cloud/api/1/file/file_image/2/file"),
        )

        assertEquals(HttpMethod.Put, request?.method)
        assertEquals("/api/1/worlds/wrld_owned", request?.url?.encodedPath)
        assertEquals(
            "{\"imageUrl\":\"https://api.vrchat.cloud/api/1/file/file_image/2/file\"}",
            body,
        )
        assertEquals("https://cdn.example/thumbnail.png", updated.thumbnailImageUrl)
        client.close()
    }

    private fun worldJson() = """{
        "authorId":"usr_owner","authorName":"Owner","capacity":32,
        "created_at":"2026-01-01T00:00:00Z","description":"World",
        "favorites":1,"featured":false,"heat":1,"id":"wrld_owned",
        "imageUrl":"https://cdn.example/original.png","labsPublicationDate":"",
        "name":"Owned World","namespace":null,"organization":"vrchat",
        "popularity":1,"privateOccupants":0,"publicOccupants":0,
        "publicationDate":"","recommendedCapacity":16,"releaseStatus":"private",
        "tags":[],"thumbnailImageUrl":"https://cdn.example/thumbnail.png",
        "udonProducts":[],"unityPackages":[],"updated_at":"2026-01-02T00:00:00Z",
        "version":2,"visits":1
    }""".trimIndent()
}
