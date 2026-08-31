package io.github.vrcmteam.vrcm.network.api.avatars

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.ContentType
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
    fun updateAvatarPublicationSendsOnlyTheReleaseStatusAndDecodesTheAvatar() = runBlocking {
        var method: HttpMethod? = null
        var path: String? = null
        var contentType: ContentType? = null
        var body = ""
        val client = HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    val outgoing = request.body as OutgoingContent.ByteArrayContent
                    method = request.method
                    path = request.url.encodedPath
                    contentType = outgoing.contentType
                    body = outgoing.bytes().decodeToString()
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

        val avatar = AvatarsApi(client).updateAvatar(
            avatarId = "avtr_owned",
            update = AvatarUpdateData(releaseStatus = "public"),
        )

        assertEquals(HttpMethod.Put, method)
        assertEquals("/api/1/avatars/avtr_owned", path)
        assertEquals(ContentType.Application.Json, contentType)
        assertEquals("{\"releaseStatus\":\"public\"}", body)
        assertEquals("avtr_owned", avatar.id)
        assertEquals("Avatar", avatar.name)
        assertEquals("usr_owner", avatar.authorId)
        assertEquals("private", avatar.releaseStatus)
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
