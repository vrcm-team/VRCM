package io.github.vrcmteam.vrcm.service

import com.russhwolf.settings.MapSettings
import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.network.api.attributes.FavoriteType
import io.github.vrcmteam.vrcm.network.api.attributes.FavoriteGroupVisibility
import io.github.vrcmteam.vrcm.network.api.favorite.FavoriteApi
import io.github.vrcmteam.vrcm.service.data.AccountDto
import io.github.vrcmteam.vrcm.storage.FavoriteLocalDao
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.supervisorScope
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FavoriteServiceSessionTest {
    @Test
    fun unauthenticatedLoadReturnsFailureWithoutCallingFavoritesApi() = runBlocking {
        SharedFlowCentre.emitLogout()
        var favoritesRequestCount = 0
        val client = favoriteClient { requestPath, _ ->
            favoritesRequestCount++
            error("Unexpected favorites request: $requestPath")
        }
        val service = favoriteService(client)
        try {
            val result = service.loadFavoriteByGroup(FavoriteType.World)

            assertTrue(result.isFailure)
            assertEquals("No authenticated session", result.exceptionOrNull()?.message)
            assertEquals(0, favoritesRequestCount)
        } finally {
            service.dispose()
            client.close()
            SharedFlowCentre.emitLogout()
        }
    }

    @Test
    fun lateRequestFromPreviousSessionGenerationCannotOverwriteCurrentCache() = runBlocking {
        val account = AccountDto(userId = "usr_owner", username = "owner")
        SharedFlowCentre.emitAuthenticated(account)
        val oldGroupsStarted = CompletableDeferred<Unit>()
        val releaseOldGroups = CompletableDeferred<Unit>()
        var firstPageRequests = 0
        val client = favoriteClient { requestPath, offset ->
            when (requestPath) {
                "/favorites" -> jsonResponse("[]")
                "/favorite/groups" -> when (offset) {
                    "0" -> {
                        firstPageRequests++
                        if (firstPageRequests == 1) {
                            oldGroupsStarted.complete(Unit)
                            releaseOldGroups.await()
                            jsonResponse(groupJson("grp_old"))
                        } else {
                            jsonResponse(groupJson("grp_current"))
                        }
                    }
                    else -> jsonResponse("[]")
                }
                else -> error("Unexpected favorites request: $requestPath")
            }
        }
        val service = favoriteService(client)
        try {
            val oldLoad = async(start = CoroutineStart.UNDISPATCHED) {
                service.loadFavoriteByGroup(FavoriteType.World)
            }
            oldGroupsStarted.await()

            SharedFlowCentre.emitAuthenticated(account)
            val currentLoad = service.loadFavoriteByGroup(FavoriteType.World)
            assertTrue(currentLoad.isSuccess)
            assertEquals(
                listOf("grp_current"),
                service.favoritesByGroup(FavoriteType.World).value.keys
                    .filterNot { it.ownerId == "local" }
                    .map { it.id },
            )

            releaseOldGroups.complete(Unit)
            assertTrue(oldLoad.await().isSuccess)
            assertEquals(
                listOf("grp_current"),
                service.favoritesByGroup(FavoriteType.World).value.keys
                    .filterNot { it.ownerId == "local" }
                    .map { it.id },
            )
        } finally {
            service.dispose()
            client.close()
            SharedFlowCentre.emitLogout()
        }
    }

    @Test
    fun groupMetadataFailureReturnsFailureAndKeepsPreviousSnapshot() = runBlocking {
        val account = AccountDto(userId = "usr_owner", username = "owner")
        SharedFlowCentre.emitAuthenticated(account)
        var firstPageRequests = 0
        val client = favoriteClient { requestPath, offset ->
            when (requestPath) {
                "/favorites" -> jsonResponse("[]")
                "/favorite/groups" -> when (offset) {
                    "0" -> {
                        firstPageRequests++
                        if (firstPageRequests == 1) {
                            jsonResponse(groupJson("grp_cached"))
                        } else {
                            respond("failed", HttpStatusCode.InternalServerError)
                        }
                    }
                    else -> jsonResponse("[]")
                }
                else -> error("Unexpected favorites request: $requestPath")
            }
        }
        val service = favoriteService(client)
        try {
            assertTrue(service.loadFavoriteByGroup(FavoriteType.World).isSuccess)
            val previous = service.favoritesByGroup(FavoriteType.World).value

            assertTrue(service.loadFavoriteByGroup(FavoriteType.World).isFailure)
            assertEquals(previous, service.favoritesByGroup(FavoriteType.World).value)
        } finally {
            service.dispose()
            client.close()
            SharedFlowCentre.emitLogout()
        }
    }

    @Test
    fun lateGroupUpdateCannotPublishIntoAnotherAccountsCache() = runBlocking {
        val accountA = AccountDto(userId = "usr_owner_a", username = "owner-a")
        val accountB = AccountDto(userId = "usr_owner_b", username = "owner-b")
        SharedFlowCentre.emitAuthenticated(accountA)
        val updateStarted = CompletableDeferred<Unit>()
        val releaseUpdate = CompletableDeferred<Unit>()
        val client = favoriteClient { requestPath, offset ->
            val ownerId = SharedFlowCentre.currentSession.value?.token?.userId.orEmpty()
            when (requestPath) {
                "/favorites" -> if (offset == "0") {
                    jsonResponse(favoriteJson(ownerId))
                } else {
                    jsonResponse("[]")
                }
                "/favorite/groups" -> if (offset == "0") {
                    jsonResponse(groupJson("grp_$ownerId", ownerId))
                } else {
                    jsonResponse("[]")
                }
                "/favorite/group/world/worlds1/usr_owner_a" -> {
                    updateStarted.complete(Unit)
                    releaseUpdate.await()
                    jsonResponse("")
                }
                else -> error("Unexpected favorites request: $requestPath")
            }
        }
        val service = favoriteService(client)
        try {
            assertTrue(service.loadFavoriteByGroup(FavoriteType.World).isSuccess)
            val requestToken = assertNotNull(SharedFlowCentre.currentSession.value).token
            val update = service.prepareFavoriteGroupUpdate(
                sessionToken = requestToken,
                favoriteType = FavoriteType.World,
                groupName = "worlds1",
                displayName = "Account A update",
                visibility = FavoriteGroupVisibility.Public,
            )
            supervisorScope {
                val oldUpdate = async(start = CoroutineStart.UNDISPATCHED) {
                    service.sendFavoriteGroupUpdate(update)
                    service.commitFavoriteGroupUpdate(requestToken, update)
                }
                updateStarted.await()

                SharedFlowCentre.emitAuthenticated(accountB)
                assertTrue(service.loadFavoriteByGroup(FavoriteType.World).isSuccess)
                releaseUpdate.complete(Unit)
                assertFailsWith<IllegalStateException> { oldUpdate.await() }
            }

            val currentRemoteGroup = service.favoritesByGroup(FavoriteType.World).value.keys
                .single { it.ownerId != "local" }
            assertEquals(accountB.userId, currentRemoteGroup.ownerId)
            assertEquals("Worlds", currentRemoteGroup.displayName)
        } finally {
            releaseUpdate.complete(Unit)
            service.dispose()
            client.close()
            SharedFlowCentre.emitLogout()
        }
    }
}

