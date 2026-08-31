package io.github.vrcmteam.vrcm.presentation.screens.home.pager

import androidx.lifecycle.ViewModelStore
import com.russhwolf.settings.MapSettings
import io.github.vrcmteam.vrcm.core.shared.AccountSessionToken
import io.github.vrcmteam.vrcm.core.shared.AccountWebSocketEvent
import io.github.vrcmteam.vrcm.core.shared.AuthenticatedAccount
import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.di.supports.PersistentCookiesStorage
import io.github.vrcmteam.vrcm.network.api.attributes.UserStatus
import io.github.vrcmteam.vrcm.network.api.auth.AuthApi
import io.github.vrcmteam.vrcm.network.api.avatars.AvatarsApi
import io.github.vrcmteam.vrcm.network.api.favorite.FavoriteApi
import io.github.vrcmteam.vrcm.network.api.friends.FriendsApi
import io.github.vrcmteam.vrcm.network.api.friends.date.FriendData
import io.github.vrcmteam.vrcm.network.api.users.UsersApi
import io.github.vrcmteam.vrcm.network.api.worlds.WorldsApi
import io.github.vrcmteam.vrcm.network.websocket.data.WebSocketEvent
import io.github.vrcmteam.vrcm.network.websocket.data.content.FriendActiveContent
import io.github.vrcmteam.vrcm.network.websocket.data.content.UserContent
import io.github.vrcmteam.vrcm.network.websocket.data.type.FriendEvents
import io.github.vrcmteam.vrcm.service.AuthService
import io.github.vrcmteam.vrcm.service.FavoriteService
import io.github.vrcmteam.vrcm.service.FriendService
import io.github.vrcmteam.vrcm.service.FriendStateSnapshot
import io.github.vrcmteam.vrcm.service.UserProfileEnrichmentService
import io.github.vrcmteam.vrcm.service.data.AccountDto
import io.github.vrcmteam.vrcm.storage.AccountCacheManager
import io.github.vrcmteam.vrcm.storage.AccountDao
import io.github.vrcmteam.vrcm.storage.FavoriteLocalDao
import io.github.vrcmteam.vrcm.storage.InMemoryFavoriteListCacheStore
import io.github.vrcmteam.vrcm.storage.InMemoryFriendListCacheStore
import io.github.vrcmteam.vrcm.storage.InMemorySecureStorage
import io.github.vrcmteam.vrcm.storage.InMemoryUserProfileCacheStore
import io.github.vrcmteam.vrcm.storage.data.FriendListCache
import io.github.vrcmteam.vrcm.storage.meetup.MeetupCardAssetStore
import io.github.vrcmteam.vrcm.storage.meetup.MeetupCardConfigDao
import io.github.vrcmteam.vrcm.testing.MainDispatcherTest
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import org.koin.core.logger.EmptyLogger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FriendListPagerModelTest : MainDispatcherTest() {
    @Test
    fun staleFriendSnapshotIsRejectedAfterSessionTokenChanges() {
        val accountAToken = AccountSessionToken(userId = "usr_account_a", generation = 1L)
        val accountBToken = AccountSessionToken(userId = "usr_account_b", generation = 2L)
        val accountAFriend = cachedFriend(
            id = "usr_account_a_friend",
            displayName = "Account A Friend",
            status = UserStatus.Active,
        )

        val state = FriendStateSnapshot(
            sessionToken = accountAToken,
            friends = mapOf(accountAFriend.id to accountAFriend),
        )

        assertEquals(emptyList(), state.friendsForSession(accountBToken))
    }

    @Test
    fun sameAccountReauthenticationRepublishesRetainedFriendSnapshot() = runBlocking {
        SharedFlowCentre.emitLogout()
        val account = AccountDto(userId = "usr_same_account", username = "same-account")
        SharedFlowCentre.emitAuthenticated(account)
        val firstSession = assertNotNull(SharedFlowCentre.currentSession.value)
        val json = Json { ignoreUnknownKeys = true }
        var failFriendRefresh = false
        val client = HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    when (request.url.encodedPath) {
                        "/auth/user/friends" -> if (failFriendRefresh) {
                            respond("unavailable", HttpStatusCode.InternalServerError)
                        } else {
                            jsonResponse("[]")
                        }
                        "/auth/user" -> respond("unavailable", HttpStatusCode.InternalServerError)
                        "/auth/user/favoritelimits" -> jsonResponse(favoriteLimitsJson())
                        "/favorites", "/favorite/groups" -> jsonResponse("[]")
                        else -> error("Unexpected request: ${request.url}")
                    }
                }
            }
            install(ContentNegotiation) { json(json) }
        }
        val friendListCacheStore = InMemoryFriendListCacheStore()
        val favoriteListCacheStore = InMemoryFavoriteListCacheStore()
        val accountCacheManager = AccountCacheManager(
            friendListCacheStore = friendListCacheStore,
            userProfileCacheStore = InMemoryUserProfileCacheStore(),
            friendActivityStore = io.github.vrcmteam.vrcm.storage.NoOpFriendActivityCacheStore,
            meetupCardConfigDao = MeetupCardConfigDao(MapSettings()),
            meetupCardAssetStore = MeetupCardAssetStore(
                FakeFileSystem(),
                "/meetup-assets".toPath(),
            ),
            favoriteListCacheStore = favoriteListCacheStore,
        )
        val authService = AuthService(
            authApi = AuthApi(client),
            accountDao = AccountDao(MapSettings(), InMemorySecureStorage()).also {
                it.saveAccountInfo(account)
            },
            cookiesStorage = PersistentCookiesStorage(EmptyLogger()),
            accountCacheManager = accountCacheManager,
        )
        val friendService = FriendService(
            friendsApi = FriendsApi(client),
            authService = authService,
            json = json,
            friendListCacheStore = friendListCacheStore,
            accountCacheManager = accountCacheManager,
            logger = EmptyLogger(),
        )
        val favoriteService = FavoriteService(
            favoriteApi = FavoriteApi(client),
            favoriteLocalDao = FavoriteLocalDao(MapSettings()),
        )
        val profileScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val model = FriendListPagerModel(
            userProfileEnrichmentService = UserProfileEnrichmentService(UsersApi(client), profileScope),
            friendService = friendService,
            authService = authService,
            favoriteService = favoriteService,
            worldsApi = WorldsApi(client),
            avatarsApi = AvatarsApi(client),
            favoriteListCacheStore = favoriteListCacheStore,
            accountCacheManager = accountCacheManager,
        )

        try {
            val friendId = "usr_retained_friend"
            val friendEvent = activeFriendEvent(json, friendId, "Retained Friend")
            emitFriendUntilObserved(friendService, firstSession, friendId, friendEvent)
            awaitFriendIds(model, setOf(friendId))

            failFriendRefresh = true
            SharedFlowCentre.emitAuthenticated(account)
            val renewedSession = assertNotNull(SharedFlowCentre.currentSession.value)

            awaitUntil {
                friendService.friendStateSnapshot.value.sessionToken == renewedSession.token &&
                    model.friendDirectoryFriends.value.mapTo(mutableSetOf()) { it.id } == setOf(friendId)
            }
            assertEquals(renewedSession.token, friendService.friendStateSnapshot.value.sessionToken)
        } finally {
            profileScope.cancel()
            ViewModelStore().apply {
                put("friend-list-pager", model)
                clear()
            }
            friendService.dispose()
            favoriteService.dispose()
            SharedFlowCentre.emitLogout()
            client.close()
        }
    }

    @Test
    fun clearingSearchRestoresAllFriendsAfterFilteredFriendStateUpdate() = runBlocking {
        SharedFlowCentre.emitLogout()
        val account = AccountDto(userId = "usr_directory_owner", username = "directory-owner")
        SharedFlowCentre.emitAuthenticated(account)
        val session = assertNotNull(SharedFlowCentre.currentSession.value)
        val json = Json { ignoreUnknownKeys = true }
        var nonFriendProfileRequests = 0
        val client = testClient(json) { nonFriendProfileRequests++ }
        val friendListCacheStore = InMemoryFriendListCacheStore()
        val favoriteListCacheStore = InMemoryFavoriteListCacheStore()
        val accountCacheManager = AccountCacheManager(
            friendListCacheStore = friendListCacheStore,
            userProfileCacheStore = InMemoryUserProfileCacheStore(),
            friendActivityStore = io.github.vrcmteam.vrcm.storage.NoOpFriendActivityCacheStore,
            meetupCardConfigDao = MeetupCardConfigDao(MapSettings()),
            meetupCardAssetStore = MeetupCardAssetStore(
                FakeFileSystem(),
                "/meetup-assets".toPath(),
            ),
            favoriteListCacheStore = favoriteListCacheStore,
        )
        val authService = AuthService(
            authApi = AuthApi(client),
            accountDao = AccountDao(MapSettings(), InMemorySecureStorage()).also {
                it.saveAccountInfo(account)
            },
            cookiesStorage = PersistentCookiesStorage(EmptyLogger()),
            accountCacheManager = accountCacheManager,
        )
        val friendService = FriendService(
            friendsApi = FriendsApi(client),
            authService = authService,
            json = json,
            friendListCacheStore = friendListCacheStore,
            accountCacheManager = accountCacheManager,
            logger = EmptyLogger(),
        )
        val favoriteService = FavoriteService(
            favoriteApi = FavoriteApi(client),
            favoriteLocalDao = FavoriteLocalDao(MapSettings()),
        )
        val profileScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val model = FriendListPagerModel(
            userProfileEnrichmentService = UserProfileEnrichmentService(UsersApi(client), profileScope),
            friendService = friendService,
            authService = authService,
            favoriteService = favoriteService,
            worldsApi = WorldsApi(client),
            avatarsApi = AvatarsApi(client),
            favoriteListCacheStore = favoriteListCacheStore,
            accountCacheManager = accountCacheManager,
        )
        model.activateFriendDirectory()

        try {
            awaitUntil {
                friendService.initialRefreshCompleted.value && !model.directoryRefreshing.value
            }
            emitFriendUntilObserved(
                friendService,
                session,
                userId = "usr_alice",
                event = activeFriendEvent(json, "usr_alice", "Alice"),
            )
            emitFriendUntilObserved(
                friendService,
                session,
                userId = "usr_bob",
                event = activeFriendEvent(json, "usr_bob", "Bob"),
            )
            awaitFriendIds(model, setOf("usr_alice", "usr_bob"))

            model.setFriendDirectorySearchText("Alice")
            awaitFriendIds(model, setOf("usr_alice"))

            emitFriendUntilObserved(
                friendService,
                session,
                userId = "usr_charlie",
                event = activeFriendEvent(json, "usr_charlie", "Charlie"),
            )
            awaitUntil { model.friendTotal.value == 3 }

            model.setFriendDirectorySearchText("")
            val expectedIds = setOf("usr_alice", "usr_bob", "usr_charlie")
            awaitFriendIds(model, expectedIds)

            assertEquals(
                expectedIds,
                model.friendDirectoryFriends.value.mapTo(mutableSetOf()) { it.id },
            )

            val remoteGroup = model.friendFavoriteGroupsFlow.value.keys.single {
                it.ownerId != "local"
            }
            model.updateFriendGroupOptions(FriendGroupOptions(remoteGroup))
            model.setSearchText("No matching friend")
            awaitUntil { model.friendList.value.isEmpty() }

            assertEquals(0, nonFriendProfileRequests)
        } finally {
            profileScope.cancel()
            ViewModelStore().apply {
                put("friend-list-pager", model)
                clear()
            }
            friendService.dispose()
            favoriteService.dispose()
            SharedFlowCentre.emitLogout()
            client.close()
        }
    }

    @Test
    fun offlineSnapshotPublishesBeforeEnrichmentAndLateProfileCannotCrossAccounts() = runBlocking {
        SharedFlowCentre.emitLogout()
        val accountA = AccountDto(userId = "usr_directory_owner_a", username = "directory-owner-a")
        val accountB = AccountDto(userId = "usr_directory_owner_b", username = "directory-owner-b")
        val accountAFriend = cachedFriend(
            id = "usr_account_a_friend",
            displayName = "Account A Friend",
            status = UserStatus.Offline,
        )
        val accountBFriend = cachedFriend(
            id = "usr_account_b_friend",
            displayName = "Account B Friend",
            status = UserStatus.Active,
        )
        val json = Json { ignoreUnknownKeys = true }
        val accountAProfileStarted = CompletableDeferred<Unit>()
        val releaseAccountAProfile = CompletableDeferred<Unit>()
        val accountAProfileJob = CompletableDeferred<Job>()
        val accountANetworkStarted = CompletableDeferred<Unit>()
        val releaseAccountANetwork = CompletableDeferred<Unit>()
        val client = accountSwitchClient(
            json = json,
            accountAUserId = accountA.userId,
            accountBFriend = accountBFriend,
            accountAProfileStarted = accountAProfileStarted,
            releaseAccountAProfile = releaseAccountAProfile,
            accountAProfileJob = accountAProfileJob,
            accountANetworkStarted = accountANetworkStarted,
            releaseAccountANetwork = releaseAccountANetwork,
        )
        val friendListCacheStore = InMemoryFriendListCacheStore()
        val favoriteListCacheStore = InMemoryFavoriteListCacheStore()
        val accountCacheManager = AccountCacheManager(
            friendListCacheStore = friendListCacheStore,
            userProfileCacheStore = InMemoryUserProfileCacheStore(),
            friendActivityStore = io.github.vrcmteam.vrcm.storage.NoOpFriendActivityCacheStore,
            meetupCardConfigDao = MeetupCardConfigDao(MapSettings()),
            meetupCardAssetStore = MeetupCardAssetStore(
                FakeFileSystem(),
                "/meetup-assets".toPath(),
            ),
            favoriteListCacheStore = favoriteListCacheStore,
        )
        val authService = AuthService(
            authApi = AuthApi(client),
            accountDao = AccountDao(MapSettings(), InMemorySecureStorage()).also {
                it.saveAccountInfo(accountA)
            },
            cookiesStorage = PersistentCookiesStorage(EmptyLogger()),
            accountCacheManager = accountCacheManager,
        )
        val friendService = FriendService(
            friendsApi = FriendsApi(client),
            authService = authService,
            json = json,
            friendListCacheStore = friendListCacheStore,
            accountCacheManager = accountCacheManager,
            logger = EmptyLogger(),
        )
        val favoriteService = FavoriteService(
            favoriteApi = FavoriteApi(client),
            favoriteLocalDao = FavoriteLocalDao(MapSettings()),
        )
        val profileScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        var model: FriendListPagerModel? = null

        try {
            val activeModel = FriendListPagerModel(
                userProfileEnrichmentService = UserProfileEnrichmentService(UsersApi(client), profileScope),
                friendService = friendService,
                authService = authService,
                favoriteService = favoriteService,
                worldsApi = WorldsApi(client),
                avatarsApi = AvatarsApi(client),
                favoriteListCacheStore = favoriteListCacheStore,
                accountCacheManager = accountCacheManager,
            ).also { model = it }
            friendListCacheStore.save(accountA.userId, FriendListCache(listOf(accountAFriend)))

            SharedFlowCentre.emitAuthenticated(accountA)
            withTimeout(3_000) { accountAProfileStarted.await() }
            withTimeout(3_000) { accountANetworkStarted.await() }
            val oldProfileJob = withTimeout(3_000) { accountAProfileJob.await() }
            assertEquals(
                listOf("usr_account_a_friend"),
                activeModel.friendDirectoryFriends.value.map { it.id },
            )

            releaseAccountANetwork.complete(Unit)
            awaitUntil {
                friendService.initialRefreshCompleted.value &&
                    !friendService.isRefreshing.value &&
                    friendService.friendState.value.isEmpty()
            }

            SharedFlowCentre.emitAuthenticated(accountB)
            val sessionB = assertNotNull(SharedFlowCentre.currentSession.value)
            awaitUntil {
                friendService.friendState.value.isEmpty() &&
                    activeModel.friendDirectoryFriends.value.isEmpty()
            }
            emitFriendUntilObserved(
                friendService,
                sessionB,
                userId = "usr_account_b_friend",
                event = activeFriendEvent(json, "usr_account_b_friend", "Account B Friend"),
            )
            awaitFriendIds(activeModel, setOf("usr_account_b_friend"))

            releaseAccountAProfile.complete(Unit)
            withTimeout(3_000) { oldProfileJob.join() }

            assertEquals(
                listOf("usr_account_b_friend"),
                activeModel.friendDirectoryFriends.value.map { it.id },
            )
            assertEquals("", activeModel.friendDirectoryFriends.value.single().statusDescription)
        } finally {
            profileScope.cancel()
            releaseAccountAProfile.complete(Unit)
            releaseAccountANetwork.complete(Unit)
            model?.let { activeModel ->
                ViewModelStore().apply {
                    put("friend-list-pager", activeModel)
                    clear()
                }
            }
            friendService.dispose()
            favoriteService.dispose()
            SharedFlowCentre.emitLogout()
            client.close()
        }
    }

    @Test
    fun batchRemovalKeepsIndependentResultsAndLimitsConcurrentRequests() = runBlocking {
        SharedFlowCentre.emitLogout()
        val account = AccountDto(userId = "usr_removal_owner", username = "removal-owner")
        SharedFlowCentre.emitAuthenticated(account)
        val session = assertNotNull(SharedFlowCentre.currentSession.value)
        val json = Json { ignoreUnknownKeys = true }
        val friendIds = (1..5).map { "usr_removal_$it" }
        val failedId = friendIds[1]
        val activeRequests = atomic(0)
        val maxActiveRequests = atomic(0)
        val requestCounts = friendIds.associateWith { atomic(0) }
        val firstBatchStarted = CompletableDeferred<Unit>()
        val releaseRequests = CompletableDeferred<Unit>()
        val client = HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    val path = request.url.encodedPath
                    when {
                        request.method == HttpMethod.Delete && path.startsWith("/auth/user/friends/") -> {
                            val userId = path.substringAfterLast('/')
                            requestCounts.getValue(userId).incrementAndGet()
                            val active = activeRequests.incrementAndGet()
                            while (true) {
                                val currentMax = maxActiveRequests.value
                                if (active <= currentMax ||
                                    maxActiveRequests.compareAndSet(currentMax, active)
                                ) break
                            }
                            if (activeRequests.value == 3) firstBatchStarted.complete(Unit)
                            releaseRequests.await()
                            activeRequests.decrementAndGet()
                            if (userId == failedId) {
                                respond("failed", HttpStatusCode.InternalServerError)
                            } else {
                                jsonResponse(successResponseJson())
                            }
                        }
                        path == "/auth/user/friends" -> jsonResponse("[]")
                        path == "/auth/user/favoritelimits" -> jsonResponse(favoriteLimitsJson())
                        path == "/favorites" || path == "/favorite/groups" -> jsonResponse("[]")
                        path == "/auth/user" -> respond("unavailable", HttpStatusCode.InternalServerError)
                        else -> error("Unexpected request: ${request.url}")
                    }
                }
            }
            install(ContentNegotiation) { json(json) }
        }
        val fixture = createRemovalFixture(account, client, json)

        try {
            fixture.model.activateFriendDirectory()
            friendIds.forEachIndexed { index, userId ->
                emitFriendUntilObserved(
                    fixture.friendService,
                    session,
                    userId,
                    activeFriendEvent(json, userId, "Removal Friend $index"),
                )
            }
            awaitFriendIds(fixture.model, friendIds.toSet())

            fixture.model.enterFriendSelectionMode()
            fixture.model.requestFriendRemovalConfirmation()
            assertFalse(fixture.model.friendRemovalState.value.confirmationVisible)

            fixture.model.toggleVisibleFriendSelection(friendIds.toSet())
            fixture.model.requestFriendRemovalConfirmation()
            assertTrue(fixture.model.friendRemovalState.value.confirmationVisible)
            fixture.model.confirmFriendRemoval()
            fixture.model.confirmFriendRemoval()
            fixture.model.requestFriendRemovalConfirmation()

            withTimeout(3_000) { firstBatchStarted.await() }
            repeat(20) { yield() }
            assertEquals(3, activeRequests.value)
            assertEquals(3, maxActiveRequests.value)
            assertTrue(fixture.model.friendRemovalState.value.isSubmitting)

            releaseRequests.complete(Unit)
            awaitUntil {
                val state = fixture.model.friendRemovalState.value
                !state.isSubmitting && state.completedCount == friendIds.size
            }

            val state = fixture.model.friendRemovalState.value
            assertEquals(4, state.successCount)
            assertEquals(1, state.failureCount)
            assertEquals(friendIds.toSet(), state.results.keys)
            assertTrue(state.results.getValue(failedId).errorMessage?.isNotBlank() == true)
            assertEquals(setOf(failedId), state.selectedUserIds)
            assertTrue(state.selectionMode)
            assertEquals(setOf(failedId), fixture.friendService.friendState.value.keys)
            assertTrue(requestCounts.values.all { it.value == 1 })
            assertTrue(maxActiveRequests.value <= 3)
        } finally {
            releaseRequests.complete(Unit)
            fixture.close()
            SharedFlowCentre.emitLogout()
        }
    }

    @Test
    fun accountSwitchCancelsRemovalAndRejectsLateSuccess() = runBlocking {
        SharedFlowCentre.emitLogout()
        val accountA = AccountDto(userId = "usr_removal_owner_a", username = "removal-owner-a")
        val accountB = AccountDto(userId = "usr_removal_owner_b", username = "removal-owner-b")
        SharedFlowCentre.emitAuthenticated(accountA)
        val sessionA = assertNotNull(SharedFlowCentre.currentSession.value)
        val json = Json { ignoreUnknownKeys = true }
        val oldFriendId = "usr_removal_old_friend"
        val newFriendId = "usr_removal_new_friend"
        val oldRequestStarted = CompletableDeferred<Unit>()
        val releaseOldRequest = CompletableDeferred<Unit>()
        val client = HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    val path = request.url.encodedPath
                    when {
                        request.method == HttpMethod.Delete && path.endsWith("/$oldFriendId") -> {
                            oldRequestStarted.complete(Unit)
                            withContext(NonCancellable) { releaseOldRequest.await() }
                            jsonResponse(successResponseJson())
                        }
                        path == "/auth/user/friends" -> jsonResponse("[]")
                        path == "/auth/user/favoritelimits" -> jsonResponse(favoriteLimitsJson())
                        path == "/favorites" || path == "/favorite/groups" -> jsonResponse("[]")
                        path == "/auth/user" -> respond("unavailable", HttpStatusCode.InternalServerError)
                        else -> error("Unexpected request: ${request.url}")
                    }
                }
            }
            install(ContentNegotiation) { json(json) }
        }
        val fixture = createRemovalFixture(accountA, client, json)

        try {
            fixture.model.activateFriendDirectory()
            emitFriendUntilObserved(
                fixture.friendService,
                sessionA,
                oldFriendId,
                activeFriendEvent(json, oldFriendId, "Old Session Friend"),
            )
            awaitFriendIds(fixture.model, setOf(oldFriendId))
            fixture.model.enterFriendSelectionMode()
            fixture.model.toggleFriendSelection(oldFriendId)
            fixture.model.requestFriendRemovalConfirmation()
            fixture.model.confirmFriendRemoval()
            withTimeout(3_000) { oldRequestStarted.await() }

            SharedFlowCentre.emitAuthenticated(accountB)
            val sessionB = assertNotNull(SharedFlowCentre.currentSession.value)
            awaitUntil { fixture.model.friendRemovalState.value == FriendRemovalState() }
            emitFriendUntilObserved(
                fixture.friendService,
                sessionB,
                newFriendId,
                activeFriendEvent(json, newFriendId, "New Session Friend"),
            )
            awaitFriendIds(fixture.model, setOf(newFriendId))

            releaseOldRequest.complete(Unit)
            repeat(20) { yield() }

            assertEquals(FriendRemovalState(), fixture.model.friendRemovalState.value)
            assertEquals(setOf(newFriendId), fixture.friendService.friendState.value.keys)
            assertEquals(setOf(newFriendId), fixture.model.friendDirectoryFriends.value.map { it.id }.toSet())
        } finally {
            releaseOldRequest.complete(Unit)
            fixture.close()
            SharedFlowCentre.emitLogout()
        }
    }

    @Test
    fun unauthorizedBatchRemovalDoesNotReauthenticateAfterAccountSwitch() = runBlocking {
        SharedFlowCentre.emitLogout()
        val accountA = AccountDto(
            userId = "usr_removal_auth_owner_a",
            username = "removal-auth-owner-a",
            password = "password-a",
        )
        val accountB = AccountDto(
            userId = "usr_removal_auth_owner_b",
            username = "removal-auth-owner-b",
            password = "password-b",
        )
        SharedFlowCentre.emitAuthenticated(accountA)
        val sessionA = assertNotNull(SharedFlowCentre.currentSession.value)
        val json = Json { ignoreUnknownKeys = true }
        val oldFriendId = "usr_removal_auth_old_friend"
        val newFriendId = "usr_removal_auth_new_friend"
        val deleteRequests = atomic(0)
        val authenticationRequests = atomic(0)
        val firstDeleteStarted = CompletableDeferred<Unit>()
        val releaseFirstDelete = CompletableDeferred<Unit>()
        val client = HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    val path = request.url.encodedPath
                    when {
                        request.method == HttpMethod.Delete && path.endsWith("/$oldFriendId") -> {
                            val attempt = deleteRequests.incrementAndGet()
                            if (attempt == 1) {
                                firstDeleteStarted.complete(Unit)
                                releaseFirstDelete.await()
                                respond("expired", HttpStatusCode.Unauthorized)
                            } else {
                                jsonResponse(successResponseJson())
                            }
                        }
                        path == "/auth/user/friends" -> jsonResponse("[]")
                        path == "/auth/user/favoritelimits" -> jsonResponse(favoriteLimitsJson())
                        path == "/favorites" || path == "/favorite/groups" -> jsonResponse("[]")
                        path == "/auth/user" -> {
                            if (request.headers[HttpHeaders.Authorization] != null) {
                                authenticationRequests.incrementAndGet()
                            }
                            respond("unavailable", HttpStatusCode.InternalServerError)
                        }
                        else -> error("Unexpected request: ${request.url}")
                    }
                }
            }
            install(ContentNegotiation) { json(json) }
        }
        val fixture = createRemovalFixture(accountA, client, json)

        try {
            emitFriendUntilObserved(
                fixture.friendService,
                sessionA,
                oldFriendId,
                activeFriendEvent(json, oldFriendId, "Old Auth Friend"),
            )
            val removal = async {
                fixture.friendService.unfriendBatch(listOf(oldFriendId)).getValue(oldFriendId)
            }
            withTimeout(3_000) { firstDeleteStarted.await() }

            fixture.accountDao.saveAccountInfo(accountB)
            SharedFlowCentre.emitAuthenticated(accountB)
            val sessionB = assertNotNull(SharedFlowCentre.currentSession.value)
            emitFriendUntilObserved(
                fixture.friendService,
                sessionB,
                newFriendId,
                activeFriendEvent(json, newFriendId, "New Auth Friend"),
            )
            releaseFirstDelete.complete(Unit)

            assertTrue(removal.await().isFailure)
            repeat(20) { yield() }
            assertEquals(1, deleteRequests.value)
            assertEquals(0, authenticationRequests.value)
            assertEquals(setOf(newFriendId), fixture.friendService.friendState.value.keys)
        } finally {
            releaseFirstDelete.complete(Unit)
            fixture.close()
            SharedFlowCentre.emitLogout()
        }
    }

    private fun createRemovalFixture(
        account: AccountDto,
        client: HttpClient,
        json: Json,
    ): RemovalFixture {
        val friendListCacheStore = InMemoryFriendListCacheStore()
        val favoriteListCacheStore = InMemoryFavoriteListCacheStore()
        val accountCacheManager = AccountCacheManager(
            friendListCacheStore = friendListCacheStore,
            userProfileCacheStore = InMemoryUserProfileCacheStore(),
            friendActivityStore = io.github.vrcmteam.vrcm.storage.NoOpFriendActivityCacheStore,
            meetupCardConfigDao = MeetupCardConfigDao(MapSettings()),
            meetupCardAssetStore = MeetupCardAssetStore(
                FakeFileSystem(),
                "/meetup-assets".toPath(),
            ),
            favoriteListCacheStore = favoriteListCacheStore,
        )
        val accountDao = AccountDao(MapSettings(), InMemorySecureStorage()).also {
            it.saveAccountInfo(account)
        }
        val authService = AuthService(
            authApi = AuthApi(client),
            accountDao = accountDao,
            cookiesStorage = PersistentCookiesStorage(EmptyLogger()),
            accountCacheManager = accountCacheManager,
        )
        val friendService = FriendService(
            friendsApi = FriendsApi(client),
            authService = authService,
            json = json,
            friendListCacheStore = friendListCacheStore,
            accountCacheManager = accountCacheManager,
            logger = EmptyLogger(),
        )
        val favoriteService = FavoriteService(
            favoriteApi = FavoriteApi(client),
            favoriteLocalDao = FavoriteLocalDao(MapSettings()),
        )
        val profileScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val model = FriendListPagerModel(
            userProfileEnrichmentService = UserProfileEnrichmentService(UsersApi(client), profileScope),
            friendService = friendService,
            authService = authService,
            favoriteService = favoriteService,
            worldsApi = WorldsApi(client),
            avatarsApi = AvatarsApi(client),
            favoriteListCacheStore = favoriteListCacheStore,
            accountCacheManager = accountCacheManager,
        )
        return RemovalFixture(model, friendService, favoriteService, accountDao, profileScope, client)
    }

    private data class RemovalFixture(
        val model: FriendListPagerModel,
        val friendService: FriendService,
        val favoriteService: FavoriteService,
        val accountDao: AccountDao,
        val profileScope: CoroutineScope,
        val client: HttpClient,
    ) {
        fun close() {
            profileScope.cancel()
            ViewModelStore().apply {
                put("friend-list-pager-removal", model)
                clear()
            }
            friendService.dispose()
            favoriteService.dispose()
            client.close()
        }
    }

    private suspend fun emitFriendUntilObserved(
        friendService: FriendService,
        session: AuthenticatedAccount,
        userId: String,
        event: WebSocketEvent,
    ) {
        withTimeout(3_000) {
            while (userId !in friendService.friendState.value) {
                SharedFlowCentre.emitWebSocket(AccountWebSocketEvent(session.token, event))
                yield()
            }
        }
    }

    private suspend fun awaitFriendIds(model: FriendListPagerModel, expectedIds: Set<String>) {
        awaitUntil {
            model.friendDirectoryFriends.value.mapTo(mutableSetOf()) { it.id } == expectedIds
        }
    }

    private suspend fun awaitUntil(predicate: () -> Boolean) {
        withTimeout(3_000) {
            while (!predicate()) yield()
        }
    }

    private fun testClient(
        json: Json,
        onNonFriendProfileRequest: () -> Unit,
    ) = HttpClient(MockEngine) {
        engine {
            addHandler { request ->
                when (request.url.encodedPath) {
                    "/auth/user/favoritelimits" -> jsonResponse(favoriteLimitsJson())
                    "/auth/user/friends" -> jsonResponse("[]")
                    "/favorites" -> jsonResponse(
                        if (request.url.parameters["offset"] == "0") friendFavoritesJson() else "[]"
                    )
                    "/favorite/groups" -> jsonResponse(
                        if (request.url.parameters["offset"] == "0") friendFavoriteGroupsJson() else "[]"
                    )
                    "/users/usr_non_friend" -> {
                        onNonFriendProfileRequest()
                        jsonResponse(nonFriendUserJson())
                    }
                    "/auth/user" -> respond("unavailable", HttpStatusCode.InternalServerError)
                    else -> error("Unexpected request: ${request.url}")
                }
            }
        }
        install(ContentNegotiation) { json(json) }
    }

    private fun activeFriendEvent(json: Json, userId: String, displayName: String) = WebSocketEvent(
        type = FriendEvents.FriendActive.typeName,
        content = json.encodeToString(
            FriendActiveContent(
                userId = userId,
                user = UserContent(
                    allowAvatarCopying = true,
                    bio = null,
                    bioLinks = emptyList(),
                    currentAvatarImageUrl = "",
                    currentAvatarTags = emptyList(),
                    currentAvatarThumbnailImageUrl = "",
                    dateJoined = "",
                    developerType = "none",
                    displayName = displayName,
                    friendKey = "",
                    id = userId,
                    isFriend = true,
                    lastActivity = "",
                    lastLogin = "",
                    lastPlatform = "web",
                    profilePicOverride = "",
                    state = "active",
                    status = UserStatus.Active,
                    statusDescription = "",
                    tags = emptyList(),
                    userIcon = "",
                    pronouns = null,
                ),
            )
        ),
    )

}

