package io.github.vrcmteam.vrcm.service

import com.russhwolf.settings.MapSettings
import io.github.vrcmteam.vrcm.core.shared.AccountWebSocketEvent
import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.di.supports.PersistentCookiesStorage
import io.github.vrcmteam.vrcm.network.api.attributes.LocationType
import io.github.vrcmteam.vrcm.network.api.attributes.UserStatus
import io.github.vrcmteam.vrcm.network.api.auth.AuthApi
import io.github.vrcmteam.vrcm.network.api.friends.FriendsApi
import io.github.vrcmteam.vrcm.network.api.friends.date.FriendData
import io.github.vrcmteam.vrcm.network.websocket.data.WebSocketEvent
import io.github.vrcmteam.vrcm.network.websocket.data.content.FriendOfflineContent
import io.github.vrcmteam.vrcm.network.websocket.data.type.FriendEvents
import io.github.vrcmteam.vrcm.service.data.AccountDto
import io.github.vrcmteam.vrcm.storage.AccountCacheManager
import io.github.vrcmteam.vrcm.storage.AccountDao
import io.github.vrcmteam.vrcm.storage.FriendListCacheStore
import io.github.vrcmteam.vrcm.storage.InMemoryFriendListCacheStore
import io.github.vrcmteam.vrcm.storage.InMemorySecureStorage
import io.github.vrcmteam.vrcm.storage.InMemoryUserProfileCacheStore
import io.github.vrcmteam.vrcm.storage.NoOpFriendActivityCacheStore
import io.github.vrcmteam.vrcm.storage.data.FriendListCache
import io.github.vrcmteam.vrcm.storage.meetup.MeetupCardAssetStore
import io.github.vrcmteam.vrcm.storage.meetup.MeetupCardConfigDao
import io.github.vrcmteam.vrcm.testing.MainDispatcherTest
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.koin.core.logger.EmptyLogger
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class FriendOfflineLastActivityRefreshTest : MainDispatcherTest() {
    @Test
    fun offlineSocketRefreshesLastActivityFromOfflineFriendsEndpoint() = runBlocking {
        SharedFlowCentre.emitLogout()
        val account = AccountDto(userId = "usr_owner", username = "owner")
        SharedFlowCentre.emitAuthenticated(account)
        val session = assertNotNull(SharedFlowCentre.currentSession.value)
        val friend = friend()
        val json = Json { ignoreUnknownKeys = true }
        val serveUpdatedOfflineFriend = atomic(false)
        val offlineRequestCount = atomic(0)
        val client = HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    when (request.url.encodedPath) {
                        "/auth/user/friends" -> {
                            val offline = request.url.parameters["offline"] == "true"
                            val offset = request.url.parameters["offset"]?.toIntOrNull() ?: 0
                            if (offline) offlineRequestCount.incrementAndGet()
                            val friends = when {
                                offset > 0 -> emptyList()
                                !offline -> listOf(friend)
                                serveUpdatedOfflineFriend.value -> listOf(
                                    friend.copy(
                                        lastActivity = UPDATED_LAST_ACTIVITY,
                                        location = LocationType.Offline.value,
                                        status = UserStatus.Offline,
                                    )
                                )
                                else -> emptyList()
                            }
                            respond(
                                content = json.encodeToString(friends),
                                status = HttpStatusCode.OK,
                                headers = headersOf(HttpHeaders.ContentType, "application/json"),
                            )
                        }
                        "/auth/user" -> respond(
                            content = "unavailable",
                            status = HttpStatusCode.InternalServerError,
                        )
                        else -> error("Unexpected request: ${request.url}")
                    }
                }
            }
            install(ContentNegotiation) { json(json) }
        }
        val friendListCacheStore = InMemoryFriendListCacheStore()
        val friendService = createFriendService(account, client, json, friendListCacheStore)

        try {
            withTimeout(3_000L) {
                while (!friendService.initialRefreshCompleted.value) yield()
            }
            assertEquals(UserStatus.Active, friendService.friendState.value.getValue(friend.id).status)
            val initialOfflineRequestCount = offlineRequestCount.value
            serveUpdatedOfflineFriend.value = true
            val offlineEvent = AccountWebSocketEvent(
                token = session.token,
                event = WebSocketEvent(
                    type = FriendEvents.FriendOffline.typeName,
                    content = json.encodeToString(FriendOfflineContent(userId = friend.id)),
                ),
            )

            SharedFlowCentre.emitWebSocket(offlineEvent)
            withTimeout(3_000L) {
                while (friendService.friendLastActivitySource.value
                        ?.friends
                        ?.firstOrNull { it.id == friend.id }
                        ?.lastActivity != UPDATED_LAST_ACTIVITY
                ) {
                    yield()
                }
            }

            assertTrue(offlineRequestCount.value > initialOfflineRequestCount)
            assertEquals(
                UserStatus.Offline,
                friendService.friendLastActivitySource.value
                    ?.friends
                    ?.first { it.id == friend.id }
                    ?.status,
            )
        } finally {
            friendService.dispose()
            SharedFlowCentre.emitLogout()
            client.close()
        }
    }

    @Test
    fun completedRefreshRejectsCacheThatFinishesLater() = runBlocking {
        SharedFlowCentre.emitLogout()
        val account = AccountDto(userId = "usr_owner", username = "owner")
        SharedFlowCentre.emitAuthenticated(account)
        val liveFriend = friend()
        val cachedFriend = liveFriend.copy(
            displayName = "Cached Friend",
            location = LocationType.Offline.value,
            status = UserStatus.Offline,
        )
        val cacheStore = BlockingFriendListCacheStore(
            FriendListCache(friends = listOf(cachedFriend)),
        )
        val json = Json { ignoreUnknownKeys = true }
        val refreshAfterCacheStarted = CompletableDeferred<Unit>()
        val releaseRefreshAfterCache = CompletableDeferred<Unit>()
        val client = HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    when (request.url.encodedPath) {
                        "/auth/user/friends" -> {
                            val offline = request.url.parameters["offline"] == "true"
                            val offset = request.url.parameters["offset"]?.toIntOrNull() ?: 0
                            if (!offline && offset == 0 && cacheStore.releaseLoad.isCompleted) {
                                refreshAfterCacheStarted.complete(Unit)
                                releaseRefreshAfterCache.await()
                            }
                            respond(
                                content = json.encodeToString(
                                    if (!offline && offset == 0) listOf(liveFriend) else emptyList(),
                                ),
                                status = HttpStatusCode.OK,
                                headers = headersOf(HttpHeaders.ContentType, "application/json"),
                            )
                        }
                        "/auth/user" -> respond(
                            content = "unavailable",
                            status = HttpStatusCode.InternalServerError,
                        )
                        else -> error("Unexpected request: ${request.url}")
                    }
                }
            }
            install(ContentNegotiation) { json(json) }
        }
        val friendService = createFriendService(account, client, json, cacheStore)

        try {
            withTimeout(3_000L) { cacheStore.loadStarted.await() }
            assertTrue(friendService.refreshFriendList())
            assertLiveFriend(friendService, liveFriend)

            cacheStore.releaseLoad.complete(Unit)
            withTimeout(3_000L) { refreshAfterCacheStarted.await() }
            assertLiveFriend(friendService, liveFriend)

            releaseRefreshAfterCache.complete(Unit)
            withTimeout(3_000L) {
                while (friendService.friendState.value[liveFriend.id]?.status != UserStatus.Active) {
                    yield()
                }
            }
        } finally {
            cacheStore.releaseLoad.complete(Unit)
            releaseRefreshAfterCache.complete(Unit)
            friendService.dispose()
            SharedFlowCentre.emitLogout()
            client.close()
        }
    }

    @Test
    fun previousSessionRefreshCannotCommitWhileNewSessionCacheIsLoading() = runBlocking {
        SharedFlowCentre.emitLogout()
        val accountA = AccountDto(userId = "usr_owner_a", username = "owner-a")
        val accountB = AccountDto(userId = "usr_owner_b", username = "owner-b")
        SharedFlowCentre.emitAuthenticated(accountA)
        val friendA = friend().copy(id = "usr_friend_a", displayName = "Friend A")
        val friendB = friend().copy(id = "usr_friend_b", displayName = "Friend B")
        val cacheStore = SwitchingFriendListCacheStore(blockedUserId = accountB.userId)
        val json = Json { ignoreUnknownKeys = true }
        val releaseAccountARefresh = CompletableDeferred<Unit>()
        val accountBRefreshStarted = CompletableDeferred<Unit>()
        val releaseAccountBRefresh = CompletableDeferred<Unit>()
        val onlineRefreshCount = atomic(0)
        val client = HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    when (request.url.encodedPath) {
                        "/auth/user/friends" -> {
                            val offline = request.url.parameters["offline"] == "true"
                            val offset = request.url.parameters["offset"]?.toIntOrNull() ?: 0
                            val friends = if (!offline && offset == 0) {
                                when (onlineRefreshCount.incrementAndGet()) {
                                    1 -> {
                                        releaseAccountARefresh.await()
                                        listOf(friendA)
                                    }
                                    2 -> {
                                        accountBRefreshStarted.complete(Unit)
                                        releaseAccountBRefresh.await()
                                        listOf(friendB)
                                    }
                                    else -> listOf(friendB)
                                }
                            } else {
                                emptyList()
                            }
                            respond(
                                content = json.encodeToString(friends),
                                status = HttpStatusCode.OK,
                                headers = headersOf(HttpHeaders.ContentType, "application/json"),
                            )
                        }
                        "/auth/user" -> respond(
                            content = "unavailable",
                            status = HttpStatusCode.InternalServerError,
                        )
                        else -> error("Unexpected request: ${request.url}")
                    }
                }
            }
            install(ContentNegotiation) { json(json) }
        }
        val friendService = createFriendService(accountA, client, json, cacheStore)
        var accountBRefresh: Deferred<Boolean>? = null

        try {
            withTimeout(3_000L) {
                while (onlineRefreshCount.value == 0) yield()
            }
            SharedFlowCentre.emitAuthenticated(accountB)
            withTimeout(3_000L) { cacheStore.blockedLoadStarted.await() }
            val refresh = async { friendService.refreshFriendList() }
            accountBRefresh = refresh

            releaseAccountARefresh.complete(Unit)
            withTimeout(3_000L) { accountBRefreshStarted.await() }

            assertTrue(friendService.friendState.value.values.none { it.id == friendA.id })
            assertTrue(
                friendService.friendActivitySource.value
                    ?.friends
                    .orEmpty()
                    .none { it.id == friendA.id },
            )
            assertTrue(
                friendService.friendLastActivitySource.value
                    ?.friends
                    .orEmpty()
                    .none { it.id == friendA.id },
            )
            assertTrue(cacheStore.saved(accountB.userId)?.friends.orEmpty().none { it.id == friendA.id })

            releaseAccountBRefresh.complete(Unit)
            assertTrue(refresh.await())
            assertEquals(friendB, friendService.friendState.value[friendB.id])
        } finally {
            releaseAccountARefresh.complete(Unit)
            releaseAccountBRefresh.complete(Unit)
            cacheStore.releaseBlockedLoad.complete(Unit)
            accountBRefresh?.cancel()
            friendService.dispose()
            SharedFlowCentre.emitLogout()
            client.close()
        }
    }

    @Test
    fun cacheLoadFailureStillStartsInitialNetworkRefresh() = runBlocking {
        SharedFlowCentre.emitLogout()
        val account = AccountDto(userId = "usr_owner", username = "owner")
        SharedFlowCentre.emitAuthenticated(account)
        val liveFriend = friend()
        val json = Json { ignoreUnknownKeys = true }
        val client = HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    when (request.url.encodedPath) {
                        "/auth/user/friends" -> {
                            val offline = request.url.parameters["offline"] == "true"
                            val offset = request.url.parameters["offset"]?.toIntOrNull() ?: 0
                            respond(
                                content = json.encodeToString(
                                    if (!offline && offset == 0) listOf(liveFriend) else emptyList(),
                                ),
                                status = HttpStatusCode.OK,
                                headers = headersOf(HttpHeaders.ContentType, "application/json"),
                            )
                        }
                        "/auth/user" -> respond(
                            content = "unavailable",
                            status = HttpStatusCode.InternalServerError,
                        )
                        else -> error("Unexpected request: ${request.url}")
                    }
                }
            }
            install(ContentNegotiation) { json(json) }
        }
        val friendService = createFriendService(
            account = account,
            client = client,
            json = json,
            friendListCacheStore = ThrowingFriendListCacheStore,
        )

        try {
            withTimeout(3_000L) {
                while (!friendService.initialRefreshCompleted.value ||
                    friendService.friendState.value[liveFriend.id] != liveFriend ||
                    friendService.friendActivitySource.value
                        ?.friends
                        ?.singleOrNull { it.id == liveFriend.id } != liveFriend
                ) {
                    yield()
                }
            }
        } finally {
            friendService.dispose()
            SharedFlowCentre.emitLogout()
            client.close()
        }
    }

    private suspend fun createFriendService(
        account: AccountDto,
        client: HttpClient,
        json: Json,
        friendListCacheStore: FriendListCacheStore,
    ): FriendService {
        val accountDao = AccountDao(MapSettings(), InMemorySecureStorage()).also {
            it.saveAccountInfo(account)
        }
        val cacheManager = AccountCacheManager(
            friendListCacheStore = friendListCacheStore,
            userProfileCacheStore = InMemoryUserProfileCacheStore(),
            friendActivityStore = NoOpFriendActivityCacheStore,
            meetupCardConfigDao = MeetupCardConfigDao(MapSettings()),
            meetupCardAssetStore = MeetupCardAssetStore(
                FakeFileSystem(),
                "/meetup-assets".toPath(),
            ),
        )
        val authService = AuthService(
            authApi = AuthApi(client),
            accountDao = accountDao,
            cookiesStorage = PersistentCookiesStorage(EmptyLogger()),
            accountCacheManager = cacheManager,
        )
        return FriendService(
            friendsApi = FriendsApi(client),
            authService = authService,
            json = json,
            friendListCacheStore = friendListCacheStore,
            accountCacheManager = cacheManager,
            logger = EmptyLogger(),
        )
    }

    private fun assertLiveFriend(friendService: FriendService, liveFriend: FriendData) {
        assertEquals(liveFriend, friendService.friendState.value[liveFriend.id])
        assertEquals(
            liveFriend,
            friendService.friendActivitySource.value
                ?.friends
                ?.single { it.id == liveFriend.id },
        )
    }

    private fun friend() = FriendData(
        bio = null,
        currentAvatarImageUrl = "",
        currentAvatarThumbnailImageUrl = "",
        developerType = "none",
        displayName = "Friend",
        friendKey = "",
        id = "usr_friend",
        imageUrl = "",
        isFriend = true,
        lastLogin = "",
        lastActivity = "",
        lastPlatform = "standalonewindows",
        location = "wrld_world:instance_a",
        profilePicOverride = "",
        status = UserStatus.Active,
        statusDescription = "",
        userIcon = "",
        pronouns = null,
    )

    private companion object {
        const val UPDATED_LAST_ACTIVITY = "2026-08-10T17:15:00.000Z"
    }
}

