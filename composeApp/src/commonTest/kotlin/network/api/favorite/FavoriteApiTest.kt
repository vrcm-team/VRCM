package io.github.vrcmteam.vrcm.network.api.favorite

import io.github.vrcmteam.vrcm.network.api.attributes.FavoriteType
import io.github.vrcmteam.vrcm.network.supports.VRCApiException
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
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
}
