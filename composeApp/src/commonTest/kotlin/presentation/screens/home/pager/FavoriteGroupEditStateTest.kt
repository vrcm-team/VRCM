package io.github.vrcmteam.vrcm.presentation.screens.home.pager

import androidx.lifecycle.ViewModelStore
import com.russhwolf.settings.MapSettings
import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.di.supports.PersistentCookiesStorage
import io.github.vrcmteam.vrcm.network.api.attributes.FavoriteGroupVisibility
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
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
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
class FavoriteGroupEditStateTest : MainDispatcherTest() {
    @Test
    fun failedSaveKeepsEditorRetryableAndSuccessfulRetryPublishesUpdatedGroup() = runBlocking {
        val fixture = createFavoriteGroupEditFixture()
        try {
            val group = fixture.favoriteService.favoritesByGroup(FavoriteType.Avatar).value.keys
                .single { it.ownerId != "local" }
            fixture.model.openFavoriteGroupEditor(group.copy(ownerId = "usr_other"))
            assertNull(fixture.model.favoriteGroupEditState.value.group)

            fixture.model.updateAvatarGroupOptions(AvatarGroupOptions(group))
            fixture.model.openFavoriteGroupEditor(group)
            assertEquals(group, fixture.model.favoriteGroupEditState.value.group)

            fixture.model.saveFavoriteGroup("   ", FavoriteGroupVisibility.Public)
            assertEquals(
                FavoriteGroupEditFailure.InvalidName,
                fixture.model.favoriteGroupEditState.value.failure,
            )
            assertEquals(0, fixture.requests.updateCount.value)

            fixture.model.saveFavoriteGroup(" Updated avatars ", FavoriteGroupVisibility.Friends)
            awaitUntil { fixture.requests.firstUpdateStarted.isCompleted }
            assertTrue(fixture.model.favoriteGroupEditState.value.isSaving)

            fixture.model.saveFavoriteGroup("Ignored duplicate", FavoriteGroupVisibility.Public)
            yield()
            assertEquals(1, fixture.requests.updateCount.value)

            fixture.requests.releaseFirstUpdate.complete(Unit)
            awaitUntil {
                fixture.model.favoriteGroupEditState.value.failure == FavoriteGroupEditFailure.SaveFailed
            }
            assertNotNull(fixture.model.favoriteGroupEditState.value.group)
            assertTrue(!fixture.model.favoriteGroupEditState.value.isSaving)

            fixture.model.saveFavoriteGroup(" Updated avatars ", FavoriteGroupVisibility.Friends)
            awaitUntil { fixture.model.favoriteGroupEditState.value.group == null }

            assertEquals(2, fixture.requests.updateCount.value)
            val updatedEntry = fixture.favoriteService.favoritesByGroup(FavoriteType.Avatar).value.entries
                .single { it.key.ownerId != "local" }
            assertEquals("Updated avatars", updatedEntry.key.displayName)
            assertEquals("friends", updatedEntry.key.visibility)
            assertEquals(listOf("avtr_usr_editor"), updatedEntry.value.map { it.favoriteId })
            assertEquals(updatedEntry.key, fixture.model.avatarGroupOptions.value.selectedGroup)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun unauthorizedSaveReauthenticatesAndCommitsWithRenewedSession() = runBlocking {
        val fixture = createFavoriteGroupEditFixture(FavoriteGroupEditScenario.UnauthorizedThenSuccess)
        try {
            val firstSession = assertNotNull(SharedFlowCentre.currentSession.value)
            val group = fixture.favoriteService.favoritesByGroup(FavoriteType.Avatar).value.keys
                .single { it.ownerId != "local" }
            fixture.model.updateAvatarGroupOptions(AvatarGroupOptions(group))
            fixture.model.openFavoriteGroupEditor(group)

            fixture.model.saveFavoriteGroup("Renewed avatars", FavoriteGroupVisibility.Public)
            awaitUntil { fixture.model.favoriteGroupEditState.value.group == null }
            awaitUntil { fixture.requests.cachePublicationCount.value == 1 }

            val renewedSession = assertNotNull(SharedFlowCentre.currentSession.value)
            assertEquals(2, fixture.requests.updateCount.value)
            assertEquals(1, fixture.requests.credentialAuthenticationCount.value)
            assertEquals(firstSession.token.generation, fixture.requests.firstPutGeneration.value)
            assertNotEquals(firstSession.token, renewedSession.token)
            assertEquals(renewedSession.token.generation, fixture.requests.secondPutGeneration.value)
            assertEquals(1, fixture.requests.cachePublicationCount.value)
            val updatedGroup = fixture.favoriteService.favoritesByGroup(FavoriteType.Avatar).value.keys
                .single { it.ownerId != "local" }
            assertEquals("Renewed avatars", updatedGroup.displayName)
            assertEquals("public", updatedGroup.visibility)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun externalSessionRenewalReleasesSavingWithoutPublishingStaleSuccess() = runBlocking {
        val fixture = createFavoriteGroupEditFixture(
            FavoriteGroupEditScenario.SessionRenewalBeforeSuccess,
        )
        try {
            val firstSession = assertNotNull(SharedFlowCentre.currentSession.value)
            val group = fixture.favoriteService.favoritesByGroup(FavoriteType.Avatar).value.keys
                .single { it.ownerId != "local" }
            fixture.model.openFavoriteGroupEditor(group)
            fixture.model.saveFavoriteGroup("Stale success", FavoriteGroupVisibility.Public)
            awaitUntil { fixture.requests.firstUpdateStarted.isCompleted }

            SharedFlowCentre.emitAuthenticated(firstSession.account)
            val renewedSession = assertNotNull(SharedFlowCentre.currentSession.value)
            assertNotEquals(firstSession.token, renewedSession.token)
            fixture.requests.releaseFirstUpdate.complete(Unit)
            awaitUntil {
                fixture.model.favoriteGroupEditState.value.failure ==
                    FavoriteGroupEditFailure.SaveFailed
            }

            val editState = fixture.model.favoriteGroupEditState.value
            assertEquals(group, editState.group)
            assertTrue(!editState.isSaving)
            assertEquals(1, fixture.requests.updateCount.value)
            assertEquals(0, fixture.requests.credentialAuthenticationCount.value)
            assertEquals(0, fixture.requests.cachePublicationCount.value)
            val unchangedGroup = fixture.favoriteService.favoritesByGroup(FavoriteType.Avatar).value.keys
                .single { it.ownerId != "local" }
            assertEquals("Avatars", unchangedGroup.displayName)
            assertEquals("private", unchangedGroup.visibility)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun accountSwitchBeforeUnauthorizedResponseDoesNotRetryOrPublishOldUpdate() = runBlocking {
        val fixture = createFavoriteGroupEditFixture(
            FavoriteGroupEditScenario.SwitchAccountBeforeUnauthorized,
        )
        try {
            val group = fixture.favoriteService.favoritesByGroup(FavoriteType.Avatar).value.keys
                .single { it.ownerId != "local" }
            fixture.model.updateAvatarGroupOptions(AvatarGroupOptions(group))
            fixture.model.openFavoriteGroupEditor(group)
            fixture.model.saveFavoriteGroup("Stale update", FavoriteGroupVisibility.Public)
            awaitUntil { fixture.requests.firstUpdateStarted.isCompleted }

            val nextAccount = AccountDto(
                userId = "usr_second",
                username = "second",
                password = "second-password",
                authCookie = "second-auth",
            )
            SharedFlowCentre.emitAuthenticated(nextAccount)
            awaitUntil { fixture.model.favoriteGroupEditState.value.group == null }
            fixture.requests.releaseFirstUpdate.complete(Unit)
            awaitUntil { fixture.requests.firstUpdateResponded.isCompleted }

            assertEquals(1, fixture.requests.updateCount.value)
            assertEquals(0, fixture.requests.credentialAuthenticationCount.value)
            assertEquals(nextAccount.userId, SharedFlowCentre.currentSession.value?.token?.userId)
            assertTrue(fixture.favoriteService.loadFavoriteByGroup(FavoriteType.Avatar).isSuccess)
            val currentEntry = fixture.favoriteService.favoritesByGroup(FavoriteType.Avatar).value.entries
                .single { it.key.ownerId != "local" }
            assertEquals(nextAccount.userId, currentEntry.key.ownerId)
            assertEquals("Avatars", currentEntry.key.displayName)
            assertEquals("private", currentEntry.key.visibility)
            assertEquals(listOf("avtr_usr_second"), currentEntry.value.map { it.favoriteId })
        } finally {
            fixture.close()
        }
    }
}

private class FavoriteGroupEditFixture(
    val model: FriendListPagerModel,
    val favoriteService: FavoriteService,
    val requests: FavoriteGroupEditRequests,
    private val friendService: FriendService,
    private val profileScope: CoroutineScope,
    private val client: HttpClient,
) {
    suspend fun close() {
        requests.releaseFirstUpdate.complete(Unit)
        ViewModelStore().apply {
            put("favorite-group-edit", model)
            clear()
        }
        friendService.dispose()
        favoriteService.dispose()
        profileScope.cancel()
        SharedFlowCentre.emitLogout()
        client.close()
    }
}

private enum class FavoriteGroupEditScenario {
    FailureThenSuccess,
    UnauthorizedThenSuccess,
    SessionRenewalBeforeSuccess,
    SwitchAccountBeforeUnauthorized,
}

private class FavoriteGroupEditRequests(
    val scenario: FavoriteGroupEditScenario,
) {
    val updateCount = atomic(0)
    val credentialAuthenticationCount = atomic(0)
    val cachePublicationCount = atomic(0)
    val firstPutGeneration = atomic(-1L)
    val secondPutGeneration = atomic(-1L)
    val firstUpdateStarted = CompletableDeferred<Unit>()
    val releaseFirstUpdate = CompletableDeferred<Unit>()
    val firstUpdateResponded = CompletableDeferred<Unit>()
}

private suspend fun createFavoriteGroupEditFixture(
    scenario: FavoriteGroupEditScenario = FavoriteGroupEditScenario.FailureThenSuccess,
): FavoriteGroupEditFixture {
    SharedFlowCentre.emitLogout()
    val account = AccountDto(
        userId = "usr_editor",
        username = "editor",
        password = "editor-password",
        authCookie = "editor-auth",
    )
    SharedFlowCentre.emitAuthenticated(account)
    val requests = FavoriteGroupEditRequests(scenario)
    val json = Json { ignoreUnknownKeys = true }
    val client = favoriteGroupEditClient(requests, json)
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
    assertTrue(favoriteService.loadFavoriteByGroup(FavoriteType.Avatar).isSuccess)
    val profileScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    profileScope.launch(start = CoroutineStart.UNDISPATCHED) {
        favoriteService.favoritesByGroup(FavoriteType.Avatar).drop(1).collect {
            requests.cachePublicationCount.incrementAndGet()
        }
    }
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
    return FavoriteGroupEditFixture(
        model = model,
        favoriteService = favoriteService,
        requests = requests,
        friendService = friendService,
        profileScope = profileScope,
        client = client,
    )
}

private fun favoriteGroupEditClient(
    requests: FavoriteGroupEditRequests,
    json: Json,
) = HttpClient(MockEngine) {
    engine {
        addHandler { request ->
            when (request.url.encodedPath) {
                "/auth/user/favoritelimits" -> jsonResponse(FAVORITE_GROUP_EDIT_LIMITS)
                "/auth/user/friends" -> jsonResponse("[]")
                "/favorites" -> if (request.url.parameters["offset"] == "0") {
                    val ownerId = SharedFlowCentre.currentSession.value?.token?.userId.orEmpty()
                    jsonResponse(favoriteGroupEditFavorites(ownerId))
                } else {
                    jsonResponse("[]")
                }
                "/favorite/groups" -> if (request.url.parameters["offset"] == "0") {
                    val ownerId = SharedFlowCentre.currentSession.value?.token?.userId.orEmpty()
                    jsonResponse(favoriteGroupEditGroups(ownerId))
                } else {
                    jsonResponse("[]")
                }
                "/auth/user" -> {
                    if (request.headers[HttpHeaders.Authorization] != null) {
                        requests.credentialAuthenticationCount.incrementAndGet()
                    }
                    if (requests.scenario == FavoriteGroupEditScenario.SwitchAccountBeforeUnauthorized) {
                        error("Authentication must not run after the account changes")
                    }
                    val account = SharedFlowCentre.currentSession.value?.account
                        ?: error("Authentication requested without a session")
                    jsonResponse(favoriteGroupEditCurrentUser(account))
                }
                else -> {
                    if (!request.url.encodedPath.startsWith("/favorite/group/avatar/avatars1/")) {
                        error("Unexpected request: ${request.url}")
                    }
                    val updateNumber = requests.updateCount.incrementAndGet()
                    val generation = SharedFlowCentre.currentSession.value?.token?.generation ?: -1L
                    if (updateNumber == 1) {
                        requests.firstPutGeneration.value = generation
                        requests.firstUpdateStarted.complete(Unit)
                    } else if (updateNumber == 2) {
                        requests.secondPutGeneration.value = generation
                    }
                    when (requests.scenario) {
                        FavoriteGroupEditScenario.FailureThenSuccess -> {
                            if (updateNumber == 1) {
                                requests.releaseFirstUpdate.await()
                                requests.firstUpdateResponded.complete(Unit)
                                respond("failed", HttpStatusCode.InternalServerError)
                            } else {
                                jsonResponse("")
                            }
                        }
                        FavoriteGroupEditScenario.UnauthorizedThenSuccess -> {
                            if (updateNumber == 1) {
                                requests.firstUpdateResponded.complete(Unit)
                                respond("unauthorized", HttpStatusCode.Unauthorized)
                            } else {
                                jsonResponse("")
                            }
                        }
                        FavoriteGroupEditScenario.SessionRenewalBeforeSuccess -> {
                            check(updateNumber == 1) { "Stale success must not be retried" }
                            requests.releaseFirstUpdate.await()
                            requests.firstUpdateResponded.complete(Unit)
                            jsonResponse("")
                        }
                        FavoriteGroupEditScenario.SwitchAccountBeforeUnauthorized -> {
                            check(updateNumber == 1) { "Old account request must not be retried" }
                            withContext(NonCancellable) {
                                requests.releaseFirstUpdate.await()
                                requests.firstUpdateResponded.complete(Unit)
                            }
                            respond("unauthorized", HttpStatusCode.Unauthorized)
                        }
                    }
                }
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

private suspend fun awaitUntil(predicate: () -> Boolean) {
    withTimeout(3_000) {
        while (!predicate()) yield()
    }
}

private const val FAVORITE_GROUP_EDIT_LIMITS = """
    {
      "maxFavoriteGroups":{"avatar":4,"friend":3,"world":4},
      "maxFavoritesPerGroup":{"avatar":100,"friend":100,"world":100},
      "defaultMaxFavoriteGroups":4,
      "defaultMaxFavoritesPerGroup":100
    }
"""

private fun favoriteGroupEditFavorites(ownerId: String) = """
    [{
      "favoriteId":"avtr_$ownerId","id":"fvrt_$ownerId",
      "tags":["avatars1"],"type":"avatar"
    }]
""".trimIndent()

private fun favoriteGroupEditGroups(ownerId: String) = """
    [{
      "id":"grp_avatars1_$ownerId","ownerId":"$ownerId","type":"avatar",
      "visibility":"private","displayName":"Avatars","name":"avatars1",
      "ownerDisplayName":"$ownerId","tags":[]
    }]
""".trimIndent()

private fun favoriteGroupEditCurrentUser(account: AccountDto) = """
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
