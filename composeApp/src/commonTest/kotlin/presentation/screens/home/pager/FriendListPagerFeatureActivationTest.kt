package io.github.vrcmteam.vrcm.presentation.screens.home.pager

import androidx.lifecycle.ViewModelStore
import com.russhwolf.settings.MapSettings
import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.di.supports.PersistentCookiesStorage
import io.github.vrcmteam.vrcm.network.api.attributes.FavoriteType
import io.github.vrcmteam.vrcm.network.api.auth.AuthApi
import io.github.vrcmteam.vrcm.network.api.avatars.AvatarsApi
import io.github.vrcmteam.vrcm.network.api.favorite.FavoriteApi
import io.github.vrcmteam.vrcm.network.api.friends.FriendsApi
import io.github.vrcmteam.vrcm.network.api.users.UsersApi
import io.github.vrcmteam.vrcm.network.api.worlds.WorldsApi
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
import io.github.vrcmteam.vrcm.storage.NoOpFriendActivityCacheStore
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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
class FriendListPagerFeatureActivationTest : MainDispatcherTest() {
    @Test
    fun directoryActivationAndReentryLoadGroupsWithoutDuplicatingGlobalFriendRefresh() = runBlocking {
        val fixture = createFixture()
        try {
            fixture.model.activateFavoritesPage()
            fixture.awaitFavoritesRefresh()

            assertEquals(0, fixture.requests.favoriteGroups(FavoriteType.Friend))
            assertEquals(fixture.friendListsBeforeFeatures, fixture.requests.friendLists)

            fixture.model.activateFriendDirectory()
            fixture.awaitFriendDirectoryRefresh()

            assertEquals(1, fixture.requests.favoriteGroups(FavoriteType.Friend))
            assertEquals(fixture.friendListsBeforeFeatures, fixture.requests.friendLists)

            fixture.model.activateFriendDirectory()
            awaitUntil {
                fixture.requests.favoriteGroups(FavoriteType.Friend) > 1 &&
                    !fixture.model.directoryRefreshing.value
            }
            assertEquals(fixture.friendListsBeforeFeatures, fixture.requests.friendLists)

            fixture.model.refreshFriendDirectory()
            awaitUntil { fixture.requests.friendLists > fixture.friendListsBeforeFeatures }
        } finally {
            fixture.close()
        }
    }

    @Test
    fun accountSwitchRestartsOnlyActivatedFeatures() = runBlocking {
        val fixture = createFixture()
        try {
            fixture.model.activateFavoritesPage()
            fixture.awaitFavoritesRefresh()
            val worldGroupsBeforeSwitch = fixture.requests.favoriteGroups(FavoriteType.World)
            val avatarGroupsBeforeSwitch = fixture.requests.favoriteGroups(FavoriteType.Avatar)
            val worldsBeforeSwitch = fixture.requests.worldLists
            val avatarsBeforeSwitch = fixture.requests.avatarLists

            SharedFlowCentre.emitAuthenticated(AccountDto(userId = "usr_b", username = "b"))
            awaitUntil {
                fixture.requests.favoriteGroups(FavoriteType.World) > worldGroupsBeforeSwitch &&
                    fixture.requests.favoriteGroups(FavoriteType.Avatar) > avatarGroupsBeforeSwitch &&
                    fixture.requests.worldLists > worldsBeforeSwitch &&
                    fixture.requests.avatarLists > avatarsBeforeSwitch &&
                    1 !in fixture.model.refreshingTabs.value &&
                    2 !in fixture.model.refreshingTabs.value &&
                    !fixture.model.directoryRefreshing.value
            }

            assertEquals(0, fixture.requests.favoriteGroups(FavoriteType.Friend))

            fixture.model.activateFriendDirectory()
            fixture.awaitFriendDirectoryRefresh()
            val friendGroupsBeforeSecondSwitch = fixture.requests.favoriteGroups(FavoriteType.Friend)
            val worldGroupsBeforeSecondSwitch = fixture.requests.favoriteGroups(FavoriteType.World)
            val avatarGroupsBeforeSecondSwitch = fixture.requests.favoriteGroups(FavoriteType.Avatar)
            val worldsBeforeSecondSwitch = fixture.requests.worldLists
            val avatarsBeforeSecondSwitch = fixture.requests.avatarLists

            SharedFlowCentre.emitAuthenticated(AccountDto(userId = "usr_c", username = "c"))
            awaitUntil {
                fixture.requests.favoriteGroups(FavoriteType.Friend) > friendGroupsBeforeSecondSwitch &&
                    fixture.requests.favoriteGroups(FavoriteType.World) > worldGroupsBeforeSecondSwitch &&
                    fixture.requests.favoriteGroups(FavoriteType.Avatar) > avatarGroupsBeforeSecondSwitch &&
                    fixture.requests.worldLists > worldsBeforeSecondSwitch &&
                    fixture.requests.avatarLists > avatarsBeforeSecondSwitch &&
                    1 !in fixture.model.refreshingTabs.value &&
                    2 !in fixture.model.refreshingTabs.value &&
                    !fixture.model.directoryRefreshing.value
            }
        } finally {
            fixture.close()
        }
    }

