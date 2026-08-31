package io.github.vrcmteam.vrcm.presentation.screens.home.pager

import androidx.lifecycle.ViewModelStore
import com.russhwolf.settings.MapSettings
import io.github.vrcmteam.vrcm.core.shared.AccountWebSocketEvent
import io.github.vrcmteam.vrcm.core.shared.AuthenticatedAccount
import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.di.supports.PersistentCookiesStorage
import io.github.vrcmteam.vrcm.network.api.attributes.UserStatus
import io.github.vrcmteam.vrcm.network.api.auth.AuthApi
import io.github.vrcmteam.vrcm.network.api.avatars.AvatarsApi
import io.github.vrcmteam.vrcm.network.api.favorite.FavoriteApi
import io.github.vrcmteam.vrcm.network.api.friends.FriendsApi
import io.github.vrcmteam.vrcm.network.api.users.UsersApi
import io.github.vrcmteam.vrcm.network.api.worlds.WorldsApi
import io.github.vrcmteam.vrcm.network.websocket.data.WebSocketEvent
import io.github.vrcmteam.vrcm.network.websocket.data.content.FriendActiveContent
import io.github.vrcmteam.vrcm.network.websocket.data.content.FriendOfflineContent
import io.github.vrcmteam.vrcm.network.websocket.data.content.UserContent
import io.github.vrcmteam.vrcm.network.websocket.data.type.FriendEvents
import io.github.vrcmteam.vrcm.service.AuthService
import io.github.vrcmteam.vrcm.service.FavoriteService
import io.github.vrcmteam.vrcm.service.FriendService
import io.github.vrcmteam.vrcm.service.UserProfileEnrichmentService
import io.github.vrcmteam.vrcm.service.data.AccountDto
import io.github.vrcmteam.vrcm.storage.AccountCacheManager
import io.github.vrcmteam.vrcm.storage.AccountDao
import io.github.vrcmteam.vrcm.storage.FavoriteLocalDao
import io.github.vrcmteam.vrcm.storage.InMemoryFavoriteListCacheStore
import io.github.vrcmteam.vrcm.storage.InMemoryFriendListCacheStore
import io.github.vrcmteam.vrcm.storage.InMemorySecureStorage
import io.github.vrcmteam.vrcm.storage.InMemoryUserProfileCacheStore
import io.github.vrcmteam.vrcm.storage.meetup.MeetupCardAssetStore
import io.github.vrcmteam.vrcm.storage.meetup.MeetupCardConfigDao
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
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
import kotlin.test.assertNotNull

class FriendListPagerModelTest : MainDispatcherTest() {
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
    fun lateOfflineProfileFromPreviousAccountCannotReplaceCurrentFriendSnapshot() = runBlocking {
        SharedFlowCentre.emitLogout()
        val accountA = AccountDto(userId = "usr_directory_owner_a", username = "directory-owner-a")
        val accountB = AccountDto(userId = "usr_directory_owner_b", username = "directory-owner-b")
        SharedFlowCentre.emitAuthenticated(accountA)
        val sessionA = assertNotNull(SharedFlowCentre.currentSession.value)
        val json = Json { ignoreUnknownKeys = true }
        val accountAProfileStarted = CompletableDeferred<Unit>()
        val releaseAccountAProfile = CompletableDeferred<Unit>()
        val accountAProfileJob = CompletableDeferred<Job>()
        val client = accountSwitchClient(
            json = json,
            accountAProfileStarted = accountAProfileStarted,
            releaseAccountAProfile = releaseAccountAProfile,
            accountAProfileJob = accountAProfileJob,
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
            awaitUntil {
                friendService.initialRefreshCompleted.value && !model.directoryRefreshing.value
            }
            emitFriendUntilObserved(
                friendService,
                sessionA,
                userId = "usr_account_a_friend",
                event = activeFriendEvent(json, "usr_account_a_friend", "Account A Friend"),
            )
            awaitFriendIds(model, setOf("usr_account_a_friend"))

            SharedFlowCentre.emitWebSocket(
                AccountWebSocketEvent(
                    sessionA.token,
                    offlineFriendEvent(json, "usr_account_a_friend"),
                )
            )
            withTimeout(3_000) { accountAProfileStarted.await() }
            val oldProfileJob = withTimeout(3_000) { accountAProfileJob.await() }

            SharedFlowCentre.emitAuthenticated(accountB)
            val sessionB = assertNotNull(SharedFlowCentre.currentSession.value)
            awaitUntil {
                friendService.friendState.value.isEmpty() &&
                    model.friendDirectoryFriends.value.isEmpty()
            }
            emitFriendUntilObserved(
                friendService,
                sessionB,
                userId = "usr_account_b_friend",
                event = activeFriendEvent(json, "usr_account_b_friend", "Account B Friend"),
            )
            awaitFriendIds(model, setOf("usr_account_b_friend"))

            releaseAccountAProfile.complete(Unit)
            withTimeout(3_000) { oldProfileJob.join() }

            assertEquals(
                listOf("usr_account_b_friend"),
                model.friendDirectoryFriends.value.map { it.id },
            )
            assertEquals("", model.friendDirectoryFriends.value.single().statusDescription)
        } finally {
            profileScope.cancel()
            releaseAccountAProfile.complete(Unit)
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

    private fun offlineFriendEvent(json: Json, userId: String) = WebSocketEvent(
        type = FriendEvents.FriendOffline.typeName,
        content = json.encodeToString(FriendOfflineContent(userId = userId)),
    )
}

private fun accountSwitchClient(
    json: Json,
    accountAProfileStarted: CompletableDeferred<Unit>,
    releaseAccountAProfile: CompletableDeferred<Unit>,
    accountAProfileJob: CompletableDeferred<Job>,
) = HttpClient(MockEngine) {
    engine {
        addHandler { request ->
            when (request.url.encodedPath) {
                "/auth/user/favoritelimits" -> jsonResponse(favoriteLimitsJson())
                "/auth/user/friends", "/favorites", "/favorite/groups" -> jsonResponse("[]")
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

private fun MockRequestHandleScope.jsonResponse(content: String) = respond(
    content = content,
    status = HttpStatusCode.OK,
    headers = headersOf(HttpHeaders.ContentType, "application/json"),
)

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
