package io.github.vrcmteam.vrcm.network.api.users

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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UsersApiPlayerBlockTest {
    @Test
    fun readsCurrentStateWithTargetedBlockQuery() = runBlocking {
        var capturedRequest: HttpRequestData? = null
        val client = testClient { request ->
            capturedRequest = request
            """[
                {"targetUserId":"usr_other","type":"block"},
                {"targetUserId":"usr_target","type":"block"}
            ]"""
        }

        try {
            assertTrue(UsersApi(client).isUserBlocked("usr_target"))
            val request = requireNotNull(capturedRequest)
            assertEquals(HttpMethod.Get, request.method)
            assertEquals("/api/1/auth/user/playermoderations", request.url.encodedPath)
            assertEquals("block", request.url.parameters["type"])
            assertEquals("usr_target", request.url.parameters["targetUserId"])
        } finally {
            client.close()
        }
    }

    @Test
    fun missingTargetedBlockIsReportedAsUnblocked() = runBlocking {
        val client = testClient {
            """[{"targetUserId":"usr_other","type":"block"}]"""
        }

        try {
            assertFalse(UsersApi(client).isUserBlocked("usr_target"))
        } finally {
            client.close()
        }
    }

    @Test
    fun blockAndUnblockUseTheRequiredEndpointsAndBodies() = runBlocking {
        val requests = mutableListOf<Triple<HttpMethod, String, String>>()
        val client = testClient { request ->
            requests += Triple(request.method, request.url.encodedPath, request.bodyText())
            if (request.method == HttpMethod.Post) {
                """{"targetUserId":"usr_target","type":"block"}"""
            } else {
                """{"success":{"message":"ok","status_code":200}}"""
            }
        }
        val api = UsersApi(client)

        try {
            api.blockUser("usr_target")
            api.unblockUser("usr_target")

            assertEquals(
                listOf(
                    Triple(
                        HttpMethod.Post,
                        "/api/1/auth/user/playermoderations",
                        """{"moderated":"usr_target","type":"block"}""",
                    ),
                    Triple(
                        HttpMethod.Put,
                        "/api/1/auth/user/unplayermoderate",
                        """{"moderated":"usr_target","type":"block"}""",
                    ),
                ),
                requests,
            )
        } finally {
            client.close()
        }
    }

    @Test
    fun mismatchedBlockResponseIsNotAcceptedAsSuccess(): Unit = runBlocking {
        val client = testClient {
            """{"targetUserId":"usr_other","type":"block"}"""
        }

        try {
            assertFailsWith<IllegalStateException> {
                UsersApi(client).blockUser("usr_target")
            }
        } finally {
            client.close()
        }
    }

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
