package io.github.vrcmteam.vrcm.presentation.screens.world

import cafe.adriel.voyager.core.annotation.InternalVoyagerApi
import cafe.adriel.voyager.core.model.ScreenModelStore
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
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import kotlinx.serialization.json.Json
import org.koin.core.logger.EmptyLogger
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

@OptIn(InternalVoyagerApi::class)
class RecentWorldsScreenModelTest : MainDispatcherTest() {
    private val holderKey = "RecentWorldsScreenModelTest:${hashCode()}"

    @AfterTest
    fun disposeModel() {
        ScreenModelStore.onDisposeNavigator(holderKey)
    }

    @Test
    fun repeatedInitialLoadKeepsExistingStateWithoutAnotherRequest() = runBlocking {
        var requestCount = 0
        val client = HttpClient(MockEngine) {
            engine {
                addHandler {
                    requestCount++
                    respond(
                        content = "[]",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
            }
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
        val model = createModel(client)

        try {
            model.loadRecentWorlds()
            awaitUntil { !model.isLoading }
            assertEquals(1, requestCount)

            model.loadRecentWorlds()

            assertFalse(model.isLoading)
            assertEquals(1, requestCount)
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
            ),
        )
        return ScreenModelStore.getOrPut<RecentWorldsScreenModel>(holderKey, tag = null) {
            RecentWorldsScreenModel(
                authService = authService,
                worldsApi = WorldsApi(client),
            )
        }
    }
}

private suspend fun awaitUntil(predicate: () -> Boolean) {
    withTimeout(1_000) {
        while (!predicate()) yield()
    }
}