private fun accountSwitchClient(
    json: Json,
    accountAUserId: String,
    accountBFriend: FriendData,
    accountAProfileStarted: CompletableDeferred<Unit>,
    releaseAccountAProfile: CompletableDeferred<Unit>,
    accountAProfileJob: CompletableDeferred<Job>,
    accountANetworkStarted: CompletableDeferred<Unit>,
    releaseAccountANetwork: CompletableDeferred<Unit>,
) = HttpClient(MockEngine) {
    engine {
        addHandler { request ->
            when (request.url.encodedPath) {
                "/auth/user/favoritelimits" -> jsonResponse(favoriteLimitsJson())
                "/auth/user/friends" -> {
                    val offline = request.url.parameters["offline"] == "true"
                    val offset = request.url.parameters["offset"]?.toIntOrNull() ?: 0
                    val requestUserId = SharedFlowCentre.currentSession.value?.account?.userId
                    val friends = when {
                        offline || offset != 0 -> emptyList()
                        requestUserId == accountAUserId -> {
                            accountANetworkStarted.complete(Unit)
                            withContext(NonCancellable) { releaseAccountANetwork.await() }
                            emptyList()
                        }
                        else -> listOf(accountBFriend)
                    }
                    jsonResponse(json.encodeToString(friends))
                }
                "/favorites", "/favorite/groups" -> jsonResponse("[]")
                "/users/usr_account_a_friend" -> {
                    accountAProfileJob.complete(currentCoroutineContext().job)
                    accountAProfileStarted.complete(Unit)
                    withContext(NonCancellable) { releaseAccountAProfile.await() }
                    jsonResponse(accountAProfileJson())
                }
                "/auth/user" -> respond("unavailable", HttpStatusCode.InternalServerError)
                else -> error("Unexpected request: ${request.url}")
            }
        }
    }
    install(ContentNegotiation) { json(json) }
}

