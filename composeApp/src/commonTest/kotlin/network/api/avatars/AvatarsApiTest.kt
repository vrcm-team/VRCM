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
import io.github.vrcmteam.vrcm.testing.currentUserJson
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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
    fun updateAvatarSendsStyleIdsAndTheCompleteTagSet() = runBlocking {
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
            update = AvatarUpdateData(
                tags = listOf("content_horror", "author_tag_dance"),
                primaryStyle = "avst_primary",
                secondaryStyle = "",
            ),
        )

        assertEquals(
            """{"tags":["content_horror","author_tag_dance"],"primaryStyle":"avst_primary","secondaryStyle":""}""",
            body,
        )
        client.close()
    }

    @Test
    fun getAvatarStylesUsesTheServiceEndpointAndParsesIds() = runBlocking {
        var path: String? = null
        val client = HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    path = request.url.encodedPath
                    respond(
                        content = """[{"id":"avst_one","styleName":"Anime"}]""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
            }
            defaultRequest { url("https://api.vrchat.cloud/api/1/") }
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }

        val styles = AvatarsApi(client).getAvatarStyles()

        assertEquals("/api/1/avatarStyles", path)
        assertEquals("avst_one", styles.single().id)
        assertEquals("Anime", styles.single().styleName)
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

    @Test
    fun selectFallbackAvatarUsesPutWithoutBodyAndParsesCurrentUser() = runBlocking {
        var method: HttpMethod? = null
        var path: String? = null
        var body: OutgoingContent? = null
        val client = HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    method = request.method
                    path = request.url.encodedPath
                    body = request.body
                    respond(
                        content = currentUserJson(
                            userId = "usr_current",
                            fallbackAvatar = "avtr_fallback",
                        ),
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

        val result = AvatarsApi(client).selectFallbackAvatar("avtr_fallback")

        assertEquals(HttpMethod.Put, method)
        assertEquals("/api/1/avatars/avtr_fallback/selectFallback", path)
        assertTrue(body is OutgoingContent.NoContent)
        assertEquals("usr_current", result.id)
        assertEquals("avtr_fallback", result.fallbackAvatar)
        client.close()
    }

    @Test
    fun deleteAvatarUsesDeleteAndParsesAuthoritativeResponse() = runBlocking {
        var method: HttpMethod? = null
        var path: String? = null
        val client = HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    method = request.method
                    path = request.url.encodedPath
                    respond(
                        content = avatarJson(releaseStatus = "hidden"),
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

        val result = AvatarsApi(client).deleteAvatar("avtr_owned")

        assertEquals(HttpMethod.Delete, method)
        assertEquals("/api/1/avatars/avtr_owned", path)
        assertEquals("avtr_owned", result.id)
        assertEquals("usr_owner", result.authorId)
        assertEquals("hidden", result.releaseStatus)
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

    @Test
    fun impostorCreationUsesEnqueueAndReadsAuthoritativeQueueData() = runBlocking {
        val requests = mutableListOf<Pair<HttpMethod, String>>()
        val client = HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    requests += request.method to request.url.encodedPath
                    when (request.method) {
                        HttpMethod.Post -> respond(
                            content = """{
                                "created_at":"2026-09-03T00:00:00Z",
                                "id":"service_1",
                                "progress":[],
                                "requesterUserId":"usr_owner",
                                "state":"queued",
                                "subjectId":"avtr_owned",
                                "subjectType":"avatar",
                                "type":"avatar-impostor",
                                "updated_at":"2026-09-03T00:00:00Z"
                            }""".trimIndent(),
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, "application/json"),
                        )
                        else -> respond(
                            content = """{"estimatedServiceDurationSeconds":125}""",
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, "application/json"),
                        )
                    }
                }
            }
            defaultRequest { url("https://api.vrchat.cloud/api/1/") }
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }

        val api = AvatarsApi(client)
        val status = api.enqueueImpostor("avtr_owned")
        val queue = api.getImpostorQueueStats()

        assertEquals(
            listOf(
                HttpMethod.Post to "/api/1/avatars/avtr_owned/impostor/enqueue",
                HttpMethod.Get to "/api/1/avatars/impostor/queue/stats",
            ),
            requests,
        )
        assertEquals("service_1", status.id)
        assertEquals("queued", status.state)
        assertEquals("avtr_owned", status.subjectId)
        assertEquals(125, queue.estimatedServiceDurationSeconds)
        assertTrue(requests.none { it.first == HttpMethod.Delete })
        client.close()
    }

    private fun avatarJson(releaseStatus: String = "private") = """{
        "id":"avtr_owned",
        "name":"Avatar",
        "description":"",
        "authorId":"usr_owner",
        "authorName":"Owner",
        "imageUrl":"",
        "releaseStatus":"$releaseStatus",
        "tags":[],
        "unityPackages":[]
    }"""

}
