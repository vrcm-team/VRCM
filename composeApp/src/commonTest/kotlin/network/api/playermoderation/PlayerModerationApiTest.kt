package io.github.vrcmteam.vrcm.network.api.playermoderation

import io.github.vrcmteam.vrcm.core.shared.AccountSessionToken
import io.github.vrcmteam.vrcm.network.api.playermoderation.data.PlayerModerationData
import io.github.vrcmteam.vrcm.network.api.playermoderation.data.PlayerModerationType
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PlayerModerationApiTest {
    @Test
    fun targetReadUsesAccountCollectionAndFiltersBySourceAndTarget() = runTest {
        lateinit var capturedRequest: HttpRequestData
        val client = HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    capturedRequest = request
                    respond(
                        content = "[" +
                            moderationJson("mute") + "," +
                            moderationJson("block", targetUserId = OTHER_TARGET_USER_ID) + "," +
                            moderationJson("interactOff", sourceUserId = OTHER_SOURCE_USER_ID) +
                            "]",
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

        try {
            val result = PlayerModerationApi(client).getForTarget(
                sessionToken = CURRENT_SESSION,
                targetUserId = TARGET_USER_ID,
            )

            assertTrue(capturedRequest.url.parameters.isEmpty())
            assertEquals(listOf("mute"), result.map { it.type })
        } finally {
            client.close()
        }
    }

    @Test
    fun concurrentTargetReadsForOneSessionShareOneHttpRequest() = runTest {
        val requestCount = atomic(0)
        val requestStarted = CompletableDeferred<Unit>()
        val releaseResponse = CompletableDeferred<Unit>()
        val client = HttpClient(MockEngine) {
            engine {
                addHandler {
                    requestCount.incrementAndGet()
                    requestStarted.complete(Unit)
                    releaseResponse.await()
                    respond(
                        content = "[" +
                            moderationJson("mute") + "," +
                            moderationJson("block", targetUserId = OTHER_TARGET_USER_ID) +
                            "]",
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
        val api = PlayerModerationApi(client)

        try {
            val reads = List(4) { index ->
                async {
                    api.getForTarget(
                        sessionToken = CURRENT_SESSION,
                        targetUserId = if (index % 2 == 0) {
                            TARGET_USER_ID
                        } else {
                            OTHER_TARGET_USER_ID
                        },
                    )
                }
            }
            requestStarted.await()
            repeat(10) { yield() }
            releaseResponse.complete(Unit)

            reads.awaitAll()
            assertEquals(1, requestCount.value)
        } finally {
            client.close()
        }
    }

    @Test
    fun getAllRequestsTheCompleteRecordCollectionWithoutFilters() = runTest {
        lateinit var capturedRequest: HttpRequestData
        val client = HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    capturedRequest = request
                    respond(
                        content = """
                            [{
                              "created":"2026-08-31T00:00:00.000Z",
                              "id":"pmod_1",
                              "sourceDisplayName":"Current User",
                              "sourceUserId":"usr_current",
                              "targetDisplayName":"Target User",
                              "targetUserId":"usr_target",
                              "type":"mute"
                            },{
                              "sourceDisplayName":"Current User",
                              "sourceUserId":"usr_current",
                              "targetDisplayName":"Target Without Metadata",
                              "targetUserId":"usr_target_without_metadata",
                              "type":"futureType"
                            }]
                        """.trimIndent(),
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

        try {
            val result = PlayerModerationApi(client).getAll()

            assertEquals("/api/1/auth/user/playermoderations", capturedRequest.url.encodedPath)
            assertTrue(capturedRequest.url.parameters.isEmpty())
            assertEquals("pmod_1", result.first().id)
            assertEquals("mute", result.first().type)
            assertEquals("", result.last().id)
            assertEquals("", result.last().created)
            assertEquals("futureType", result.last().type)
        } finally {
            client.close()
        }
    }

    @Test
    fun getUsesOptionalTypeFilterAndDecodesRecords() = runTest {
        val requests = mutableListOf<Pair<String?, String>>()
        val client = cleanupTestClient { request ->
            requests += request.url.parameters["type"] to request.url.encodedPath
            """[{"id":"pmod_1","targetUserId":"usr_target","type":"mute","future":true}]"""
        }

        try {
            val all = PlayerModerationApi(client).get()
            val muted = PlayerModerationApi(client).get(PlayerModerationType.Mute)

            assertEquals(
                listOf(
                    null to "/api/1/auth/user/playermoderations",
                    "mute" to "/api/1/auth/user/playermoderations",
                ),
                requests,
            )
            assertEquals("usr_target", all.single().targetUserId)
            assertEquals("mute", muted.single().type)
        } finally {
            client.close()
        }
    }

    @Test
    fun removeUsesPutEndpointAndModeratedRequestField() = runTest {
        var method: HttpMethod? = null
        var path = ""
        var body = ""
        val client = cleanupTestClient { request ->
            method = request.method
            path = request.url.encodedPath
            body = request.bodyText()
            """{"success":{"message":"ok","status_code":200}}"""
        }

        try {
            PlayerModerationApi(client).remove("usr_target", PlayerModerationType.InteractOff)

            assertEquals(HttpMethod.Put, method)
            assertEquals("/api/1/auth/user/unplayermoderate", path)
            assertEquals(
                buildJsonObject {
                    put("moderated", "usr_target")
                    put("type", "interactOff")
                },
                Json.parseToJsonElement(body),
            )
        } finally {
            client.close()
        }
    }

    @Test
    fun removeRejectsInvalidTargetBeforeSendingRequest() = runTest {
        var requests = 0
        val client = cleanupTestClient {
            requests++
            """{"success":{"message":"ok","status_code":200}}"""
        }

        try {
            assertFailsWith<IllegalArgumentException> {
                PlayerModerationApi(client).remove("usr_target/path", PlayerModerationType.Block)
            }
            assertEquals(0, requests)
        } finally {
            client.close()
        }
    }

    @Test
    fun voiceOverrideFlowUsesDocumentedRequestsAndParsesResponses() = runTest {
        val requests = mutableListOf<HttpRequestData>()
        val client = HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    requests += request
                    respond(
                        content = when (request.method) {
                            HttpMethod.Get -> "[${moderationJson("mute")}]"
                            HttpMethod.Post -> moderationJson("unmute")
                            else -> """{"success":{"message":"removed","status_code":200}}"""
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
        val api = PlayerModerationApi(client)

        try {
            val existing = api.getForTarget(CURRENT_SESSION, TARGET_USER_ID)
            api.remove(TARGET_USER_ID, VoiceModerationType.Mute)
            val created = api.moderate(TARGET_USER_ID, VoiceModerationType.Unmute)

            assertEquals("mute", existing.single().type)
            assertEquals(TARGET_USER_ID, existing.single().targetUserId)
            assertEquals("unmute", created.type)

            assertEquals(HttpMethod.Get, requests[0].method)
            assertEquals("/api/1/auth/user/playermoderations", requests[0].url.encodedPath)
            assertTrue(requests[0].url.parameters.isEmpty())

            assertEquals(HttpMethod.Put, requests[1].method)
            assertEquals("/api/1/auth/user/unplayermoderate", requests[1].url.encodedPath)
            assertEquals(
                """{"moderated":"$TARGET_USER_ID","type":"mute"}""",
                requests[1].bodyText(),
            )

            assertEquals(HttpMethod.Post, requests[2].method)
            assertEquals("/api/1/auth/user/playermoderations", requests[2].url.encodedPath)
            assertEquals(
                """{"moderated":"$TARGET_USER_ID","type":"unmute"}""",
                requests[2].bodyText(),
            )
        } finally {
            client.close()
        }
    }

    private fun HttpRequestData.bodyText(): String =
        (body as OutgoingContent.ByteArrayContent).bytes().decodeToString()

    private fun cleanupTestClient(responseBody: (HttpRequestData) -> String) = HttpClient(MockEngine) {
        engine {
            addHandler { request ->
                respond(
                    content = responseBody(request),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            }
        }
        defaultRequest { url("https://api.vrchat.cloud/api/1/") }
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    }

    private fun moderationJson(
        type: String,
        sourceUserId: String = CURRENT_USER_ID,
        targetUserId: String = TARGET_USER_ID,
    ) = """
        {
          "created":"2026-08-31T00:00:00.000Z",
          "id":"pmod_1",
          "sourceDisplayName":"Current User",
          "sourceUserId":"$sourceUserId",
          "targetDisplayName":"Target User",
          "targetUserId":"$targetUserId",
          "type":"$type"
        }
    """.trimIndent()

    private companion object {
        const val CURRENT_USER_ID = "usr_current"
        val CURRENT_SESSION = AccountSessionToken(CURRENT_USER_ID, generation = 1)
        const val OTHER_SOURCE_USER_ID = "usr_other_source"
        const val TARGET_USER_ID = "usr_target"
        const val OTHER_TARGET_USER_ID = "usr_other_target"
    }
}
