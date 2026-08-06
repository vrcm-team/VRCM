package io.github.vrcmteam.vrcm.presentation.screens.world

import androidx.lifecycle.ViewModelStore
import com.russhwolf.settings.MapSettings
import io.github.vrcmteam.vrcm.di.supports.PersistentCookiesStorage
import io.github.vrcmteam.vrcm.network.api.auth.AuthApi
import io.github.vrcmteam.vrcm.network.api.worlds.WorldsApi
import io.github.vrcmteam.vrcm.service.AuthService
import io.github.vrcmteam.vrcm.storage.AccountCacheManager
import io.github.vrcmteam.vrcm.storage.AccountDao
import io.github.vrcmteam.vrcm.storage.FriendListCacheDao
import io.github.vrcmteam.vrcm.storage.InMemorySecureStorage
import io.github.vrcmteam.vrcm.storage.UserProfileCacheDao
import io.github.vrcmteam.vrcm.testing.MainDispatcherTest
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
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import kotlinx.serialization.json.Json
import org.koin.core.logger.EmptyLogger
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class RecentWorldsScreenModelTest : MainDispatcherTest() {
    private val models = mutableListOf<RecentWorldsScreenModel>()

    @AfterTest
    fun disposeModel() {
        models.forEach { model ->
            ViewModelStore().apply {
                put("test", model)
                clear()
            }
        }
    }

    @Test
    fun repeatedInitialLoadSilentlyRefreshesWhileKeepingCurrentContent() = runBlocking {
        var requestCount = 0
        val releaseRefresh = CompletableDeferred<Unit>()
        val client = HttpClient(MockEngine) {
            engine {
                addHandler {
                    requestCount++
                    if (requestCount == 1) {
                        worldResponse("wrld_old")
                    } else {
                        releaseRefresh.await()
                        worldResponse("wrld_new")
                    }
                }
            }
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
        val model = createModel(client)

        try {
            model.loadRecentWorlds()
            awaitUntil { !model.isLoading && model.worlds.singleOrNull()?.id == "wrld_old" }
            assertEquals(1, requestCount)

            model.loadRecentWorlds()
            awaitUntil { requestCount == 2 }

            assertFalse(model.isLoading)
            assertEquals("wrld_old", model.worlds.single().id)

            releaseRefresh.complete(Unit)
            awaitUntil { model.worlds.singleOrNull()?.id == "wrld_new" }

            assertFalse(model.isLoading)
            assertEquals(2, requestCount)
        } finally {
            client.close()
        }
    }

    private fun createModel(client: HttpClient): RecentWorldsScreenModel {
        val logger = EmptyLogger()
        val authService = AuthService(
            authApi = AuthApi(client),
            accountDao = AccountDao(MapSettings(), InMemorySecureStorage()),
            cookiesStorage = PersistentCookiesStorage(logger),
            accountCacheManager = AccountCacheManager(
                friendListCacheDao = FriendListCacheDao(MapSettings()),
                userProfileCacheDao = UserProfileCacheDao(MapSettings()),
                friendActivityStore = io.github.vrcmteam.vrcm.storage.NoOpFriendActivityCacheStore,
            ),
        )
        return RecentWorldsScreenModel(
            authService = authService,
            worldsApi = WorldsApi(client),
        )
            .also(models::add)
    }
}

private fun MockRequestHandleScope.worldResponse(id: String) = respond(
    content = """
        [{
          "authorId":"usr_author","authorName":"Author","capacity":16,"created_at":null,
          "description":null,"favorites":0,"featured":false,"heat":0,"id":"$id",
          "imageUrl":"","labsPublicationDate":"","name":"$id","namespace":null,
          "organization":"","popularity":0,"publicationDate":"","recommendedCapacity":16,
          "releaseStatus":"public","tags":[],"thumbnailImageUrl":null,"udonProducts":[],
          "unityPackages":[],"updated_at":null,"version":1,"visits":0
        }]
    """.trimIndent(),
    status = HttpStatusCode.OK,
    headers = headersOf(HttpHeaders.ContentType, "application/json"),
)

private suspend fun awaitUntil(predicate: () -> Boolean) {
    withTimeout(1_000) {
        while (!predicate()) yield()
    }
}
