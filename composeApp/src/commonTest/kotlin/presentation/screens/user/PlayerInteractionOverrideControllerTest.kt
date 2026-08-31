package io.github.vrcmteam.vrcm.presentation.screens.user

import com.russhwolf.settings.MapSettings
import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.di.supports.PersistentCookiesStorage
import io.github.vrcmteam.vrcm.network.api.auth.AuthApi
import io.github.vrcmteam.vrcm.network.api.users.UsersApi
import io.github.vrcmteam.vrcm.network.api.users.data.PlayerInteractionOverride
import io.github.vrcmteam.vrcm.service.AuthService
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
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.koin.core.logger.EmptyLogger
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlayerInteractionOverrideControllerTest : MainDispatcherTest() {
    @AfterTest
    fun clearSession() = runTest { SharedFlowCentre.emitLogout() }

    @Test
    fun switchingSidesDeletesTheOldOverrideBeforeCreatingTheNewOne() = runTest {
        val requests = mutableListOf<HttpRequestData>()
        val fixture = fixture { request ->
            requests += request
            when (request.method) {
                HttpMethod.Get -> jsonResponse(moderationList("interactOff"))
                HttpMethod.Put -> jsonResponse(successJson())
                HttpMethod.Post -> jsonResponse(moderation("interactOn"))
                else -> error("Unexpected request: ${request.method} ${request.url}")
            }
        }

        assertIs<PlayerInteractionRequestResult.Succeeded>(fixture.controller.refresh(TARGET_USER_ID))
        assertIs<PlayerInteractionRequestResult.Succeeded>(
            fixture.controller.setOverride(TARGET_USER_ID, PlayerInteractionOverride.InteractOn),
        )

        assertEquals(
            listOf(
                Triple(HttpMethod.Get, MODERATIONS_PATH, ""),
                Triple(
                    HttpMethod.Put,
                    UNMODERATE_PATH,
                    """{"moderated":"usr_target","type":"interactOff"}""",
                ),
                Triple(
                    HttpMethod.Post,
                    MODERATIONS_PATH,
                    """{"moderated":"usr_target","type":"interactOn"}""",
                ),
            ),
            requests.map { Triple(it.method, it.url.encodedPath, it.bodyTextOrEmpty()) },
        )
        val ready = assertIs<PlayerInteractionState.Ready>(fixture.controller.state.value)
        assertEquals(PlayerInteractionOverride.InteractOn, ready.snapshot.effectiveOverride)
        fixture.client.close()
    }

    @Test
    fun conflictingSnapshotDeletesBothSidesThenCreatesOneRequestedOverride() = runTest {
        val writeBodies = mutableListOf<Pair<HttpMethod, String>>()
        val fixture = fixture { request ->
            when (request.method) {
                HttpMethod.Get -> jsonResponse(
                    """[
                        ${moderation("interactOn", "2026-08-31T15:00:00Z")},
                        ${moderation("interactOff", "2026-08-31T14:00:00Z")}
                    ]""",
                )
                HttpMethod.Put -> {
                    writeBodies += request.method to request.bodyTextOrEmpty()
                    jsonResponse(successJson())
                }
                HttpMethod.Post -> {
                    writeBodies += request.method to request.bodyTextOrEmpty()
                    jsonResponse(moderation("interactOff"))
                }
                else -> error("Unexpected request: ${request.method} ${request.url}")
            }
        }

        fixture.controller.refresh(TARGET_USER_ID)
        assertIs<PlayerInteractionRequestResult.Succeeded>(
            fixture.controller.setOverride(TARGET_USER_ID, PlayerInteractionOverride.InteractOff),
        )

        assertEquals(
            listOf(
                HttpMethod.Put to """{"moderated":"usr_target","type":"interactOn"}""",
                HttpMethod.Put to """{"moderated":"usr_target","type":"interactOff"}""",
                HttpMethod.Post to """{"moderated":"usr_target","type":"interactOff"}""",
            ),
            writeBodies,
        )
        fixture.client.close()
    }

    @Test
    fun partialWriteFailureRechecksAuthorityAndKeepsTheRequestedRetry() = runTest {
        var getCount = 0
        var postCount = 0
        val fixture = fixture { request ->
            when (request.method) {
                HttpMethod.Get -> {
                    getCount++
                    jsonResponse(if (getCount == 1) moderationList("interactOff") else "[]")
                }
                HttpMethod.Put -> jsonResponse(successJson())
                HttpMethod.Post -> {
                    postCount++
                    if (postCount == 1) {
                        respond("temporary failure", HttpStatusCode.InternalServerError)
                    } else {
                        jsonResponse(moderation("interactOn"))
                    }
                }
                else -> error("Unexpected request: ${request.method} ${request.url}")
            }
        }

        fixture.controller.refresh(TARGET_USER_ID)
        assertIs<PlayerInteractionRequestResult.Failed>(
            fixture.controller.setOverride(TARGET_USER_ID, PlayerInteractionOverride.InteractOn),
        )
        val failed = assertIs<PlayerInteractionState.Failed>(fixture.controller.state.value)
        assertEquals(PlayerInteractionOverride.Default, failed.snapshot?.effectiveOverride)
        assertEquals(PlayerInteractionOverride.InteractOn, failed.retryOverride)

        assertIs<PlayerInteractionRequestResult.Succeeded>(
            fixture.controller.setOverride(TARGET_USER_ID, PlayerInteractionOverride.InteractOn),
        )
        assertEquals(2, getCount)
        assertEquals(2, postCount)
        fixture.client.close()
    }

    @Test
    fun repeatedSubmissionIsIgnoredWhileTheFirstWriteIsRunning() = runTest {
        val putStarted = CompletableDeferred<Unit>()
        val finishPut = CompletableDeferred<Unit>()
        var putCount = 0
        var postCount = 0
        val fixture = fixture { request ->
            when (request.method) {
                HttpMethod.Get -> jsonResponse(moderationList("interactOff"))
                HttpMethod.Put -> {
                    putCount++
                    putStarted.complete(Unit)
                    finishPut.await()
                    jsonResponse(successJson())
                }
                HttpMethod.Post -> {
                    postCount++
                    jsonResponse(moderation("interactOn"))
                }
                else -> error("Unexpected request: ${request.method} ${request.url}")
            }
        }
        fixture.controller.refresh(TARGET_USER_ID)

        val first = async(start = CoroutineStart.UNDISPATCHED) {
            fixture.controller.setOverride(TARGET_USER_ID, PlayerInteractionOverride.InteractOn)
        }
        putStarted.await()
        assertIs<PlayerInteractionRequestResult.Ignored>(
            fixture.controller.setOverride(TARGET_USER_ID, PlayerInteractionOverride.InteractOn),
        )
        finishPut.complete(Unit)

        assertIs<PlayerInteractionRequestResult.Succeeded>(first.await())
        assertEquals(1, putCount)
        assertEquals(1, postCount)
        fixture.client.close()
    }

    @Test
    fun failedInitialReadCanRetryIntoReadyState() = runTest {
        var getCount = 0
        val fixture = fixture { request ->
            check(request.method == HttpMethod.Get)
            getCount++
            if (getCount == 1) {
                respond("temporary failure", HttpStatusCode.InternalServerError)
            } else {
                jsonResponse(moderationList("interactOn"))
            }
        }

        assertIs<PlayerInteractionRequestResult.Failed>(fixture.controller.refresh(TARGET_USER_ID))
        val failed = assertIs<PlayerInteractionState.Failed>(fixture.controller.state.value)
        assertNull(failed.snapshot)
        assertNull(failed.retryOverride)

        assertIs<PlayerInteractionRequestResult.Succeeded>(fixture.controller.refresh(TARGET_USER_ID))
        val ready = assertIs<PlayerInteractionState.Ready>(fixture.controller.state.value)
        assertEquals(PlayerInteractionOverride.InteractOn, ready.snapshot.effectiveOverride)
        fixture.client.close()
    }

    @Test
    fun realUnauthorizedWriteRenewsTheSameAccountSessionAndRetriesOnlyThatStep() = runTest {
        var putCount = 0
        var postCount = 0
        val fixture = fixture { request ->
            when (request.method) {
                HttpMethod.Get -> jsonResponse(moderationList("interactOff"))
                HttpMethod.Put -> {
                    putCount++
                    jsonResponse(successJson())
                }
                HttpMethod.Post -> {
                    postCount++
                    if (postCount == 1) {
                        respond("expired", HttpStatusCode.Unauthorized)
                    } else {
                        jsonResponse(moderation("interactOn"))
                    }
                }
                else -> error("Unexpected request: ${request.method} ${request.url}")
            }
        }
        fixture.controller.refresh(TARGET_USER_ID)
        val firstToken = assertNotNull(SharedFlowCentre.currentSession.value).token
        val collector = launch(start = CoroutineStart.UNDISPATCHED) {
            SharedFlowCentre.currentSession.drop(1).collect { session ->
                fixture.controller.onSessionChanged(session?.token)
            }
        }

        assertIs<PlayerInteractionRequestResult.Succeeded>(
            fixture.controller.setOverride(TARGET_USER_ID, PlayerInteractionOverride.InteractOn),
        )

        val renewedToken = assertNotNull(SharedFlowCentre.currentSession.value).token
        assertNotEquals(firstToken, renewedToken)
        assertEquals(1, putCount)
        assertEquals(2, postCount)
        assertIs<PlayerInteractionState.Ready>(fixture.controller.state.value)
        collector.cancelAndJoin()
        fixture.client.close()
    }

    @Test
    fun accountSwitchInvalidatesAnInFlightReadImmediately() = runTest {
        val readStarted = CompletableDeferred<Unit>()
        val finishRead = CompletableDeferred<Unit>()
        val fixture = fixture { request ->
            check(request.method == HttpMethod.Get)
            readStarted.complete(Unit)
            finishRead.await()
            jsonResponse(moderationList("interactOff"))
        }
        val read = async(start = CoroutineStart.UNDISPATCHED) {
            fixture.controller.refresh(TARGET_USER_ID)
        }
        readStarted.await()

        SharedFlowCentre.emitAuthenticated(
            AccountDto(userId = "usr_other_account", username = "other"),
        )
        val replacement = assertNotNull(SharedFlowCentre.currentSession.value).token
        assertFalse(fixture.controller.onSessionChanged(replacement))
        assertIs<PlayerInteractionState.Unavailable>(fixture.controller.state.value)
        finishRead.complete(Unit)

        assertIs<PlayerInteractionRequestResult.Stale>(read.await())
        assertIs<PlayerInteractionState.Unavailable>(fixture.controller.state.value)
        fixture.client.close()
    }

    @Test
    fun logoutInvalidatesAnInFlightWriteAndPreventsTheFollowingCreate() = runTest {
        val putStarted = CompletableDeferred<Unit>()
        val finishPut = CompletableDeferred<Unit>()
        var postCount = 0
        val fixture = fixture { request ->
            when (request.method) {
                HttpMethod.Get -> jsonResponse(moderationList("interactOff"))
                HttpMethod.Put -> {
                    putStarted.complete(Unit)
                    finishPut.await()
                    jsonResponse(successJson())
                }
                HttpMethod.Post -> {
                    postCount++
                    jsonResponse(moderation("interactOn"))
                }
                else -> error("Unexpected request: ${request.method} ${request.url}")
            }
        }
        fixture.controller.refresh(TARGET_USER_ID)
        val write = async(start = CoroutineStart.UNDISPATCHED) {
            fixture.controller.setOverride(TARGET_USER_ID, PlayerInteractionOverride.InteractOn)
        }
        putStarted.await()

        SharedFlowCentre.emitLogout()
        assertFalse(fixture.controller.onSessionChanged(null))
        assertIs<PlayerInteractionState.Unavailable>(fixture.controller.state.value)
        finishPut.complete(Unit)

        assertIs<PlayerInteractionRequestResult.Stale>(write.await())
        assertEquals(0, postCount)
        fixture.client.close()
    }

    @Test
    fun sameAccountExternalTokenReplacementInvalidatesThenAllowsAuthoritativeReload() = runTest {
        var getCount = 0
        val fixture = fixture { request ->
            check(request.method == HttpMethod.Get)
            getCount++
            jsonResponse(
                if (getCount == 1) moderationList("interactOff")
                else moderationList("interactOn"),
            )
        }
        fixture.controller.refresh(TARGET_USER_ID)

        SharedFlowCentre.emitAuthenticated(fixture.account)
        val replacement = assertNotNull(SharedFlowCentre.currentSession.value).token
        assertTrue(fixture.controller.onSessionChanged(replacement))
        assertIs<PlayerInteractionState.Unavailable>(fixture.controller.state.value)

        assertIs<PlayerInteractionRequestResult.Succeeded>(fixture.controller.refresh(TARGET_USER_ID))
        val ready = assertIs<PlayerInteractionState.Ready>(fixture.controller.state.value)
        assertEquals(PlayerInteractionOverride.InteractOn, ready.snapshot.effectiveOverride)
        assertEquals(2, getCount)
        fixture.client.close()
    }

    @Test
    fun updateDetectsSameAccountReplacementBeforeTheSessionCollectorRuns() = runTest {
        val fixture = fixture { request ->
            check(request.method == HttpMethod.Get)
            jsonResponse(moderationList("interactOff"))
        }
        fixture.controller.refresh(TARGET_USER_ID)
        SharedFlowCentre.emitAuthenticated(fixture.account)

        val result = assertIs<PlayerInteractionRequestResult.Stale>(
            fixture.controller.setOverride(TARGET_USER_ID, PlayerInteractionOverride.InteractOn),
        )

        assertTrue(result.canReload)
        assertIs<PlayerInteractionState.Unavailable>(fixture.controller.state.value)
        assertIs<PlayerInteractionRequestResult.Succeeded>(fixture.controller.refresh(TARGET_USER_ID))
        fixture.client.close()
    }

    @Test
    fun targetSwitchPreventsTheOlderReadFromPublishingIntoTheNewProfile() = runTest {
        val firstReadStarted = CompletableDeferred<Unit>()
        val finishFirstRead = CompletableDeferred<Unit>()
        val fixture = fixture { request ->
            val target = request.url.parameters["targetUserId"]
            if (target == TARGET_USER_ID) {
                firstReadStarted.complete(Unit)
                finishFirstRead.await()
                jsonResponse(moderationList("interactOff", TARGET_USER_ID))
            } else {
                jsonResponse(moderationList("interactOn", SECOND_TARGET_USER_ID))
            }
        }
        val firstRead = async(start = CoroutineStart.UNDISPATCHED) {
            fixture.controller.refresh(TARGET_USER_ID)
        }
        firstReadStarted.await()
        val secondRead = async(start = CoroutineStart.UNDISPATCHED) {
            fixture.controller.refresh(SECOND_TARGET_USER_ID)
        }
        assertEquals(
            SECOND_TARGET_USER_ID,
            assertIs<PlayerInteractionState.Checking>(fixture.controller.state.value).targetUserId,
        )
        finishFirstRead.complete(Unit)

        assertIs<PlayerInteractionRequestResult.Stale>(firstRead.await())
        assertIs<PlayerInteractionRequestResult.Succeeded>(secondRead.await())
        val ready = assertIs<PlayerInteractionState.Ready>(fixture.controller.state.value)
        assertEquals(SECOND_TARGET_USER_ID, ready.targetUserId)
        assertEquals(PlayerInteractionOverride.InteractOn, ready.snapshot.effectiveOverride)
        fixture.client.close()
    }

    @Test
    fun staleTargetUpdateDoesNotInvalidateTheNewTargetsReadyState() = runTest {
        val fixture = fixture { request ->
            check(request.method == HttpMethod.Get)
            jsonResponse(moderationList("interactOn", SECOND_TARGET_USER_ID))
        }
        fixture.controller.refresh(SECOND_TARGET_USER_ID)

        assertIs<PlayerInteractionRequestResult.Ignored>(
            fixture.controller.setOverride(TARGET_USER_ID, PlayerInteractionOverride.InteractOff),
        )

        val ready = assertIs<PlayerInteractionState.Ready>(fixture.controller.state.value)
        assertEquals(SECOND_TARGET_USER_ID, ready.targetUserId)
        assertEquals(PlayerInteractionOverride.InteractOn, ready.snapshot.effectiveOverride)
        fixture.client.close()
    }

    @Test
    fun targetSwitchStopsAnOlderWriteBeforeItCanCreateTheNewOverride() = runTest {
        val putStarted = CompletableDeferred<Unit>()
        val finishPut = CompletableDeferred<Unit>()
        var postCount = 0
        val fixture = fixture { request ->
            when (request.method) {
                HttpMethod.Get -> {
                    val target = request.url.parameters["targetUserId"]
                    jsonResponse(
                        if (target == TARGET_USER_ID) {
                            moderationList("interactOff", TARGET_USER_ID)
                        } else {
                            moderationList("interactOn", SECOND_TARGET_USER_ID)
                        },
                    )
                }
                HttpMethod.Put -> {
                    putStarted.complete(Unit)
                    finishPut.await()
                    jsonResponse(successJson())
                }
                HttpMethod.Post -> {
                    postCount++
                    jsonResponse(moderation("interactOn"))
                }
                else -> error("Unexpected request: ${request.method} ${request.url}")
            }
        }
        fixture.controller.refresh(TARGET_USER_ID)
        val write = async(start = CoroutineStart.UNDISPATCHED) {
            fixture.controller.setOverride(TARGET_USER_ID, PlayerInteractionOverride.InteractOn)
        }
        putStarted.await()
        val newTargetRead = async(start = CoroutineStart.UNDISPATCHED) {
            fixture.controller.refresh(SECOND_TARGET_USER_ID)
        }
        finishPut.complete(Unit)

        assertIs<PlayerInteractionRequestResult.Stale>(write.await())
        assertIs<PlayerInteractionRequestResult.Succeeded>(newTargetRead.await())
        assertEquals(0, postCount)
        val ready = assertIs<PlayerInteractionState.Ready>(fixture.controller.state.value)
        assertEquals(SECOND_TARGET_USER_ID, ready.targetUserId)
        fixture.client.close()
    }

    private data class Fixture(
        val controller: PlayerInteractionOverrideController,
        val client: HttpClient,
        val account: AccountDto,
    )

    private suspend fun fixture(handler: MockRequestHandler): Fixture {
        SharedFlowCentre.emitLogout()
        val account = AccountDto(
            userId = OWNER_USER_ID,
            username = "owner",
            password = "password",
            current = true,
            authCookie = "auth-cookie",
            twoFactorAuthCookie = "two-factor-cookie",
        )
        val cookies = PersistentCookiesStorage(EmptyLogger())
        val client = HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    if (request.url.encodedPath == AUTH_USER_PATH) {
                        jsonResponse(currentUserJson(account))
                    } else {
                        handler(request)
                    }
                }
            }
            defaultRequest { url("https://api.vrchat.cloud/api/1/") }
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            install(HttpCookies) { storage = cookies }
        }
        val accountDao = AccountDao(MapSettings(), InMemorySecureStorage()).also {
            it.saveAccountInfo(account)
        }
        val authService = AuthService(
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
        authService.restoreAuth()
        val sessionToken = assertNotNull(SharedFlowCentre.currentSession.value).token
        return Fixture(
            controller = PlayerInteractionOverrideController(
                ownerUserId = OWNER_USER_ID,
                initialSessionToken = sessionToken,
                authService = authService,
                usersApi = UsersApi(client),
            ),
            client = client,
            account = account,
        )
    }

    private fun MockRequestHandleScope.jsonResponse(body: String) = respond(
        content = body,
        status = HttpStatusCode.OK,
        headers = headersOf(HttpHeaders.ContentType, "application/json"),
    )

    private fun successJson() = """{"success":{"message":"ok","status_code":200}}"""

    private fun moderationList(type: String, targetUserId: String = TARGET_USER_ID) =
        "[${moderation(type, targetUserId = targetUserId)}]"

    private fun moderation(
        type: String,
        created: String = "2026-08-31T12:00:00Z",
        targetUserId: String = TARGET_USER_ID,
    ) = """{"targetUserId":"$targetUserId","type":"$type","created":"$created"}"""

    private fun HttpRequestData.bodyTextOrEmpty(): String =
        (body as? OutgoingContent.ByteArrayContent)?.bytes()?.decodeToString().orEmpty()

    private fun currentUserJson(account: AccountDto) = """
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
          "googleId":"","hasBirthday":true,"hasEmail":true,"hasLoggedInFromClient":true,
          "hasPendingEmail":false,"hideContentFilterSettings":false,"homeLocation":"",
          "id":"${account.userId}","isFriend":false,"last_activity":"","last_login":"",
          "last_platform":"standalonewindows","obfuscatedEmail":"",
          "obfuscatedPendingEmail":"","oculusId":"","offlineFriends":[],"onlineFriends":[],
          "pastDisplayNames":[],"picoId":"",
          "presence":{"avatarThumbnail":null,"displayName":"${account.username}","groups":[],
            "id":"${account.userId}","instance":"","instanceType":"","isRejoining":null,
            "platform":"standalonewindows","profilePicOverride":null,"status":"active",
            "travelingToInstance":"","travelingToWorld":"","world":""},
          "profilePicOverride":"","state":"online","status":"active","statusDescription":"",
          "statusFirstTime":false,"statusHistory":[],"steamDetails":{},"steamId":"","tags":[],
          "twoFactorAuthEnabled":false,"twoFactorAuthEnabledDate":null,"unsubscribe":false,
          "updated_at":"","userIcon":"","userLanguage":null,"userLanguageCode":null,
          "username":"${account.username}","viveId":"","pronouns":null
        }
    """.trimIndent()

    private companion object {
        const val OWNER_USER_ID = "usr_owner"
        const val TARGET_USER_ID = "usr_target"
        const val SECOND_TARGET_USER_ID = "usr_second_target"
        const val AUTH_USER_PATH = "/api/1/auth/user"
        const val MODERATIONS_PATH = "/api/1/auth/user/playermoderations"
        const val UNMODERATE_PATH = "/api/1/auth/user/unplayermoderate"
    }
}