    @Test
    fun repeatedDirectoryActivationKeepsSingleGroupRefreshInFlight() = runBlocking {
        val fixture = createFixture(blockFirstFriendFavoriteGroupRequest = true)
        try {
            fixture.model.activateFriendDirectory()
            fixture.awaitFriendFavoriteGroupRequest()
            val friendListsBeforeDirectoryRefresh = fixture.requests.friendLists

            fixture.model.activateFriendDirectory()
            fixture.releaseFriendFavoriteGroupRequest()
            fixture.awaitFriendDirectoryRefresh()

            assertEquals(1, fixture.requests.favoriteGroups(FavoriteType.Friend))
            assertEquals(friendListsBeforeDirectoryRefresh, fixture.requests.friendLists)
        } finally {
            fixture.close()
        }
    }
}

private class FeatureActivationFixture(
    val model: FriendListPagerModel,
    val requests: FeatureRequestRecorder,
    val friendListsBeforeFeatures: Int,
    private val friendService: FriendService,
    private val favoriteService: FavoriteService,
    private val profileScope: CoroutineScope,
    private val client: HttpClient,
) {
    suspend fun awaitFavoritesRefresh() {
        awaitUntil {
            requests.favoriteGroups(FavoriteType.World) > 0 &&
                requests.favoriteGroups(FavoriteType.Avatar) > 0 &&
                1 !in model.refreshingTabs.value &&
                2 !in model.refreshingTabs.value &&
                !model.directoryRefreshing.value
        }
    }

    suspend fun awaitFriendDirectoryRefresh() {
        awaitUntil {
            requests.favoriteGroups(FavoriteType.Friend) > 0 &&
                !model.directoryRefreshing.value
        }
    }

    suspend fun awaitFriendFavoriteGroupRequest() {
        requests.awaitFriendFavoriteGroupRequest()
    }

    fun releaseFriendFavoriteGroupRequest() {
        requests.releaseFriendFavoriteGroupRequest()
    }

    suspend fun close() {
        requests.releaseFriendFavoriteGroupRequest()
        ViewModelStore().apply {
            put("feature-activation", model)
            clear()
        }
        friendService.dispose()
        favoriteService.dispose()
        profileScope.cancel()
        SharedFlowCentre.emitLogout()
        client.close()
    }
}

private suspend fun createFixture(
    blockFirstFriendFavoriteGroupRequest: Boolean = false,
): FeatureActivationFixture {
    SharedFlowCentre.emitLogout()
    val account = AccountDto(userId = "usr_a", username = "a")
    SharedFlowCentre.emitAuthenticated(account)
    val requests = FeatureRequestRecorder(blockFirstFriendFavoriteGroupRequest)
    val json = Json { ignoreUnknownKeys = true }
    val client = featureActivationClient(requests, json)
    val accountDao = AccountDao(MapSettings(), InMemorySecureStorage()).also {
        it.saveAccountInfo(account)
    }
    val friendListCacheStore = InMemoryFriendListCacheStore()
    val favoriteListCacheStore = InMemoryFavoriteListCacheStore()
    val accountCacheManager = AccountCacheManager(
        friendListCacheStore = friendListCacheStore,
        userProfileCacheStore = InMemoryUserProfileCacheStore(),
        friendActivityStore = NoOpFriendActivityCacheStore,
        meetupCardConfigDao = MeetupCardConfigDao(MapSettings()),
        meetupCardAssetStore = MeetupCardAssetStore(FakeFileSystem(), "/assets".toPath()),
        favoriteListCacheStore = favoriteListCacheStore,
    )
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
    awaitUntil { friendService.initialRefreshCompleted.value }
    val friendListsBeforeFeatures = requests.friendLists
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
    return FeatureActivationFixture(
        model = model,
        requests = requests,
        friendListsBeforeFeatures = friendListsBeforeFeatures,
        friendService = friendService,
        favoriteService = favoriteService,
        profileScope = profileScope,
        client = client,
    )
}

