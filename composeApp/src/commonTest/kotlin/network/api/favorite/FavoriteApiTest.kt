package io.github.vrcmteam.vrcm.network.api.favorite

import io.github.vrcmteam.vrcm.network.api.attributes.FavoriteGroupVisibility
import io.github.vrcmteam.vrcm.network.api.attributes.FavoriteType
import io.github.vrcmteam.vrcm.network.supports.VRCApiException
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class FavoriteApiTest {
    @Test
    fun clearGroupUsesDeleteWithOfficialGroupCoordinates() = runBlocking {
        val client = HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    assertEquals(HttpMethod.Delete, request.method)
                    assertEquals(
                        "/favorite/group/world/worlds1/usr_owner",
                        request.url.encodedPath,
                    )
                    respond("", HttpStatusCode.OK)
                }
            }
        }
        try {
            FavoriteApi(client).clearFavoriteGroup(
                favoriteType = FavoriteType.World,
                favoriteGroupName = "worlds1",
                userId = "usr_owner",
            )
        } finally {
            client.close()
        }
    }

    @Test
    fun clearGroupRejectsNonSuccessResponse() = runBlocking {
        val client = HttpClient(MockEngine) {
            engine { addHandler { respond("denied", HttpStatusCode.Forbidden) } }
        }
        try {
            val error = assertFailsWith<VRCApiException> {
                FavoriteApi(client).clearFavoriteGroup(
                    favoriteType = FavoriteType.Friend,
                    favoriteGroupName = "group_1",
                    userId = "usr_owner",
                )
            }
            assertEquals(HttpStatusCode.Forbidden.value, error.code)
        } finally {
            client.close()
        }
    }

    @Test
    fun updateFavoriteGroupUsesOwnerScopedEndpointAndExactPayload() = runBlocking {
        var requestMethod: HttpMethod? = null
        var requestPath = ""
        var requestBody = ""
        val client = HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    requestMethod = request.method
                    requestPath = request.url.encodedPath
                    requestBody = request.bodyText()
                    respond("", HttpStatusCode.OK)
                }
            }
            install(ContentNegotiation) { json(Json) }
        }

        try {
            FavoriteApi(client).updateFavoriteGroup(
                favoriteType = FavoriteType.Avatar,
                favoriteGroupName = "avatars1",
                userId = "usr_owner",
                displayName = "Favorites",
                visibility = FavoriteGroupVisibility.Friends,
            )

            assertEquals(HttpMethod.Put, requestMethod)
            assertEquals("/favorite/group/avatar/avatars1/usr_owner", requestPath)
            assertEquals(
                "{\"displayName\":\"Favorites\",\"visibility\":\"friends\"}",
                requestBody,
            )
        } finally {
            client.close()
        }
    }

    private fun HttpRequestData.bodyText(): String =
        (body as OutgoingContent.ByteArrayContent).bytes().decodeToString()
}
