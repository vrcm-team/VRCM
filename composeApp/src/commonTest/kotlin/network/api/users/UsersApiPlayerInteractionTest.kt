package io.github.vrcmteam.vrcm.network.api.users

import io.github.vrcmteam.vrcm.network.api.users.data.PlayerInteractionModerationData
import io.github.vrcmteam.vrcm.network.api.users.data.PlayerInteractionOverride
import io.github.vrcmteam.vrcm.network.api.users.data.resolvePlayerInteractionSnapshot
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
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class UsersApiPlayerInteractionTest {
    @Test
    fun readsBothInteractionOverridesForOnlyTheRequestedUser() = runBlocking {
        var capturedRequest: HttpRequestData? = null
        val client = testClient { request ->
            capturedRequest = request
            """[
                {"targetUserId":"usr_other","type":"interactOff","created":"2026-08-31T12:00:00Z"},
                {"targetUserId":"usr_target","type":"interactOn","created":"2026-08-31T13:00:00Z"},
                {"targetUserId":"usr_target","type":"mute","created":"2026-08-31T15:00:00Z"},
                {"targetUserId":"usr_target","type":"interactOff","created":"2026-08-31T14:00:00Z"}
            ]"""
        }

        try {
            val snapshot = UsersApi(client).getPlayerInteractionSnapshot("usr_target")

            assertEquals(PlayerInteractionOverride.InteractOff, snapshot.effectiveOverride)
            assertEquals(
                setOf(PlayerInteractionOverride.InteractOn, PlayerInteractionOverride.InteractOff),
                snapshot.explicitOverrides,
            )
            val request = requireNotNull(capturedRequest)
            assertEquals(HttpMethod.Get, request.method)
            assertEquals("/api/1/auth/user/playermoderations", request.url.encodedPath)
            assertEquals("usr_target", request.url.parameters["targetUserId"])
            assertNull(request.url.parameters["type"])
        } finally {
            client.close()
        }
    }

    @Test
    fun fullyParseableConflictUsesTheNewestInstant() {
        val snapshot = resolvePlayerInteractionSnapshot(
            targetUserId = "usr_target",
            moderations = listOf(
                moderation("interactOff", "2026-08-31T14:00:00Z"),
                moderation("interactOn", "2026-08-31T13:00:00Z"),
            ),
        )

        assertEquals(PlayerInteractionOverride.InteractOff, snapshot.effectiveOverride)
    }

    @Test
    fun anyInvalidConflictTimeFallsBackToStableResponseOrder() {
        val snapshot = resolvePlayerInteractionSnapshot(
            targetUserId = "usr_target",
            moderations = listOf(
                moderation("interactOff", "2026-08-31T14:00:00Z"),
                moderation("interactOn", "not-an-instant"),
            ),
        )

        assertEquals(PlayerInteractionOverride.InteractOn, snapshot.effectiveOverride)
    }

    @Test
    fun entirelyInvalidConflictTimesUseTheLastMatchingResponse() {
        val snapshot = resolvePlayerInteractionSnapshot(
            targetUserId = "usr_target",
            moderations = listOf(
                moderation("interactOn", null),
                moderation("interactOff", "invalid"),
            ),
        )

        assertEquals(PlayerInteractionOverride.InteractOff, snapshot.effectiveOverride)
    }

    @Test
    fun removeAndCreateUseTheOfficialEndpointsAndBodies() = runBlocking {
        val requests = mutableListOf<Triple<HttpMethod, String, String>>()
        val client = testClient { request ->
            requests += Triple(request.method, request.url.encodedPath, request.bodyText())
            if (request.method == HttpMethod.Put) {
                """{"success":{"message":"ok","status_code":200}}"""
            } else {
                """{"targetUserId":"usr_target","type":"interactOn"}"""
            }
        }
        val api = UsersApi(client)

        try {
            api.removePlayerInteractionOverride(
                "usr_target",
                PlayerInteractionOverride.InteractOff,
            )
            api.createPlayerInteractionOverride(
                "usr_target",
                PlayerInteractionOverride.InteractOn,
            )

            assertEquals(
                listOf(
                    Triple(
                        HttpMethod.Put,
                        "/api/1/auth/user/unplayermoderate",
                        """{"moderated":"usr_target","type":"interactOff"}""",
                    ),
                    Triple(
                        HttpMethod.Post,
                        "/api/1/auth/user/playermoderations",
                        """{"moderated":"usr_target","type":"interactOn"}""",
                    ),
                ),
                requests,
            )
        } finally {
            client.close()
        }
    }

    @Test
    fun mismatchedCreateResponseIsRejected(): Unit = runBlocking {
        val client = testClient {
            """{"targetUserId":"usr_other","type":"interactOn"}"""
        }

        try {
            assertFailsWith<IllegalStateException> {
                UsersApi(client).createPlayerInteractionOverride(
                    "usr_target",
                    PlayerInteractionOverride.InteractOn,
                )
            }
        } finally {
            client.close()
        }
    }

    private fun moderation(type: String, created: String?) = PlayerInteractionModerationData(
        targetUserId = "usr_target",
        type = type,
        created = created,
    )

    private fun testClient(responseBody: (HttpRequestData) -> String) =
        HttpClient(MockEngine) {
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
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }

    private fun HttpRequestData.bodyText(): String =
        (body as OutgoingContent.ByteArrayContent).bytes().decodeToString()
}