private class BlockingFriendListCacheStore(
    private var cache: FriendListCache?,
) : FriendListCacheStore {
    val loadStarted = CompletableDeferred<Unit>()
    val releaseLoad = CompletableDeferred<Unit>()

    override suspend fun load(userId: String): FriendListCache? {
        val result = cache
        loadStarted.complete(Unit)
        releaseLoad.await()
        return result
    }

    override suspend fun save(userId: String, cache: FriendListCache) {
        this.cache = cache
    }

    override suspend fun clear(userId: String) {
        cache = null
    }

    override suspend fun clearAll() {
        cache = null
    }
}

private class SwitchingFriendListCacheStore(
    private val blockedUserId: String,
) : FriendListCacheStore {
    val blockedLoadStarted = CompletableDeferred<Unit>()
    val releaseBlockedLoad = CompletableDeferred<Unit>()
    private val entries = atomic<Map<String, FriendListCache>>(emptyMap())

    override suspend fun load(userId: String): FriendListCache? {
        val result = entries.value[userId]
        if (userId == blockedUserId) {
            blockedLoadStarted.complete(Unit)
            releaseBlockedLoad.await()
        }
        return result
    }

    override suspend fun save(userId: String, cache: FriendListCache) {
        entries.value = entries.value + (userId to cache)
    }

    fun saved(userId: String): FriendListCache? = entries.value[userId]

    override suspend fun clear(userId: String) {
        entries.value = entries.value - userId
    }

    override suspend fun clearAll() {
        entries.value = emptyMap()
    }
}

private object ThrowingFriendListCacheStore : FriendListCacheStore {
    override suspend fun load(userId: String): FriendListCache =
        error("cache read failed")

    override suspend fun save(userId: String, cache: FriendListCache) = Unit

    override suspend fun clear(userId: String) = Unit

    override suspend fun clearAll() = Unit
}