private fun favoriteService(client: HttpClient) = FavoriteService(
    favoriteApi = FavoriteApi(client),
    favoriteLocalDao = FavoriteLocalDao(MapSettings()),
)

private fun favoriteClient(
    favoriteHandler: suspend MockRequestHandleScope.(path: String, offset: String?) -> io.ktor.client.request.HttpResponseData,
) = HttpClient(MockEngine) {
    engine {
        addHandler { request ->
            if (request.url.encodedPath == "/auth/user/favoritelimits") {
                jsonResponse(favoriteLimitsJson())
            } else {
                favoriteHandler(request.url.encodedPath, request.url.parameters["offset"])
            }
        }
    }
    install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
}

private fun MockRequestHandleScope.jsonResponse(content: String) = respond(
    content = content,
    status = HttpStatusCode.OK,
    headers = headersOf(HttpHeaders.ContentType, "application/json"),
)

private fun groupJson(id: String, ownerId: String = "usr_owner") = """
    [{
      "id":"$id","ownerId":"$ownerId","type":"world","visibility":"private",
      "displayName":"Worlds","name":"worlds1","ownerDisplayName":"owner","tags":[]
    }]
""".trimIndent()

private fun favoriteJson(ownerId: String) = """
    [{
      "favoriteId":"wrld_$ownerId","id":"fvrt_$ownerId",
      "tags":["worlds1"],"type":"world"
    }]
""".trimIndent()

private fun favoriteLimitsJson() = """
    {
      "maxFavoriteGroups":{"avatar":1,"friend":1,"world":1},
      "maxFavoritesPerGroup":{"avatar":100,"friend":100,"world":100},
      "defaultMaxFavoriteGroups":1,
      "defaultMaxFavoritesPerGroup":100
    }
""".trimIndent()
