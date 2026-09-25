package io.github.vrcmteam.vrcm.presentation.screens.world

import com.russhwolf.settings.MapSettings
import io.github.vrcmteam.vrcm.core.shared.AccountSessionToken
import io.github.vrcmteam.vrcm.core.shared.AuthenticatedAccount
import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.di.supports.PersistentCookiesStorage
import io.github.vrcmteam.vrcm.network.api.auth.AuthApi
import io.github.vrcmteam.vrcm.network.api.worlds.WorldsApi
import io.github.vrcmteam.vrcm.service.AuthService
import io.github.vrcmteam.vrcm.service.SessionBoundResponse
import io.github.vrcmteam.vrcm.service.data.AccountDto
import io.github.vrcmteam.vrcm.storage.AccountCacheManager
import io.github.vrcmteam.vrcm.storage.AccountDao
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
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.plugins.defaultRequest
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import org.koin.core.logger.EmptyLogger
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class WorldDeletionStateModelTest : MainDispatcherTest() {
    @AfterTest
    fun clearSession() = runTest { SharedFlowCentre.emitLogout() }

    @Test
    fun onlyTheCurrentOwnerCanStartDeletion() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val token = AccountSessionToken("usr_owner", 1)
        val session = MutableStateFlow(authenticated(token))
        val source = RecordingDeletionSource()
        val model = WorldDeletionStateModel(
            source = source,
            scope = backgroundScope,
            removeCachedWorld = {},
            requestDispatcher = dispatcher,
            sessionFlow = session,
        )

        model.setTarget("wrld_owned", "usr_other")

        assertFalse(model.state.value.isAvailable)
        assertFalse(model.delete())
        assertTrue(source.calls.isEmpty())

        model.setTarget("wrld_owned", "usr_owner")

        assertTrue(model.state.value.canDelete)
    }

    @Test
    fun duplicateSubmissionIsRejectedAndRemoteFailureAllowsRetry() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val token = AccountSessionToken("usr_owner", 1)
        val session = MutableStateFlow(authenticated(token))
        val pending = CompletableDeferred<SessionBoundResponse<Unit>?>()
        val source = RecordingDeletionSource { _, _ -> pending.await() }
        val notices = mutableListOf<WorldDeletionNotice>()
        val model = WorldDeletionStateModel(
            source = source,
            scope = backgroundScope,
            removeCachedWorld = {},
            requestDispatcher = dispatcher,
            sessionFlow = session,
        )
        backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) {
            model.notices.collect(notices::add)
        }
        model.setTarget("wrld_owned", "usr_owner")

        assertTrue(model.delete())
        assertFalse(model.delete())
        runCurrent()

        assertEquals(listOf("wrld_owned"), source.calls.map { it.second })
        assertTrue(model.state.value.isDeleting)

        pending.complete(
            SessionBoundResponse(
                result = Result.failure(IllegalStateException("offline")),
                sessionToken = token,
            )
        )
        runCurrent()

        assertTrue(model.state.value.canDelete)
        assertEquals(listOf<WorldDeletionNotice>(WorldDeletionNotice.Failed), notices)
    }

    @Test
    fun successfulDeletionRemovesOnlyTheTargetCacheAndCannotBeRepeated() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val token = AccountSessionToken("usr_owner", 1)
        val session = MutableStateFlow(authenticated(token))
        val source = RecordingDeletionSource()
        val removedWorlds = mutableListOf<String>()
        val notices = mutableListOf<WorldDeletionNotice>()
        val model = WorldDeletionStateModel(
            source = source,
            scope = backgroundScope,
            removeCachedWorld = removedWorlds::add,
            requestDispatcher = dispatcher,
            sessionFlow = session,
        )
        backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) {
            model.notices.collect(notices::add)
        }
        model.setTarget("wrld_owned", "usr_owner")

        assertTrue(model.delete())
        runCurrent()

        assertEquals(listOf("wrld_owned"), removedWorlds)
        assertEquals(listOf<WorldDeletionNotice>(WorldDeletionNotice.Deleted()), notices)
        assertTrue(model.state.value.isDeleted)
        assertFalse(model.state.value.canDelete)
        assertFalse(model.delete())
        assertEquals(1, source.calls.size)

        session.value = authenticated(AccountSessionToken("usr_other", 2))
        runCurrent()
        session.value = authenticated(AccountSessionToken("usr_owner", 3))
        runCurrent()

        assertTrue(model.state.value.isDeleted)
        assertFalse(model.delete())
        assertEquals(1, source.calls.size)
    }

    @Test
    fun cacheFailureAfterRemoteSuccessStillExitsAsDeletedAndPreventsAnotherRequest() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val token = AccountSessionToken("usr_owner", 1)
        val session = MutableStateFlow(authenticated(token))
        val source = RecordingDeletionSource()
        val notices = mutableListOf<WorldDeletionNotice>()
        val model = WorldDeletionStateModel(
            source = source,
            scope = backgroundScope,
            removeCachedWorld = { error("cache unavailable") },
            requestDispatcher = dispatcher,
            sessionFlow = session,
        )
        backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) {
            model.notices.collect(notices::add)
        }
        model.setTarget("wrld_owned", "usr_owner")

        assertTrue(model.delete())
        runCurrent()

        assertEquals(
            listOf<WorldDeletionNotice>(
                WorldDeletionNotice.Deleted(cacheCleanupFailed = true)
            ),
            notices,
        )
        assertTrue(model.state.value.isDeleted)
        assertFalse(model.state.value.canDelete)
        assertFalse(model.delete())
        assertEquals(1, source.calls.size)
    }

    @Test
    fun sameAccountRenewalAcceptsTheExactResponseToken() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val firstToken = AccountSessionToken("usr_owner", 1)
        val renewedToken = AccountSessionToken("usr_owner", 2)
        val session = MutableStateFlow(authenticated(firstToken))
        val removedWorlds = mutableListOf<String>()
        val source = RecordingDeletionSource { _, _ ->
            session.value = authenticated(renewedToken)
            SessionBoundResponse(Result.success(Unit), renewedToken)
        }
        val model = WorldDeletionStateModel(
            source = source,
            scope = backgroundScope,
            removeCachedWorld = removedWorlds::add,
            requestDispatcher = dispatcher,
            sessionFlow = session,
        )
        model.setTarget("wrld_owned", "usr_owner")

        assertTrue(model.delete())
        runCurrent()

        assertEquals(listOf("wrld_owned"), removedWorlds)
        assertTrue(model.state.value.isDeleted)
    }

    @Test
    fun externalSameAccountTokenChangeUnlocksWhenTheOldResponseReturns() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val firstToken = AccountSessionToken("usr_owner", 1)
        val renewedToken = AccountSessionToken("usr_owner", 2)
        val session = MutableStateFlow(authenticated(firstToken))
        val pending = CompletableDeferred<SessionBoundResponse<Unit>?>()
        val source = RecordingDeletionSource { _, _ -> pending.await() }
        val removedWorlds = mutableListOf<String>()
        val notices = mutableListOf<WorldDeletionNotice>()
        val model = WorldDeletionStateModel(
            source = source,
            scope = backgroundScope,
            removeCachedWorld = removedWorlds::add,
            requestDispatcher = dispatcher,
            sessionFlow = session,
        )
        backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) {
            model.notices.collect(notices::add)
        }
        model.setTarget("wrld_owned", "usr_owner")
        assertTrue(model.delete())
        runCurrent()

        session.value = authenticated(renewedToken)
        runCurrent()
        assertTrue(model.state.value.isDeleting)

        pending.complete(SessionBoundResponse(Result.success(Unit), firstToken))
        runCurrent()

        assertFalse(model.state.value.isDeleting)
        assertTrue(model.state.value.canDelete)
        assertTrue(removedWorlds.isEmpty())
        assertTrue(notices.isEmpty())
    }

    @Test
    fun sameAccountTokenChangeDuringCacheCleanupKeepsTheDeletedCommitAndFinishes() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val firstToken = AccountSessionToken("usr_owner", 1)
        val renewedToken = AccountSessionToken("usr_owner", 2)
        val session = MutableStateFlow(authenticated(firstToken))
        val cacheCleanup = CompletableDeferred<Unit>()
        val source = RecordingDeletionSource()
        val notices = mutableListOf<WorldDeletionNotice>()
        val model = WorldDeletionStateModel(
            source = source,
            scope = backgroundScope,
            removeCachedWorld = { cacheCleanup.await() },
            requestDispatcher = dispatcher,
            sessionFlow = session,
        )
        backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) {
            model.notices.collect(notices::add)
        }
        model.setTarget("wrld_owned", "usr_owner")

        assertTrue(model.delete())
        runCurrent()
        assertTrue(model.state.value.isDeleted)
        assertFalse(model.state.value.isDeleting)

        session.value = authenticated(renewedToken)
        runCurrent()

        assertTrue(model.state.value.isDeleted)
        assertFalse(model.delete())
        cacheCleanup.complete(Unit)
        runCurrent()

        assertEquals(listOf<WorldDeletionNotice>(WorldDeletionNotice.Deleted()), notices)
        assertTrue(model.state.value.isDeleted)
        assertEquals(1, source.calls.size)
    }

    @Test
    fun accountSwitchDiscardsLateSuccessWithoutCacheOrNotice() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val ownerToken = AccountSessionToken("usr_owner", 1)
        val otherToken = AccountSessionToken("usr_other", 2)
        val session = MutableStateFlow(authenticated(ownerToken))
        val pending = CompletableDeferred<SessionBoundResponse<Unit>?>()
        val source = RecordingDeletionSource { _, _ ->
            withContext(NonCancellable) { pending.await() }
        }
        val removedWorlds = mutableListOf<String>()
        val notices = mutableListOf<WorldDeletionNotice>()
        val model = WorldDeletionStateModel(
            source = source,
            scope = backgroundScope,
            removeCachedWorld = removedWorlds::add,
            requestDispatcher = dispatcher,
            sessionFlow = session,
        )
        backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) {
            model.notices.collect(notices::add)
        }
        model.setTarget("wrld_owned", "usr_owner")
        assertTrue(model.delete())
        runCurrent()

        session.value = authenticated(otherToken)
        runCurrent()
        pending.complete(SessionBoundResponse(Result.success(Unit), ownerToken))
        runCurrent()

        assertFalse(model.state.value.isAvailable)
        assertFalse(model.state.value.isDeleting)
        assertTrue(removedWorlds.isEmpty())
        assertTrue(notices.isEmpty())
    }

    @Test
    fun unauthorizedRequestRenewsTheSameAccountAndReturnsTheCurrentToken() = runTest {
        var deletionAttempts = 0
        val account = cachedAccount()
        val fixture = authFixture(account) { request ->
            when (request.url.encodedPath) {
                "/api/1/auth/user" -> jsonResponse(currentUserJson(account))
                "/api/1/worlds/wrld_owned" -> {
                    deletionAttempts++
                    if (deletionAttempts == 1) {
                        respond("expired", HttpStatusCode.Unauthorized)
                    } else {
                        respond("", HttpStatusCode.OK)
                    }
                }
                else -> error("Unexpected request: ${request.url}")
            }
        }
        try {
            fixture.service.restoreAuth()
            val firstSession = assertNotNull(SharedFlowCentre.currentSession.value)
            val response = assertNotNull(
                NetworkWorldDeletionSource(
                    worldsApi = WorldsApi(fixture.client),
                    authService = fixture.service,
                ).delete(firstSession.token, "wrld_owned")
            )

            val renewedSession = assertNotNull(SharedFlowCentre.currentSession.value)
            assertEquals(2, deletionAttempts)
            assertEquals(firstSession.account.userId, renewedSession.account.userId)
            assertNotEquals(firstSession.token, renewedSession.token)
            assertEquals(renewedSession.token, response.sessionToken)
            assertTrue(response.result.isSuccess)
        } finally {
            fixture.client.close()
        }
    }
}

