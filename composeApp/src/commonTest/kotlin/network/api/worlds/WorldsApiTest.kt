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
import io.ktor.http.headersOf
import io.ktor.http.content.OutgoingContent
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class WorldsApiTest {
    @Test
    fun updateWorldOnlySendsChangedFieldsAndUsesUrlListForAllowedDomains() = runBlocking {
        var method: HttpMethod? = null
        var path: String? = null
        var body = ""
        val client = HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    method = request.method
                    path = request.url.encodedPath
                    body = (request.body as OutgoingContent.ByteArrayContent)
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
            update = WorldUpdateData(
                description = "",
                tags = emptyList(),
                urlList = listOf("https://example.com"),
            ),
        )

        assertEquals(HttpMethod.Put, method)
        assertEquals("/api/1/worlds/wrld_owned", path)
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

    private fun worldJson(urlListJson: String = "[\"https://example.com\"]") = """{
        "authorId":"usr_owner",
        "authorName":"Owner",
        "capacity":16,
        "created_at":"2026-01-01T00:00:00.000Z",
        "description":"",
        "favorites":1,
        "featured":false,
        "heat":0,
        "id":"wrld_owned",
        "imageUrl":"https://example.com/world.png",
        "labsPublicationDate":"none",
        "name":"World",
        "namespace":null,
        "organization":"vrchat",
        "popularity":0,
        "publicationDate":"none",
        "recommendedCapacity":8,
        "releaseStatus":"private",
        "tags":[],
        "thumbnailImageUrl":null,
        "udonProducts":[],
        "unityPackages":[],
        "updated_at":"2026-01-02T00:00:00.000Z",
        "version":2,
        "visits":1,
        "urlList":$urlListJson
    }"""
}
