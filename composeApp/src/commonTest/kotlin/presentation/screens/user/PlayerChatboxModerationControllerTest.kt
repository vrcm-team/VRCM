package io.github.vrcmteam.vrcm.presentation.screens.user

import com.russhwolf.settings.MapSettings
import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.di.supports.PersistentCookiesStorage
import io.github.vrcmteam.vrcm.network.api.auth.AuthApi
import io.github.vrcmteam.vrcm.network.api.playermoderation.PlayerChatboxModerationApi
import io.github.vrcmteam.vrcm.service.AuthService
import io.github.vrcmteam.vrcm.service.data.AccountDto
import io.github.vrcmteam.vrcm.storage.AccountCacheManager
import io.github.vrcmteam.vrcm.storage.AccountDao
import io.github.vrcmteam.vrcm.storage.InMemoryFriendListCacheStore
import io.github.vrcmteam.vrcm.storage.InMemorySecureStorage
import io.github.vrcmteam.vrcm.storage.InMemoryUserProfileCacheStore
import io.github.vrcmteam.vrcm.storage.meetup.MeetupCardAssetStore
import io.github.vrcmteam.vrcm.storage.meetup.MeetupCardConfigDao
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.plugins.defaultRequest
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import org.koin.core.logger.EmptyLogger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class PlayerChatboxModerationControllerTest {
    @Test
    fun duplicateToggleRemovesOldOverrideBeforeCreatingNewOne() = runBlocking {
        val operations = mutableListOf<Pair<HttpMethod, String>>()
        val fixture = fixture { request ->
            operations += request.method to request.url.encodedPath
            when (request.method) {
                HttpMethod.Get -> jsonResponse("[${moderationJson(TARGET_ONE, "muteChat")}]")
                HttpMethod.Put -> jsonResponse(SUCCESS_JSON)
                HttpMethod.Post -> jsonResponse(moderationJson(TARGET_ONE, "unmuteChat"))
                else -> error("Unexpected request: ${request.method} ${request.url}")
            }
        }

        try {
            assertTrue(fixture.controller.awaitState<PlayerChatboxModerationState.Ready>().isMuted)
            val successes = mutableListOf<Boolean>()
            var failures = 0

            fixture.controller.toggle(successes::add) { failures++ }
            fixture.controller.toggle(successes::add) { failures++ }

            assertIs<PlayerChatboxModerationState.Updating>(fixture.controller.state.value)

            assertFalse(
                fixture.controller.awaitState<PlayerChatboxModerationState.Ready> { !it.isMuted }.isMuted,
            )
            assertEquals(listOf(false), successes)
            assertEquals(0, failures)
            assertEquals(
                listOf(
                    HttpMethod.Get to "/api/1/auth/user/playermoderations",
                    HttpMethod.Put to "/api/1/auth/user/unplayermoderate",
                    HttpMethod.Post to "/api/1/auth/user/playermoderations",
                ),
                operations,
            )
        } finally {
            fixture.close()
        }
    }

    @Test
    fun conflictingOverridesAreBothRemovedBeforeOneDesiredOverrideIsCreated() = runBlocking {
        val writeBodies = mutableListOf<Pair<HttpMethod, String>>()
        val fixture = fixture { request ->
            when (request.method) {
                HttpMethod.Get -> jsonResponse(
                    """[
                        ${moderationJson(TARGET_ONE, "muteChat", "2026-08-31T01:00:00+02:00")},
                        ${moderationJson(TARGET_ONE, "unmuteChat", "2026-08-31T00:30:00Z")}
                    ]""",
                )
                HttpMethod.Put -> {
                    writeBodies += request.method to request.bodyText()
                    jsonResponse(SUCCESS_JSON)
                }
                HttpMethod.Post -> {
                    writeBodies += request.method to request.bodyText()
                    jsonResponse(moderationJson(TARGET_ONE, "muteChat"))
                }
                else -> error("Unexpected request: ${request.method} ${request.url}")
            }
        }

        try {
            assertFalse(fixture.controller.awaitState<PlayerChatboxModerationState.Ready>().isMuted)

            fixture.controller.toggle({}, {})
            val ready = fixture.controller.awaitState<PlayerChatboxModerationState.Ready> { it.isMuted }

            assertEquals(
                listOf(
                    HttpMethod.Put to requestJson("muteChat"),
                    HttpMethod.Put to requestJson("unmuteChat"),
                    HttpMethod.Post to requestJson("muteChat"),
                ),
                writeBodies,
            )
            assertTrue(ready.isMuted)
            assertEquals(1, ready.activeTypes.size)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun invalidCreatedTimestampFallsBackToStableResponseOrder() = runBlocking {
        val fixture = fixture { request ->
            check(request.method == HttpMethod.Get)
            jsonResponse(
                """[
                    ${moderationJson(TARGET_ONE, "unmuteChat", "2099-01-01T00:00:00Z")},
                    ${moderationJson(TARGET_ONE, "muteChat", "not-a-timestamp")}
                ]""",
            )
        }

        try {
            assertTrue(fixture.controller.awaitState<PlayerChatboxModerationState.Ready>().isMuted)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun failedPairedUpdateReloadsAuthoritativeRemoteState() = runBlocking {
        var moderationGets = 0
        val fixture = fixture { request ->
            when {
                request.method == HttpMethod.Get &&
                    request.url.encodedPath.endsWith("playermoderations") -> {
                    moderationGets++
                    jsonResponse(
                        if (moderationGets == 1) {
                            "[${moderationJson(TARGET_ONE, "muteChat")}]"
                        } else {
                            "[]"
                        },
                    )
                }
                request.method == HttpMethod.Put -> jsonResponse(SUCCESS_JSON)
                request.method == HttpMethod.Post -> respond(
                    content = "update failed",
                    status = HttpStatusCode.InternalServerError,
                )
                else -> error("Unexpected request: ${request.method} ${request.url}")
            }
        }

        try {
            assertTrue(fixture.controller.awaitState<PlayerChatboxModerationState.Ready>().isMuted)
            var successes = 0
            var failures = 0

            fixture.controller.toggle({ successes++ }) { failures++ }
            val ready = fixture.controller.awaitState<PlayerChatboxModerationState.Ready> {
                !it.isMuted
            }

            assertEquals(0, successes)
            assertEquals(1, failures)
            assertEquals(2, moderationGets)
            assertFalse(ready.isMuted)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun failedInitialCheckCanBeRetried() = runBlocking {
        var moderationGets = 0
        val fixture = fixture { request ->
            moderationGets++
            if (moderationGets == 1) {
                respond("offline", HttpStatusCode.ServiceUnavailable)
            } else {
                jsonResponse("[${moderationJson(TARGET_ONE, "muteChat")}]")
            }
        }

        try {
            fixture.controller.awaitState<PlayerChatboxModerationState.Failed>()

            fixture.controller.retry()

            assertTrue(fixture.controller.awaitState<PlayerChatboxModerationState.Ready>().isMuted)
            assertEquals(2, moderationGets)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun targetChangeRejectsOldCheckAndLoadsNewTarget() = runBlocking {
        val firstRequestStarted = CompletableDeferred<Unit>()
        val finishFirstRequest = CompletableDeferred<Unit>()
        val queriedTargets = mutableListOf<String?>()
        val fixture = fixture { request ->
            val target = request.url.parameters["targetUserId"]
            queriedTargets += target
            if (queriedTargets.size == 1) {
                firstRequestStarted.complete(Unit)
                finishFirstRequest.await()
                jsonResponse("[${moderationJson(TARGET_ONE, "muteChat")}]")
            } else {
                jsonResponse("[${moderationJson(TARGET_TWO, "unmuteChat")}]")
            }
        }

        try {
            firstRequestStarted.await()
            fixture.controller.setTargetUserId(TARGET_TWO)
            assertIs<PlayerChatboxModerationState.Checking>(fixture.controller.state.value)
            finishFirstRequest.complete(Unit)

            val ready = fixture.controller.awaitState<PlayerChatboxModerationState.Ready> {
                it.targetUserId == TARGET_TWO
            }
            assertEquals(TARGET_TWO, ready.targetUserId)
            assertFalse(ready.isMuted)
            assertEquals(listOf<String?>(TARGET_ONE, TARGET_TWO), queriedTargets)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun targetChangeDuringMutationImmediatelyChecksNewTargetAndRejectsOldCompletion() = runBlocking {
        val removalStarted = CompletableDeferred<Unit>()
        val finishRemoval = CompletableDeferred<Unit>()
        val queriedTargets = mutableListOf<String?>()
        val fixture = fixture { request ->
            when (request.method) {
                HttpMethod.Get -> {
                    val target = request.url.parameters["targetUserId"]
                    queriedTargets += target
                    jsonResponse(
                        if (target == TARGET_ONE) {
                            "[${moderationJson(TARGET_ONE, "muteChat")}]"
                        } else {
                            "[${moderationJson(TARGET_TWO, "unmuteChat")}]"
                        },
                    )
                }
                HttpMethod.Put -> {
                    removalStarted.complete(Unit)
                    finishRemoval.await()
                    jsonResponse(SUCCESS_JSON)
                }
                HttpMethod.Post -> respond(
                    content = "old target update failed",
                    status = HttpStatusCode.InternalServerError,
                )
                else -> error("Unexpected request: ${request.method} ${request.url}")
            }
        }

        try {
            assertTrue(fixture.controller.awaitState<PlayerChatboxModerationState.Ready>().isMuted)
            var successes = 0
            var failures = 0

            fixture.controller.toggle({ successes++ }) { failures++ }
            removalStarted.await()
            assertIs<PlayerChatboxModerationState.Updating>(fixture.controller.state.value)

            fixture.controller.setTargetUserId(TARGET_TWO)
            assertIs<PlayerChatboxModerationState.Checking>(fixture.controller.state.value)
            finishRemoval.complete(Unit)

            val ready = fixture.controller.awaitState<PlayerChatboxModerationState.Ready> {
                it.targetUserId == TARGET_TWO
            }
            assertEquals(TARGET_TWO, ready.targetUserId)
            assertFalse(ready.isMuted)
            assertEquals(0, successes)
            assertEquals(0, failures)
            assertEquals(listOf<String?>(TARGET_ONE, TARGET_TWO), queriedTargets)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun externalSameAccountTokenChangeRejectsOldCheckAndReloads() = runBlocking {
        val firstRequestStarted = CompletableDeferred<Unit>()
        val finishFirstRequest = CompletableDeferred<Unit>()
        var moderationGets = 0
        val fixture = fixture { request ->
            moderationGets++
            if (moderationGets == 1) {
                firstRequestStarted.complete(Unit)
                finishFirstRequest.await()
                jsonResponse("[${moderationJson(TARGET_ONE, "muteChat")}]")
            } else {
                jsonResponse("[${moderationJson(TARGET_ONE, "unmuteChat")}]")
            }
        }

        try {
            firstRequestStarted.await()
            val previousToken = SharedFlowCentre.currentSession.value!!.token
            SharedFlowCentre.emitAuthenticated(fixture.account)
            val replacementToken = SharedFlowCentre.currentSession.value!!.token
            assertNotEquals(previousToken, replacementToken)
            finishFirstRequest.complete(Unit)

            val ready = fixture.controller.awaitState<PlayerChatboxModerationState.Ready> {
                it.sessionToken == replacementToken
            }
            assertFalse(ready.isMuted)
            assertEquals(replacementToken, ready.sessionToken)
            assertEquals(2, moderationGets)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun accountSwitchReloadsAndLogoutClearsSettledState(): Unit = runBlocking {
        var moderationGets = 0
        val fixture = fixture { request ->
            moderationGets++
            jsonResponse(
                if (moderationGets == 1) {
                    "[${moderationJson(TARGET_ONE, "muteChat")}]"
                } else {
                    "[${moderationJson(TARGET_ONE, "unmuteChat")}]"
                },
            )
        }

        try {
            assertTrue(fixture.controller.awaitState<PlayerChatboxModerationState.Ready>().isMuted)

            val secondAccount = fixture.account.copy(
                userId = "usr_second_account",
                username = "second-account",
            )
            SharedFlowCentre.emitAuthenticated(secondAccount)
            val switched = fixture.controller.awaitState<PlayerChatboxModerationState.Ready> {
                it.sessionToken.userId == secondAccount.userId
            }
            assertFalse(switched.isMuted)
            assertEquals(secondAccount.userId, switched.sessionToken.userId)

            SharedFlowCentre.emitLogout()
            fixture.controller.awaitState<PlayerChatboxModerationState.Unavailable>()
        } finally {
            fixture.close()
        }
    }

    @Test
    fun unauthorizedPairedMutationRetriesWithRenewedToken() = runBlocking {
        var moderationPosts = 0
        var moderationRemovals = 0
        var authUserGets = 0
        val writes = mutableListOf<HttpMethod>()
        val fixture = fixture { request ->
            when {
                request.url.encodedPath == "/api/1/auth/user/playermoderations" &&
                    request.method == HttpMethod.Get ->
                    jsonResponse("[${moderationJson(TARGET_ONE, "muteChat")}]")
                request.url.encodedPath == "/api/1/auth/user/unplayermoderate" -> {
                    writes += request.method
                    moderationRemovals++
                    jsonResponse(SUCCESS_JSON)
                }
                request.url.encodedPath == "/api/1/auth/user/playermoderations" &&
                    request.method == HttpMethod.Post -> {
                    writes += request.method
                    moderationPosts++
                    if (moderationPosts == 1) {
                        respond("expired", HttpStatusCode.Unauthorized)
                    } else {
                        jsonResponse(moderationJson(TARGET_ONE, "unmuteChat"))
                    }
                }
                request.url.encodedPath == "/api/1/auth/user" -> {
                    authUserGets++
                    jsonResponse(currentUserJson(fixtureAccount()))
                }
                else -> error("Unexpected request: ${request.method} ${request.url}")
            }
        }
        val initialToken = SharedFlowCentre.currentSession.value!!.token

        try {
            assertTrue(fixture.controller.awaitState<PlayerChatboxModerationState.Ready>().isMuted)

            fixture.controller.toggle({}, {})

            val ready = fixture.controller.awaitState<PlayerChatboxModerationState.Ready> {
                !it.isMuted && it.sessionToken != initialToken
            }
            val renewedToken = SharedFlowCentre.currentSession.value!!.token
            assertNotEquals(initialToken, renewedToken)
            assertEquals(renewedToken, ready.sessionToken)
            assertFalse(ready.isMuted)
            assertEquals(2, moderationRemovals)
            assertEquals(2, moderationPosts)
            assertEquals(
                listOf(HttpMethod.Put, HttpMethod.Post, HttpMethod.Put, HttpMethod.Post),
                writes,
            )
            assertEquals(2, authUserGets)
        } finally {
            fixture.close()
        }
    }

    private data class Fixture(
        val account: AccountDto,
        val client: HttpClient,
        val controller: PlayerChatboxModerationController,
        val controllerScope: CoroutineScope,
    ) {
        suspend fun close() {
            SharedFlowCentre.emitLogout()
            controllerScope.cancel()
            client.close()
        }
    }

    private suspend fun fixture(handler: MockRequestHandler): Fixture {
        SharedFlowCentre.emitLogout()
        val account = fixtureAccount()
        val cookies = PersistentCookiesStorage(EmptyLogger())
        val client = HttpClient(MockEngine) {
            engine { addHandler(handler) }
            defaultRequest { url("https://api.vrchat.cloud/api/1/") }
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
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
                friendActivityStore = io.github.vrcmteam.vrcm.storage.NoOpFriendActivityCacheStore,
                meetupCardConfigDao = MeetupCardConfigDao(MapSettings()),
                meetupCardAssetStore = MeetupCardAssetStore(
                    FakeFileSystem(),
                    "/meetup-assets".toPath(),
                ),
            ),
        )
        SharedFlowCentre.emitAuthenticated(account)
        val controllerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        return Fixture(
            account = account,
            client = client,
            controller = PlayerChatboxModerationController(
                initialTargetUserId = TARGET_ONE,
                authService = authService,
                moderationApi = PlayerChatboxModerationApi(client),
                scope = controllerScope,
            ),
            controllerScope = controllerScope,
        )
    }

    private fun fixtureAccount() = AccountDto(
        userId = "usr_current",
        username = "current-user",
        password = "current-password",
        current = true,
        authCookie = "current-auth",
        twoFactorAuthCookie = "current-2fa",
    )

    private fun MockRequestHandleScope.jsonResponse(body: String) = respond(
        content = body,
        status = HttpStatusCode.OK,
        headers = headersOf(HttpHeaders.ContentType, "application/json"),
    )

    private fun io.ktor.client.request.HttpRequestData.bodyText(): String =
        (body as OutgoingContent.ByteArrayContent).bytes().decodeToString()

    private fun requestJson(type: String) =
        """{"moderated":"$TARGET_ONE","type":"$type"}"""

    private fun moderationJson(
        targetUserId: String,
        type: String,
        created: String = "2026-08-31T00:00:00.000Z",
    ) = """
        {
          "created":"$created",
          "id":"pmod_$type",
          "sourceDisplayName":"Current User",
          "sourceUserId":"usr_current",
          "targetDisplayName":"Target User",
          "targetUserId":"$targetUserId",
          "type":"$type"
        }
    """.trimIndent()

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
          "googleId":"","hasBirthday":true,"hasEmail":true,"hasLoggedInFromClient":true,
          "hasPendingEmail":false,"hideContentFilterSettings":false,"homeLocation":"",
          "id":"${account.userId}","isFriend":false,"last_activity":"","last_login":"",
          "last_platform":"standalonewindows","obfuscatedEmail":"",
          "obfuscatedPendingEmail":"","oculusId":"","offlineFriends":[],
          "onlineFriends":[],"pastDisplayNames":[],"picoId":"",
          "presence":{"avatarThumbnail":null,"displayName":"${account.username}","groups":[],
            "id":"${account.userId}","instance":"","instanceType":"","isRejoining":null,
            "platform":"standalonewindows","profilePicOverride":null,"status":"active",
            "travelingToInstance":"","travelingToWorld":"","world":""},
          "profilePicOverride":"","state":"online","status":"active",
          "statusDescription":"","statusFirstTime":false,"statusHistory":[],
          "steamDetails":{},"steamId":"","tags":[],"twoFactorAuthEnabled":false,
          "twoFactorAuthEnabledDate":null,"unsubscribe":false,"updated_at":"",
          "userIcon":"","userLanguage":null,"userLanguageCode":null,
          "username":"${account.username}","viveId":"","pronouns":null
        }
    """.trimIndent()

    private suspend inline fun <reified T : PlayerChatboxModerationState>
        PlayerChatboxModerationController.awaitState(
            crossinline predicate: (T) -> Boolean = { true },
        ): T = withTimeout(5_000) {
            state.filterIsInstance<T>().first { predicate(it) }
        }

    private companion object {
        const val TARGET_ONE = "usr_target_one"
        const val TARGET_TWO = "usr_target_two"
        const val SUCCESS_JSON =
            """{"success":{"message":"removed","status_code":200}}"""
    }
}
