package io.github.vrcmteam.vrcm.network.api.worlds

import io.github.vrcmteam.vrcm.network.api.worlds.data.WorldUpdateData
import io.github.vrcmteam.vrcm.presentation.screens.world.data.WorldProfileVo
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.client.request.HttpRequestData
import io.ktor.http.content.OutgoingContent
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

    @Test
    fun updateWorldOnlySendsChangedFieldsAndUsesUrlListForAllowedDomains() = runBlocking {
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
                        content = worldJson(urlListJson = "[\"https://example.com\"]"),
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
            update = WorldUpdateData(
                description = "",
                tags = emptyList(),
                urlList = listOf("https://example.com"),
            ),
        )

        assertEquals(HttpMethod.Put, request?.method)
        assertEquals("/api/1/worlds/wrld_owned", request?.url?.encodedPath)
        assertEquals(
            "{\"description\":\"\",\"tags\":[],\"urlList\":[\"https://example.com\"]}",
            body,
        )
        assertEquals(listOf("https://example.com"), updated.urlList)
        client.close()
    }

    @Test
    fun getWorldByIdAcceptsNullAllowedDomainsAndMapsToEmptyList() = runBlocking {
        val client = HttpClient(MockEngine) {
            engine {
                addHandler {
                    respond(
                        content = worldJson(urlListJson = "null"),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
            }
            defaultRequest { url("https://api.vrchat.cloud/api/1/") }
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }

        val world = WorldsApi(client).getWorldById("wrld_owned")

        assertEquals(null, world.urlList)
        assertEquals(emptyList(), WorldProfileVo(world).allowedDomains)
        client.close()
    }

    private fun worldJson(urlListJson: String? = null) = """{
        "authorId":"usr_owner","authorName":"Owner","capacity":32,
        "created_at":"2026-01-01T00:00:00Z","description":"World",
        "favorites":1,"featured":false,"heat":1,"id":"wrld_owned",
        "imageUrl":"https://cdn.example/original.png","labsPublicationDate":"",
        "name":"Owned World","namespace":null,"organization":"vrchat",
        "popularity":1,"privateOccupants":0,"publicOccupants":0,
        "publicationDate":"","recommendedCapacity":16,"releaseStatus":"private",
        "tags":[],"thumbnailImageUrl":"https://cdn.example/thumbnail.png",
        "udonProducts":[],"unityPackages":[],"updated_at":"2026-01-02T00:00:00Z",
        "version":2,"visits":1${urlListJson?.let { ",\"urlList\":$it" }.orEmpty()}
    }""".trimIndent()
}
