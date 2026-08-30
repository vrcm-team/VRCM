package io.github.vrcmteam.vrcm.presentation.screens.favorites

import androidx.lifecycle.ViewModelStore
import com.russhwolf.settings.MapSettings
import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.di.supports.PersistentCookiesStorage
import io.github.vrcmteam.vrcm.network.api.auth.AuthApi
import io.github.vrcmteam.vrcm.network.api.users.UsersApi
import io.github.vrcmteam.vrcm.service.AuthService
import io.github.vrcmteam.vrcm.service.data.AccountDto
import io.github.vrcmteam.vrcm.storage.AccountCacheManager
import io.github.vrcmteam.vrcm.storage.AccountDao
import io.github.vrcmteam.vrcm.storage.InMemoryFriendListCacheStore
import io.github.vrcmteam.vrcm.storage.InMemorySecureStorage
import io.github.vrcmteam.vrcm.storage.InMemoryUserProfileCacheStore
import io.github.vrcmteam.vrcm.storage.meetup.MeetupCardAssetStore
import io.github.vrcmteam.vrcm.storage.meetup.MeetupCardConfigDao
import io.github.vrcmteam.vrcm.testing.MainDispatcherTest
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import kotlinx.serialization.json.Json
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import org.koin.core.logger.EmptyLogger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class FavoritesGroupsModelTest : MainDispatcherTest() {
    @Test
    fun accountSwitchClearsLoadedGroupsBeforeNewAccountFinishes() = runBlocking {
        val accountA = AccountDto(userId = "usr_a", username = "a")
        val accountB = AccountDto(userId = "usr_b", username = "b")
        val releaseAccountB = CompletableDeferred<Unit>()
        SharedFlowCentre.emitAuthenticated(accountA)
        val fixture = createFixture(accountA) { request ->
            when (request.url.encodedPath) {
                "/users/usr_a/groups" -> jsonResponse(userGroupsJson("grp_a"))
                "/users/usr_b/groups" -> {
                    releaseAccountB.await()
                    jsonResponse(userGroupsJson("grp_b"))
                }
                else -> error("Unexpected request: ${request.url}")
            }
        }
        try {
            fixture.model.loadIfNeeded()
            awaitUntil { fixture.model.state.value.groups.singleOrNull()?.groupId == "grp_a" }

            SharedFlowCentre.emitAuthenticated(accountB)
            awaitUntil { fixture.model.state.value.groups.isEmpty() }

            assertTrue(fixture.model.state.value.groups.isEmpty())
            assertEquals("", fixture.model.state.value.searchText)
            releaseAccountB.complete(Unit)
            awaitUntil { fixture.model.state.value.groups.singleOrNull()?.groupId == "grp_b" }
        } finally {
            fixture.close()
            SharedFlowCentre.emitLogout()
        }
    }

    @Test
    fun lateOldAccountResponseCannotOverwriteNewAccountGroups() = runBlocking {
        val accountA = AccountDto(userId = "usr_a", username = "a")
        val accountB = AccountDto(userId = "usr_b", username = "b")
        val accountAStarted = CompletableDeferred<Unit>()
        val releaseAccountA = CompletableDeferred<Unit>()
        SharedFlowCentre.emitAuthenticated(accountA)
        val fixture = createFixture(accountA) { request ->
            when (request.url.encodedPath) {
                "/users/usr_a/groups" -> {
                    accountAStarted.complete(Unit)
                    releaseAccountA.await()
                    jsonResponse(userGroupsJson("grp_a_late"))
                }
                "/users/usr_b/groups" -> jsonResponse(userGroupsJson("grp_b"))
                else -> error("Unexpected request: ${request.url}")
            }
        }
        try {
            fixture.model.loadIfNeeded()
            accountAStarted.await()

            SharedFlowCentre.emitAuthenticated(accountB)
            awaitUntil { fixture.model.state.value.groups.singleOrNull()?.groupId == "grp_b" }
            releaseAccountA.complete(Unit)
            yield()

            assertEquals(listOf("grp_b"), fixture.model.state.value.groups.map { it.groupId })
        } finally {
            fixture.close()
            SharedFlowCentre.emitLogout()
        }
    }
}

private class FavoritesGroupsFixture(
    val model: FavoritesGroupsModel,
    private val client: HttpClient,
) {
    fun close() {
        ViewModelStore().apply {
            put("test", model)
            clear()
        }
        client.close()
    }
}

private fun createFixture(
    account: AccountDto,
    handler: MockRequestHandler,
): FavoritesGroupsFixture {
    val logger = EmptyLogger()
    val client = HttpClient(MockEngine) {
        engine { addHandler(handler) }
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    }
    val accountDao = AccountDao(MapSettings(), InMemorySecureStorage()).also {
        it.saveAccountInfo(account)
    }
    val authService = AuthService(
        authApi = AuthApi(client),
        accountDao = accountDao,
        cookiesStorage = PersistentCookiesStorage(logger),
        accountCacheManager = AccountCacheManager(
            friendListCacheStore = InMemoryFriendListCacheStore(),
            userProfileCacheStore = InMemoryUserProfileCacheStore(),
            friendActivityStore = io.github.vrcmteam.vrcm.storage.NoOpFriendActivityCacheStore,
            meetupCardConfigDao = MeetupCardConfigDao(MapSettings()),
            meetupCardAssetStore = MeetupCardAssetStore(FakeFileSystem(), "/assets".toPath()),
        ),
    )
    return FavoritesGroupsFixture(
        FavoritesGroupsModel(UsersApi(client), authService),
        client,
    )
}

private fun io.ktor.client.engine.mock.MockRequestHandleScope.jsonResponse(content: String) = respond(
    content = content,
    status = HttpStatusCode.OK,
    headers = headersOf(HttpHeaders.ContentType, "application/json"),
)

private fun userGroupsJson(groupId: String) = """
    [{"id":"membership_$groupId","groupId":"$groupId","name":"$groupId","shortCode":"TEST"}]
""".trimIndent()

private suspend fun awaitUntil(predicate: () -> Boolean) {
    withTimeout(3_000) {
        while (!predicate()) yield()
    }
}
