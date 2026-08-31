package io.github.vrcmteam.vrcm.presentation.screens.home.pager

import androidx.lifecycle.ViewModelStore
import com.russhwolf.settings.MapSettings
import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.di.supports.PersistentCookiesStorage
import io.github.vrcmteam.vrcm.network.api.attributes.FavoriteType
import io.github.vrcmteam.vrcm.network.api.auth.AuthApi
import io.github.vrcmteam.vrcm.network.api.avatars.AvatarsApi
import io.github.vrcmteam.vrcm.network.api.avatars.data.AvatarData
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
import io.github.vrcmteam.vrcm.storage.FavoriteListCacheStore
import io.github.vrcmteam.vrcm.storage.FavoriteLocalDao
import io.github.vrcmteam.vrcm.storage.InMemoryFavoriteListCacheStore
import io.github.vrcmteam.vrcm.storage.InMemoryFriendListCacheStore
import io.github.vrcmteam.vrcm.storage.InMemorySecureStorage
import io.github.vrcmteam.vrcm.storage.InMemoryUserProfileCacheStore
import io.github.vrcmteam.vrcm.storage.NoOpFriendActivityCacheStore
import io.github.vrcmteam.vrcm.storage.data.FavoriteListCache
import io.github.vrcmteam.vrcm.storage.data.FavoritedWorldGroup
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
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import kotlinx.serialization.json.Json
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import org.koin.core.logger.EmptyLogger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class FavoriteGroupClearStateTest : MainDispatcherTest() {
    @Test
    fun failedRequestKeepsMembersAndRetryableConfirmationWhileDuplicateConfirmIsIgnored() = runBlocking {
        val fixture = createClearFixture(ClearScenario.FailureThenSuccess)
        try {
            val group = fixture.remoteGroup()
            val localGroup = fixture.favoriteService.favoritesByGroup(FavoriteType.Avatar).value.keys
                .single { it.ownerId == "local" }
            fixture.model.openFavoriteGroupClearConfirmation(localGroup)
            fixture.model.openFavoriteGroupClearConfirmation(group.copy(ownerId = "usr_other"))
            assertNull(fixture.model.favoriteGroupClearState.value.group)

            fixture.model.updateAvatarGroupOptions(AvatarGroupOptions(group))
            fixture.model.openFavoriteGroupClearConfirmation(group)
            assertEquals(1, fixture.model.favoriteGroupClearState.value.itemCount)
            fixture.model.confirmFavoriteGroupClear()
            fixture.requests.firstDeleteStarted.await()
            assertTrue(fixture.model.favoriteGroupClearState.value.isClearing)

            fixture.model.confirmFavoriteGroupClear()
            yield()
            assertEquals(1, fixture.requests.deleteCount.value)

            fixture.requests.releaseFirstDelete.complete(Unit)
            awaitClearCondition {
                fixture.model.favoriteGroupClearState.value.failure ==
                    FavoriteGroupClearFailure.RequestFailed
            }
            assertEquals(
                listOf("avtr_usr_owner"),
                fixture.favoriteService.favoritesByGroup(FavoriteType.Avatar).value
                    .getValue(group)
                    .map { it.favoriteId },
            )
            assertEquals(group, fixture.model.avatarGroupOptions.value.selectedGroup)

            fixture.model.confirmFavoriteGroupClear()
            awaitClearCondition { fixture.model.favoriteGroupClearState.value.group == null }
            assertEquals(2, fixture.requests.deleteCount.value)
            assertEquals(emptyList(), fixture.favoriteService.favoritesByGroup(FavoriteType.Avatar).value[group])
            assertEquals(group, fixture.model.avatarGroupOptions.value.selectedGroup)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun unauthorizedRequestReauthenticatesAndCommitsWithRenewedSession() = runBlocking {
        val fixture = createClearFixture(ClearScenario.UnauthorizedThenSuccess)
        try {
            val firstSession = assertNotNull(SharedFlowCentre.currentSession.value)
            val group = fixture.remoteGroup()
            fixture.model.openFavoriteGroupClearConfirmation(group)
            fixture.model.confirmFavoriteGroupClear()
            awaitClearCondition { fixture.model.favoriteGroupClearState.value.group == null }

            val renewedSession = assertNotNull(SharedFlowCentre.currentSession.value)
            assertEquals(2, fixture.requests.deleteCount.value)
            assertEquals(1, fixture.requests.authenticationCount.value)
            assertEquals(firstSession.token.generation, fixture.requests.firstDeleteGeneration.value)
            assertNotEquals(firstSession.token, renewedSession.token)
            assertEquals(renewedSession.token.generation, fixture.requests.secondDeleteGeneration.value)
            assertEquals(emptyList(), fixture.favoriteService.favoritesByGroup(FavoriteType.Avatar).value[group])
        } finally {
            fixture.close()
        }
    }

    @Test
    fun externalSessionRenewalRejectsLateCommitAndReconcilesBeforeUnlocking() = runBlocking {
        val fixture = createClearFixture(ClearScenario.SessionRenewalBeforeSuccess)
        try {
            val firstSession = assertNotNull(SharedFlowCentre.currentSession.value)
            val group = fixture.remoteGroup()
            fixture.model.openFavoriteGroupClearConfirmation(group)
            fixture.model.confirmFavoriteGroupClear()
            fixture.requests.firstDeleteStarted.await()

            SharedFlowCentre.emitAuthenticated(firstSession.account)
            val renewedSession = assertNotNull(SharedFlowCentre.currentSession.value)
            assertNotEquals(firstSession.token, renewedSession.token)
            fixture.requests.releaseFirstDelete.complete(Unit)
            awaitClearCondition { fixture.model.favoriteGroupClearState.value.group == null }

            assertEquals(1, fixture.requests.deleteCount.value)
            assertTrue(fixture.requests.reloadAfterClearCount.value > 0)
            assertEquals(
                listOf("avtr_usr_owner"),
                fixture.favoriteService.favoritesByGroup(FavoriteType.Avatar).value[group]
                    ?.map { it.favoriteId },
            )
            fixture.model.openFavoriteGroupClearConfirmation(group)
            fixture.model.confirmFavoriteGroupClear()
            yield()
            assertNull(fixture.model.favoriteGroupClearState.value.group)
            assertEquals(1, fixture.requests.deleteCount.value)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun accountSwitchDuringLateSuccessCannotClearTheNewAccountsGroup() = runBlocking {
        val fixture = createClearFixture(ClearScenario.SwitchAccountBeforeSuccess)
        try {
            val group = fixture.remoteGroup()
            fixture.model.openFavoriteGroupClearConfirmation(group)
            fixture.model.confirmFavoriteGroupClear()
            fixture.requests.firstDeleteStarted.await()

            val nextAccount = AccountDto(
                userId = "usr_second",
                username = "second",
                password = "second-password",
                authCookie = "second-auth",
            )
            SharedFlowCentre.emitAuthenticated(nextAccount)
            awaitClearCondition { fixture.model.favoriteGroupClearState.value.group == null }
            fixture.requests.releaseFirstDelete.complete(Unit)
            fixture.requests.firstDeleteResponded.await()

            assertTrue(fixture.favoriteService.loadFavoriteByGroup(FavoriteType.Avatar).isSuccess)
            val current = fixture.favoriteService.favoritesByGroup(FavoriteType.Avatar).value.entries
                .single { it.key.ownerId != "local" }
            assertEquals(nextAccount.userId, current.key.ownerId)
            assertEquals(listOf("avtr_usr_second"), current.value.map { it.favoriteId })
            assertEquals(1, fixture.requests.deleteCount.value)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun cacheWriteFailureAfterRemoteSuccessIsTerminalAndDoesNotRepeatDelete() = runBlocking {
        val fixture = createClearFixture(
            scenario = ClearScenario.SuccessWithCacheFailure,
            rejectAvatarCacheWrites = true,
        )
        try {
            val group = fixture.remoteGroup()
            fixture.model.openFavoriteGroupClearConfirmation(group)
            fixture.model.confirmFavoriteGroupClear()
            awaitClearCondition { fixture.model.favoriteGroupClearState.value.group == null }

            assertEquals(1, fixture.requests.deleteCount.value)
            assertEquals(emptyList(), fixture.favoriteService.favoritesByGroup(FavoriteType.Avatar).value[group])
            fixture.model.openFavoriteGroupClearConfirmation(group)
            fixture.model.confirmFavoriteGroupClear()
            yield()
            assertNull(fixture.model.favoriteGroupClearState.value.group)
            assertEquals(1, fixture.requests.deleteCount.value)
        } finally {
            fixture.close()
        }
    }
}

private class ClearFixture(
    val model: FriendListPagerModel,
    val favoriteService: FavoriteService,
    val requests: ClearRequests,
    private val friendService: FriendService,
    private val profileScope: CoroutineScope,
    private val client: HttpClient,
) {
    fun remoteGroup() = favoriteService.favoritesByGroup(FavoriteType.Avatar).value.keys
        .single { it.ownerId != "local" }

    suspend fun close() {
        requests.releaseFirstDelete.complete(Unit)
        ViewModelStore().apply {
            put("favorite-group-clear", model)
            clear()
        }
        friendService.dispose()
        favoriteService.dispose()
        profileScope.cancel()
        SharedFlowCentre.emitLogout()
        client.close()
    }
}

private enum class ClearScenario {
    FailureThenSuccess,
    UnauthorizedThenSuccess,
    SessionRenewalBeforeSuccess,
    SwitchAccountBeforeSuccess,
    SuccessWithCacheFailure,
}

private class ClearRequests(val scenario: ClearScenario) {
    val deleteCount = atomic(0)
    val authenticationCount = atomic(0)
    val reloadAfterClearCount = atomic(0)
    val clearedOwnerId = atomic("")
    val firstDeleteGeneration = atomic(-1L)
    val secondDeleteGeneration = atomic(-1L)
    val firstDeleteStarted = CompletableDeferred<Unit>()
    val releaseFirstDelete = CompletableDeferred<Unit>()
    val firstDeleteResponded = CompletableDeferred<Unit>()
}

private suspend fun createClearFixture(
    scenario: ClearScenario,
    rejectAvatarCacheWrites: Boolean = false,
): ClearFixture {
    SharedFlowCentre.emitLogout()
    val account = AccountDto(
        userId = "usr_owner",
        username = "owner",
        password = "owner-password",
        authCookie = "owner-auth",
    )
    SharedFlowCentre.emitAuthenticated(account)
    val requests = ClearRequests(scenario)
    val json = Json { ignoreUnknownKeys = true }
    val client = clearClient(requests, json)
    val friendCache = InMemoryFriendListCacheStore()
    val favoriteCache: FavoriteListCacheStore = if (rejectAvatarCacheWrites) {
        RejectingAvatarCacheStore()
    } else {
        InMemoryFavoriteListCacheStore()
    }
    val accountCacheManager = AccountCacheManager(
        friendListCacheStore = friendCache,
        userProfileCacheStore = InMemoryUserProfileCacheStore(),
        friendActivityStore = NoOpFriendActivityCacheStore,
        meetupCardConfigDao = MeetupCardConfigDao(MapSettings()),
        meetupCardAssetStore = MeetupCardAssetStore(FakeFileSystem(), "/assets".toPath()),
        favoriteListCacheStore = favoriteCache,
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
        friendListCacheStore = friendCache,
        accountCacheManager = accountCacheManager,
        logger = EmptyLogger(),
    )
    val favoriteService = FavoriteService(
        favoriteApi = FavoriteApi(client),
        favoriteLocalDao = FavoriteLocalDao(MapSettings()),
    )
    assertTrue(favoriteService.loadFavoriteByGroup(FavoriteType.Avatar).isSuccess)
    val profileScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val model = FriendListPagerModel(
        userProfileEnrichmentService = UserProfileEnrichmentService(UsersApi(client), profileScope),
        friendService = friendService,
        authService = authService,
        favoriteService = favoriteService,
        worldsApi = WorldsApi(client),
        avatarsApi = AvatarsApi(client),
        favoriteListCacheStore = favoriteCache,
        accountCacheManager = accountCacheManager,
    )
    return ClearFixture(model, favoriteService, requests, friendService, profileScope, client)
}

private fun clearClient(requests: ClearRequests, json: Json) = HttpClient(MockEngine) {
    engine {
        addHandler { request ->
            when (request.url.encodedPath) {
                "/auth/user/favoritelimits" -> clearJsonResponse(CLEAR_LIMITS)
                "/auth/user/friends" -> clearJsonResponse("[]")
                "/favorites" -> {
                    val ownerId = SharedFlowCentre.currentSession.value?.token?.userId.orEmpty()
                    val isCleared = requests.clearedOwnerId.value == ownerId
                    val reloadNumber = if (isCleared) {
                        requests.reloadAfterClearCount.incrementAndGet()
                    } else {
                        0
                    }
                    val staleReload = requests.scenario == ClearScenario.SessionRenewalBeforeSuccess &&
                        reloadNumber == 1
                    if (request.url.parameters["offset"] == "0" && (!isCleared || staleReload)) {
                        clearJsonResponse(clearFavorites(ownerId))
                    } else {
                        clearJsonResponse("[]")
                    }
                }
                "/favorite/groups" -> if (request.url.parameters["offset"] == "0") {
                    val ownerId = SharedFlowCentre.currentSession.value?.token?.userId.orEmpty()
                    clearJsonResponse(clearGroups(ownerId))
                } else {
                    clearJsonResponse("[]")
                }
                "/auth/user" -> {
                    if (request.headers[HttpHeaders.Authorization] != null) {
                        requests.authenticationCount.incrementAndGet()
                    }
                    val currentAccount = SharedFlowCentre.currentSession.value?.account
                        ?: error("Authentication requested without an account")
                    clearJsonResponse(clearCurrentUser(currentAccount))
                }
                else -> {
                    check(request.url.encodedPath.startsWith("/favorite/group/avatar/avatars1/")) {
                        "Unexpected request: ${request.url}"
                    }
                    val deleteNumber = requests.deleteCount.incrementAndGet()
                    val generation = SharedFlowCentre.currentSession.value?.token?.generation ?: -1L
                    if (deleteNumber == 1) {
                        requests.firstDeleteGeneration.value = generation
                        requests.firstDeleteStarted.complete(Unit)
                    } else if (deleteNumber == 2) {
                        requests.secondDeleteGeneration.value = generation
                    }
                    when (requests.scenario) {
                        ClearScenario.FailureThenSuccess -> if (deleteNumber == 1) {
                            requests.releaseFirstDelete.await()
                            requests.firstDeleteResponded.complete(Unit)
                            respond("failed", HttpStatusCode.InternalServerError)
                        } else {
                            requests.clearedOwnerId.value = request.url.segments.last()
                            clearJsonResponse("")
                        }
                        ClearScenario.UnauthorizedThenSuccess -> if (deleteNumber == 1) {
                            requests.firstDeleteResponded.complete(Unit)
                            respond("expired", HttpStatusCode.Unauthorized)
                        } else {
                            requests.clearedOwnerId.value = request.url.segments.last()
                            clearJsonResponse("")
                        }
                        ClearScenario.SessionRenewalBeforeSuccess -> {
                            requests.releaseFirstDelete.await()
                            requests.clearedOwnerId.value = request.url.segments.last()
                            requests.firstDeleteResponded.complete(Unit)
                            clearJsonResponse("")
                        }
                        ClearScenario.SwitchAccountBeforeSuccess -> {
                            withContext(NonCancellable) { requests.releaseFirstDelete.await() }
                            requests.clearedOwnerId.value = request.url.segments.last()
                            requests.firstDeleteResponded.complete(Unit)
                            clearJsonResponse("")
                        }
                        ClearScenario.SuccessWithCacheFailure -> {
                            requests.clearedOwnerId.value = request.url.segments.last()
                            requests.firstDeleteResponded.complete(Unit)
                            clearJsonResponse("")
                        }
                    }
                }
            }
        }
    }
    install(ContentNegotiation) { json(json) }
}

private class RejectingAvatarCacheStore : FavoriteListCacheStore {
    private val delegate = InMemoryFavoriteListCacheStore()

    override suspend fun load(userId: String): FavoriteListCache? = delegate.load(userId)
    override suspend fun saveWorlds(userId: String, worlds: List<FavoritedWorldGroup>) =
        delegate.saveWorlds(userId, worlds)
    override suspend fun saveAvatars(userId: String, avatars: List<AvatarData>) {
        error("Avatar cache unavailable")
    }
    override suspend fun clear(userId: String) = delegate.clear(userId)
    override suspend fun clearAll() = delegate.clearAll()
}

private fun MockRequestHandleScope.clearJsonResponse(content: String) = respond(
    content = content,
    status = HttpStatusCode.OK,
    headers = headersOf(HttpHeaders.ContentType, "application/json"),
)

private suspend fun awaitClearCondition(predicate: () -> Boolean) {
    withTimeout(3_000) {
        while (!predicate()) yield()
    }
}

private const val CLEAR_LIMITS = """
    {
      "maxFavoriteGroups":{"avatar":4,"friend":3,"world":4},
      "maxFavoritesPerGroup":{"avatar":100,"friend":100,"world":100},
      "defaultMaxFavoriteGroups":4,
      "defaultMaxFavoritesPerGroup":100
    }
"""

private fun clearFavorites(ownerId: String) = """
    [{
      "favoriteId":"avtr_$ownerId","id":"fvrt_$ownerId",
      "tags":["avatars1"],"type":"avatar"
    }]
""".trimIndent()

private fun clearGroups(ownerId: String) = """
    [{
      "id":"grp_avatars1_$ownerId","ownerId":"$ownerId","type":"avatar",
      "visibility":"private","displayName":"Avatars","name":"avatars1",
      "ownerDisplayName":"$ownerId","tags":[]
    }]
""".trimIndent()

private fun clearCurrentUser(account: AccountDto) = """
    {
      "requiresTwoFactorAuth":null,
      "ageVerificationStatus":"verified","ageVerified":true,
      "acceptedPrivacyVersion":0,"acceptedTOSVersion":0,
      "accountDeletionDate":null,"accountDeletionLog":null,"activeFriends":[],
      "allowAvatarCopying":true,"bio":null,"bioLinks":[],
      "currentAvatar":"","currentAvatarAssetUrl":null,"currentAvatarImageUrl":"",
      "currentAvatarTags":[],"currentAvatarThumbnailImageUrl":"","date_joined":"",
      "developerType":"none","displayName":"${account.username}","emailVerified":true,
      "fallbackAvatar":"","friendGroupNames":[],"friendKey":"","friends":[],
      "googleId":"","hasBirthday":true,"hasEmail":true,
      "hasLoggedInFromClient":true,"hasPendingEmail":false,
      "hideContentFilterSettings":false,"homeLocation":"","id":"${account.userId}",
      "isFriend":false,"last_activity":"","last_login":"",
      "last_platform":"standalonewindows","obfuscatedEmail":"",
      "obfuscatedPendingEmail":"","oculusId":"","offlineFriends":[],
      "onlineFriends":[],"pastDisplayNames":[],"picoId":"",
      "presence":{
        "avatarThumbnail":null,"displayName":"${account.username}","groups":[],
        "id":"${account.userId}","instance":"","instanceType":"",
        "isRejoining":null,"platform":"standalonewindows","profilePicOverride":null,
        "status":"active","travelingToInstance":"","travelingToWorld":"","world":""
      },
      "profilePicOverride":"","state":"online","status":"active",
      "statusDescription":"","statusFirstTime":false,"statusHistory":[],
      "steamDetails":{},"steamId":"","tags":[],"twoFactorAuthEnabled":false,
      "twoFactorAuthEnabledDate":null,"unsubscribe":false,"updated_at":"",
      "userIcon":"","userLanguage":null,"userLanguageCode":null,
      "username":"${account.username}","viveId":"","pronouns":null
    }
""".trimIndent()