private class RecordingDeletionSource(
    private val handler: suspend (
        AccountSessionToken,
        String,
    ) -> SessionBoundResponse<Unit>? = { token, _ ->
        SessionBoundResponse(Result.success(Unit), token)
    },
) : WorldDeletionSource {
    val calls = mutableListOf<Pair<AccountSessionToken, String>>()

    override suspend fun delete(
        sessionToken: AccountSessionToken,
        worldId: String,
    ): SessionBoundResponse<Unit>? {
        calls += sessionToken to worldId
        return handler(sessionToken, worldId)
    }
}

private fun authenticated(token: AccountSessionToken) = AuthenticatedAccount(
    account = AccountDto(userId = token.userId),
    token = token,
)

private data class DeletionAuthFixture(
    val service: AuthService,
    val client: HttpClient,
)

private fun authFixture(
    account: AccountDto,
    handler: MockRequestHandler,
): DeletionAuthFixture {
    val cookies = PersistentCookiesStorage(EmptyLogger())
    val client = HttpClient(MockEngine) {
        engine { addHandler(handler) }
        defaultRequest { url("https://api.vrchat.cloud/api/1/") }
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        install(HttpCookies) { storage = cookies }
    }
    val accountDao = AccountDao(MapSettings(), InMemorySecureStorage()).also {
        it.saveAccountInfo(account)
    }
    val service = AuthService(
        authApi = AuthApi(client),
        accountDao = accountDao,
        cookiesStorage = cookies,
        accountCacheManager = AccountCacheManager(
            friendListCacheStore = InMemoryFriendListCacheStore(),
            userProfileCacheStore = InMemoryUserProfileCacheStore(),
            friendActivityStore = NoOpFriendActivityCacheStore,
            meetupCardConfigDao = MeetupCardConfigDao(MapSettings()),
            meetupCardAssetStore = MeetupCardAssetStore(
                FakeFileSystem(),
                "/meetup-assets".toPath(),
            ),
        ),
    )
    return DeletionAuthFixture(service, client)
}

private fun cachedAccount() = AccountDto(
    userId = "usr_owner",
    username = "owner",
    password = "owner-password",
    current = true,
    authCookie = "cached-auth",
    twoFactorAuthCookie = "cached-2fa",
)

private fun MockRequestHandleScope.jsonResponse(body: String) = respond(
    content = body,
    status = HttpStatusCode.OK,
    headers = headersOf(HttpHeaders.ContentType, "application/json"),
)

private fun currentUserJson(account: AccountDto): String = """
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
