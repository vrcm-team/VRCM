package io.github.vrcmteam.vrcm.network.api.files

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class FileApiAvatarGalleryTest {
    @Test
    fun avatarGalleryQueryUsesAvatarScopedTagAndPagination() = runBlocking {
        var query = emptyMap<String, List<String>>()
        val client = HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    query = request.url.parameters.entries().associate { it.key to it.value }
                    respond(
                        content = "[]",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
            }
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }

        FileApi(client).getAvatarGalleryFiles("avtr_model", n = 200, offset = -4)

        assertEquals(mapOf(
            "tag" to listOf("avatargallery"),
            "galleryId" to listOf("avtr_model"),
            "n" to listOf("100"),
            "offset" to listOf("0"),
        ), query)
        client.close()
    }
}