private fun cachedFriend(
    id: String,
    displayName: String,
    status: UserStatus,
) = FriendData(
    bio = null,
    currentAvatarImageUrl = "",
    currentAvatarThumbnailImageUrl = "",
    developerType = "none",
    displayName = displayName,
    friendKey = "",
    id = id,
    imageUrl = "",
    isFriend = true,
    lastLogin = "",
    lastActivity = "",
    lastPlatform = "web",
    location = "offline",
    profilePicOverride = "",
    status = status,
    statusDescription = "",
    userIcon = "",
    pronouns = null,
)

private fun MockRequestHandleScope.jsonResponse(content: String) = respond(
    content = content,
    status = HttpStatusCode.OK,
    headers = headersOf(HttpHeaders.ContentType, "application/json"),
)

private fun successResponseJson() = """
    {"success":{"message":"Friend removed","status_code":200}}
""".trimIndent()

private fun favoriteLimitsJson() = """
    {
      "maxFavoriteGroups":{"avatar":1,"friend":1,"world":1},
      "maxFavoritesPerGroup":{"avatar":100,"friend":100,"world":100},
      "defaultMaxFavoriteGroups":1,
      "defaultMaxFavoritesPerGroup":100
    }
""".trimIndent()

private fun friendFavoritesJson() = """
    [{
      "favoriteId":"usr_non_friend","id":"fvrt_non_friend",
      "tags":["group_0"],"type":"friend"
    }]
""".trimIndent()

