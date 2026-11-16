package io.github.vrcmteam.vrcm.network.api.profile

import io.github.vrcmteam.vrcm.di.modules.createNetworkJson
import io.github.vrcmteam.vrcm.network.api.profile.data.UpdateProfileData
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
import io.ktor.http.content.OutgoingContent
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class ProfileAppearanceApiTest {
    @Test
    fun bioUpdateUsesProfileEndpointAndOnlySendsTheEditedField() = runBlocking {
        val client = testClient { request ->
            assertEquals(HttpMethod.Put, request.method)
            assertEquals("/api/1/profile/usr_123", request.url.encodedPath)
            assertEquals(
                Json.parseToJsonElement("""{"bio":"New bio"}"""),
                Json.parseToJsonElement((request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()),
            )
            """{"id":"usr_123","bio":"New bio"}"""
        }
        try {
            assertEquals("New bio", ProfileAppearanceApi(client).update("usr_123", UpdateProfileData(bio = "New bio")).bio)
        } finally {
            client.close()
        }
    }

    @Test
    fun clearingPublicFieldsSendsAndAcceptsEmptyValues() = runBlocking {
        val client = testClient { request ->
            assertEquals(
                Json.parseToJsonElement("""{"bio":"","bioLinks":[],"languages":[]}"""),
                Json.parseToJsonElement((request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()),
            )
            """{"id":"usr_123","bio":"","bioLinks":[],"languages":[]}"""
        }
        try {
            val saved = ProfileAppearanceApi(client).update(
                "usr_123", UpdateProfileData(bio = "", bioLinks = emptyList(), languages = emptyList()),
            )
            assertEquals("", saved.bio)
            assertEquals(emptyList(), saved.bioLinks)
            assertEquals(emptyList(), saved.languages)
        } finally {
            client.close()
        }
    }

    @Test
    fun successfulHttpStatusCannotHideIgnoredOrMissingProfileFields() = runBlocking {
        val cases = listOf(
            UpdateProfileData(bio = "New bio") to """{"id":"usr_123","bio":"Old bio"}""",
            UpdateProfileData(bio = "New bio") to """{"id":"usr_123"}""",
            UpdateProfileData(bioLinks = emptyList()) to """{"id":"usr_123"}""",
            UpdateProfileData(languages = listOf("eng")) to """{"id":"usr_123","languages":["jpn"]}""",
            UpdateProfileData(bio = "New bio") to """{"id":"usr_other","bio":"New bio"}""",
        )
        for ((data, body) in cases) {
            val client = testClient { body }
            try {
                assertFailsWith<IllegalStateException> { ProfileAppearanceApi(client).update("usr_123", data) }
            } finally {
                client.close()
            }
        }
    }

    @Test
    fun socialLinksAndLanguagesUseProfileFieldsAndKeepServerAcceptedLinks() = runBlocking {
        val client = testClient { request ->
            assertEquals(HttpMethod.Put, request.method)
            assertEquals("/api/1/profile/usr_123", request.url.encodedPath)
            assertEquals(
                Json.parseToJsonElement("""{"bioLinks":["https://example.com"],"languages":["eng","jpn"]}"""),
                Json.parseToJsonElement((request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()),
            )
            """{"id":"usr_123","bioLinks":["https://example.com/"],"languages":["jpn","eng"]}"""
        }
        try {
            val saved = ProfileAppearanceApi(client).update(
                "usr_123", UpdateProfileData(bioLinks = listOf("https://example.com"), languages = listOf("eng", "jpn")),
            )
            assertEquals(listOf("https://example.com/"), saved.bioLinks)
        } finally {
            client.close()
        }
    }

    @Test
    fun rejectedProfileUpdatePropagatesTheServerError() = runBlocking {
        val client = testClient(status = HttpStatusCode.BadRequest) {
            """{"error":{"message":"Invalid bio link","status_code":400}}"""
        }
        try {
            val error = assertFailsWith<VRCApiException> {
                ProfileAppearanceApi(client).update("usr_123", UpdateProfileData(bioLinks = listOf("invalid")))
            }
            assertEquals(400, error.code)
        } finally {
            client.close()
        }
    }

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
    fun getPublicProfileUsesGroupsAndWorldsWithoutAsSelf() = runBlocking {
        var path: String? = null
        var asSelf: String? = null
        var withGroupsAndWorlds: String? = null
        val client = testClient { request ->
            path = request.url.encodedPath
            asSelf = request.url.parameters["asSelf"]
            withGroupsAndWorlds = request.url.parameters["withGroupsAndWorlds"]
            """{"id":"usr_123","bio":"Bio","iconUrl":"https://example.invalid/icon.png"}"""
        }

        try {
            val result = ProfileAppearanceApi(client).getPublicProfile("usr_123")

            assertEquals("/api/1/profile/usr_123", path)
            assertNull(asSelf)
            assertEquals("true", withGroupsAndWorlds)
            assertEquals("Bio", result.bio)
            assertEquals("https://example.invalid/icon.png", result.iconUrl)
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
                "bio":"Profile bio",
                "bioLinks":["https://example.invalid"],
                "displayName":"Profile name",
                "iconUrl":"https://api.vrchat.cloud/file/file_profile/1/file",
                "pronouns":"they/them",
                "bannerUrl":"https://api.vrchat.cloud/file/file_banner/1/file",
                "unknownAppearanceField":{"nested":true}
            }"""
        }

        try {
            val result = ProfileAppearanceApi(client).get("usr_123")

            assertEquals("usr_123", result.id)
            assertEquals("", result.iconFrame)
            assertEquals("Profile bio", result.bio)
            assertEquals(listOf("https://example.invalid"), result.bioLinks)
            assertEquals("Profile name", result.displayName)
            assertEquals("https://api.vrchat.cloud/file/file_profile/1/file", result.iconUrl)
            assertEquals("they/them", result.pronouns)
            assertEquals("https://api.vrchat.cloud/file/file_banner/1/file", result.bannerUrl)
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
                json(createNetworkJson())
            }
        }
}
