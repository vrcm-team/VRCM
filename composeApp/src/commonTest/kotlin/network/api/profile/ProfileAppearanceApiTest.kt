package io.github.vrcmteam.vrcm.network.api.profile

import io.github.vrcmteam.vrcm.network.supports.VRCApiException
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class ProfileAppearanceApiTest {
    @Test
    fun getUsesProfileEndpointAndRequiredQueryParameters() = runBlocking {
        var method: HttpMethod? = null
        var path: String? = null
        var asSelf: String? = null
        var withGroupsAndWorlds: String? = null
        val client = testClient { request ->
            method = request.method
            path = request.url.encodedPath
            asSelf = request.url.parameters["asSelf"]
            withGroupsAndWorlds = request.url.parameters["withGroupsAndWorlds"]
            """{"id":"usr_123"}"""
        }

        try {
            ProfileAppearanceApi(client).get("usr_123")

            assertEquals(HttpMethod.Get, method)
            assertEquals("/api/1/profile/usr_123", path)
            assertEquals("true", asSelf)
            assertEquals("true", withGroupsAndWorlds)
        } finally {
            client.close()
        }
    }

    @Test
    fun getPreservesEmptyAppearanceValuesAndIgnoresUnknownFields() = runBlocking {
        val client = testClient {
            """{
                "id":"usr_123",
                "iconFrame":"",
                "unknownAppearanceField":{"nested":true}
            }"""
        }

        try {
            val result = ProfileAppearanceApi(client).get("usr_123")

            assertEquals("usr_123", result.id)
            assertEquals("", result.iconFrame)
            assertNull(result.profileEffect)
            assertNull(result.nameplateEffect)
        } finally {
            client.close()
        }
    }

    @Test
    fun getRejectsInvalidUserIdsBeforeSendingRequest() = runBlocking {
        var requestCount = 0
        val client = testClient {
            requestCount++
            """{"id":"usr_123"}"""
        }

        try {
            listOf("", "   ", "usr_123/appearance").forEach { userId ->
                assertFailsWith<IllegalArgumentException> {
                    ProfileAppearanceApi(client).get(userId)
                }
            }
            assertEquals(0, requestCount)
        } finally {
            client.close()
        }
    }

    @Test
    fun getRejectsUnauthorizedResponseInsteadOfDecodingDtoDefaults() = runBlocking {
        val client = testClient(status = HttpStatusCode.Unauthorized) {
            """{"error":{"message":"Unauthorized","status_code":401}}"""
        }

        try {
            val error = assertFailsWith<VRCApiException> {
                ProfileAppearanceApi(client).get("usr_123")
            }

            assertEquals(401, error.code)
        } finally {
            client.close()
        }
    }

    private fun testClient(
        status: HttpStatusCode = HttpStatusCode.OK,
        responseBody: (io.ktor.client.request.HttpRequestData) -> String,
    ) =
        HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    respond(
                        content = responseBody(request),
                        status = status,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
            }
            defaultRequest { url("https://api.vrchat.cloud/api/1/") }
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
}