private fun friendFavoriteGroupsJson() = """
    [{
      "id":"grp_friend","ownerId":"usr_directory_owner","type":"friend",
      "visibility":"private","displayName":"Friends","name":"group_0",
      "ownerDisplayName":"directory-owner","tags":[]
    }]
""".trimIndent()

private fun nonFriendUserJson() = """
    {
      "ageVerificationStatus":"verified","allowAvatarCopying":true,
      "bio":"","bioLinks":[],"currentAvatarImageUrl":"",
      "currentAvatarTags":[],"currentAvatarThumbnailImageUrl":"",
      "date_joined":"","developerType":"none","displayName":"Former Friend",
      "friendKey":"","friendRequestStatus":"null","id":"usr_non_friend",
      "instanceId":"","isFriend":false,"last_activity":"","last_login":"",
      "last_platform":"web","location":"offline","note":"",
      "profilePicOverride":"","state":"offline","status":"offline",
      "statusDescription":"","tags":[],"travelingToInstance":null,
      "travelingToLocation":null,"travelingToWorld":null,"userIcon":"",
      "worldId":"","pronouns":null
    }
""".trimIndent()

private fun accountAProfileJson() = """
    {
      "ageVerificationStatus":"verified","allowAvatarCopying":true,
      "bio":"","bioLinks":[],"currentAvatarImageUrl":"",
      "currentAvatarTags":[],"currentAvatarThumbnailImageUrl":"",
      "date_joined":"","developerType":"none","displayName":"Account A Friend",
      "friendKey":"","friendRequestStatus":"null","id":"usr_account_a_friend",
      "instanceId":"","isFriend":true,"last_activity":"","last_login":"",
      "last_platform":"web","location":"offline","note":"",
      "profilePicOverride":"","state":"offline","status":"offline",
      "statusDescription":"Account A private status","tags":[],"travelingToInstance":null,
      "travelingToLocation":null,"travelingToWorld":null,"userIcon":"",
      "worldId":"","pronouns":null
    }
""".trimIndent()
