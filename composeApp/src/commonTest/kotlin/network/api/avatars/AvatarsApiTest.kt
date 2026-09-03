package io.github.vrcmteam.vrcm.network.api.avatars

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
import io.github.vrcmteam.vrcm.network.api.avatars.data.AvatarUpdateData
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class AvatarsApiTest {
    @Test
    fun updateAvatarOnlySendsChangedFieldsAndAllowsEmptyDescription() = runBlocking {
        var body = ""
        val client = HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    body = (request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()
                    respond(
                        content = avatarJson(),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
            }
            defaultRequest { url("https://api.vrchat.cloud/api/1/") }
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }

        AvatarsApi(client).updateAvatar(
            avatarId = "avtr_owned",
            update = AvatarUpdateData(description = ""),
        )

        assertEquals("{\"description\":\"\"}", body)
        client.close()
    }

    @Test
    fun selectAvatarUsesPutAndParsesPartialSelectionResponse() = runBlocking {
        var method: HttpMethod? = null
        var path: String? = null
        val client = HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    method = request.method
                    path = request.url.encodedPath
                    respond(
                        content = """{"currentAvatar":"avtr_selected"}""",
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

        val result = AvatarsApi(client).selectAvatar("avtr_selected")

        assertEquals(HttpMethod.Put, method)
        assertEquals("/api/1/avatars/avtr_selected/select", path)
        assertEquals("avtr_selected", result.currentAvatar)
        client.close()
    }

    @Test
    fun deleteImpostorUsesDeleteAndAcceptsAnEmptySuccessBody() = runBlocking {
        var method: HttpMethod? = null
        var path: String? = null
        val client = HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    method = request.method
                    path = request.url.encodedPath
                    respond(content = "", status = HttpStatusCode.OK)
                }
            }
            defaultRequest { url("https://api.vrchat.cloud/api/1/") }
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }

        AvatarsApi(client).deleteImpostor("avtr_owned")

        assertEquals(HttpMethod.Delete, method)
        assertEquals("/api/1/avatars/avtr_owned/impostor", path)
        client.close()
    }

    private fun avatarJson() = """{
        "id":"avtr_owned",
        "name":"Avatar",
        "description":"",
        "authorId":"usr_owner",
        "authorName":"Owner",
        "imageUrl":"",
        "releaseStatus":"private",
        "tags":[],
        "unityPackages":[]
    }"""

}
