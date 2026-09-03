package io.github.vrcmteam.vrcm.network.api.files

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FileApiUrlTest {
    @Test
    fun imageUrlUsesUploadedFileVersion() {
        assertEquals(
            "https://api.vrchat.cloud/api/1/image/file_avatar/4/1024",
            FileApi.imageUrl(fileId = "file_avatar", fileVersion = 4),
        )
    }

    @Test
    fun avatarGalleryRefreshUsesAvatarScopedQuery() = runBlocking {
        var request: HttpRequestData? = null
        val client = HttpClient(MockEngine) {
            engine {
                addHandler {
                    request = it
                    respond(
                        content = "[]",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
            }
            install(ContentNegotiation) { json(Json) }
        }

        FileApi(client).getAvatarGalleryFiles("avtr_owner")

        val url = requireNotNull(request).url.toString()
        assertTrue(url.contains("tag=avatargallery"))
        assertTrue(url.contains("galleryId=avtr_owner"))
        assertTrue(url.contains("n=100"))
        assertTrue(url.contains("offset=0"))
        client.close()
    }
}