private class FeatureRequestRecorder(
    private val blockFirstFriendFavoriteGroupRequest: Boolean,
) {
    private val friendFavoriteGroups = atomic(0)
    private val worldFavoriteGroups = atomic(0)
    private val avatarFavoriteGroups = atomic(0)
    private val friendListRequests = atomic(0)
    private val worldListRequests = atomic(0)
    private val avatarListRequests = atomic(0)
    private val firstFriendFavoriteGroupRequestStarted = CompletableDeferred<Unit>()
    private val releaseFirstFriendFavoriteGroupRequest = CompletableDeferred<Unit>()

    val friendLists: Int get() = friendListRequests.value
    val worldLists: Int get() = worldListRequests.value
    val avatarLists: Int get() = avatarListRequests.value

    suspend fun recordFavoriteGroup(type: FavoriteType) {
        when (type) {
            FavoriteType.Friend -> {
                val requestNumber = friendFavoriteGroups.incrementAndGet()
                if (blockFirstFriendFavoriteGroupRequest && requestNumber == 1) {
                    firstFriendFavoriteGroupRequestStarted.complete(Unit)
                    releaseFirstFriendFavoriteGroupRequest.await()
                }
            }
            FavoriteType.World -> worldFavoriteGroups.incrementAndGet()
            FavoriteType.Avatar -> avatarFavoriteGroups.incrementAndGet()
        }
    }

    suspend fun awaitFriendFavoriteGroupRequest() {
        withTimeout(3_000) { firstFriendFavoriteGroupRequestStarted.await() }
    }

    fun releaseFriendFavoriteGroupRequest() {
        releaseFirstFriendFavoriteGroupRequest.complete(Unit)
    }

    fun recordFriendList() {
        friendListRequests.incrementAndGet()
    }

    fun recordWorldList() {
        worldListRequests.incrementAndGet()
    }

    fun recordAvatarList() {
        avatarListRequests.incrementAndGet()
    }

    fun favoriteGroups(type: FavoriteType): Int = when (type) {
        FavoriteType.Friend -> friendFavoriteGroups.value
        FavoriteType.World -> worldFavoriteGroups.value
        FavoriteType.Avatar -> avatarFavoriteGroups.value
    }
}

private fun featureActivationClient(
    requests: FeatureRequestRecorder,
    json: Json,
) = HttpClient(MockEngine) {
    engine {
        addHandler { request ->
            when (request.url.encodedPath) {
                "/auth/user/friends" -> {
                    requests.recordFriendList()
                    jsonResponse("[]")
                }
                "/favorite/groups" -> {
                    requests.recordFavoriteGroup(
                        FavoriteType.entries.single { it.value == request.url.parameters["type"] },
                    )
                    jsonResponse("[]")
                }
                "/favorites" -> jsonResponse("[]")
                "/worlds/favorites" -> {
                    requests.recordWorldList()
                    jsonResponse("[]")
                }
                "/avatars/favorites" -> {
                    requests.recordAvatarList()
                    jsonResponse("[]")
                }
                "/auth/user/favoritelimits" -> jsonResponse(FAVORITE_LIMITS_JSON)
                "/auth/user" -> respond("unavailable", HttpStatusCode.InternalServerError)
                else -> error("Unexpected request: ${request.url}")
            }
        }
    }
    install(ContentNegotiation) { json(json) }
}

private fun io.ktor.client.engine.mock.MockRequestHandleScope.jsonResponse(content: String) = respond(
    content = content,
    status = HttpStatusCode.OK,
    headers = headersOf(HttpHeaders.ContentType, "application/json"),
)

private suspend fun awaitUntil(predicate: () -> Boolean) {
    withTimeout(3_000) {
        while (!predicate()) yield()
    }
}

private const val FAVORITE_LIMITS_JSON = """
    {
      "maxFavoriteGroups":{"avatar":4,"friend":3,"world":4},
      "maxFavoritesPerGroup":{"avatar":100,"friend":100,"world":100},
      "defaultMaxFavoriteGroups":4,
      "defaultMaxFavoritesPerGroup":100
    }
"""
