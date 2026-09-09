package io.github.vrcmteam.vrcm.presentation.screens.user

import com.russhwolf.settings.MapSettings
import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.di.supports.PersistentCookiesStorage
import io.github.vrcmteam.vrcm.network.api.auth.AuthApi
import io.github.vrcmteam.vrcm.network.api.playermoderation.PlayerModerationApi
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
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
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

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerVoiceModerationControllerTest {
    @Test
    fun duplicateToggleSubmitsOnceAndSwitchesToUnmuted() = runTest {
        val operations = mutableListOf<Pair<HttpMethod, String>>()
        val fixture = fixture(this) { request ->
            operations += request.method to request.url.encodedPath
            when (request.method) {
                HttpMethod.Get -> jsonResponse("[${moderationJson(TARGET_ONE, "mute")}]")
                HttpMethod.Put -> jsonResponse(SUCCESS_JSON)
                HttpMethod.Post -> jsonResponse(moderationJson(TARGET_ONE, "unmute"))
                else -> error("Unexpected request: ${request.method} ${request.url}")
            }
        }

        try {
            assertTrue(awaitReady(fixture.controller) { it.isMuted }.isMuted)
            val successes = mutableListOf<Boolean>()
            var failures = 0

            fixture.controller.toggle({ successes += it }) { failures++ }
            fixture.controller.toggle({ successes += it }) { failures++ }

            assertIs<PlayerVoiceModerationState.Updating>(fixture.controller.state.value)
            val ready = awaitReady(fixture.controller) { !it.isMuted }

            assertFalse(ready.isMuted)
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
    fun failedPairedUpdateReportsFailureAndReloadsAuthoritativeState() = runTest {
        var playerModerationGets = 0
        val fixture = fixture(this) { request ->
            when {
                request.method == HttpMethod.Get && request.url.encodedPath.endsWith("playermoderations") -> {
                    playerModerationGets++
                    jsonResponse(
                        if (playerModerationGets == 1) {
                            "[${moderationJson(TARGET_ONE, "mute")}]"
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
            awaitReady(fixture.controller) { it.isMuted }
            var successes = 0
            var failures = 0

            fixture.controller.toggle({ successes++ }) { failures++ }
            val ready = awaitReady(fixture.controller) { !it.isMuted }

            assertEquals(0, successes)
            assertEquals(1, failures)
            assertEquals(2, playerModerationGets)
            assertFalse(ready.isMuted)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun targetChangeRejectsOldCheckAndLoadsNewTarget() = runTest {
        val firstRequestStarted = CompletableDeferred<Unit>()
        val finishFirstRequest = CompletableDeferred<Unit>()
        val queriedTargets = mutableListOf<String?>()
        val fixture = fixture(this) { request ->
            val target = request.url.parameters["targetUserId"]
            queriedTargets += target
            if (queriedTargets.size == 1) {
                firstRequestStarted.complete(Unit)
                finishFirstRequest.await()
                jsonResponse("[${moderationJson(TARGET_ONE, "mute")}]")
            } else {
                jsonResponse("[${moderationJson(TARGET_TWO, "unmute")}]")
            }
        }

        try {
            runCurrent()
            firstRequestStarted.await()
            fixture.controller.setTargetUserId(TARGET_TWO)
            assertEquals(
                TARGET_TWO,
                assertIs<PlayerVoiceModerationState.Checking>(fixture.controller.state.value)
                    .targetUserId,
            )
            finishFirstRequest.complete(Unit)
            val ready = awaitReady(fixture.controller) { it.targetUserId == TARGET_TWO }

            assertEquals(TARGET_TWO, ready.targetUserId)
            assertFalse(ready.isMuted)
            assertEquals(listOf<String?>(TARGET_ONE, TARGET_TWO), queriedTargets)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun targetChangeDuringMutationRunsDeferredRefreshWithoutPublishingOldResult() = runTest {
        val mutationStarted = CompletableDeferred<Unit>()
        val finishMutation = CompletableDeferred<Unit>()
        val queriedTargets = mutableListOf<String?>()
        val fixture = fixture(this) { request ->
            when (request.method) {
                HttpMethod.Get -> {
                    val target = request.url.parameters["targetUserId"]
                    queriedTargets += target
                    jsonResponse(
                        if (target == TARGET_ONE) {
                            "[${moderationJson(TARGET_ONE, "mute")}]"
                        } else {
                            "[${moderationJson(TARGET_TWO, "unmute")}]"
                        },
                    )
                }
                HttpMethod.Put -> jsonResponse(SUCCESS_JSON)
                HttpMethod.Post -> {
                    mutationStarted.complete(Unit)
                    finishMutation.await()
                    jsonResponse(moderationJson(TARGET_ONE, "unmute"))
                }
                else -> error("Unexpected request: ${request.method} ${request.url}")
            }
        }

        try {
            awaitReady(fixture.controller) { it.isMuted }
            var successes = 0
            var failures = 0

            fixture.controller.toggle({ successes++ }) { failures++ }
            mutationStarted.await()
            fixture.controller.setTargetUserId(TARGET_TWO)
            assertEquals(
                TARGET_TWO,
                assertIs<PlayerVoiceModerationState.Checking>(fixture.controller.state.value)
                    .targetUserId,
            )
            finishMutation.complete(Unit)
            val ready = awaitReady(fixture.controller) { it.targetUserId == TARGET_TWO }

            assertFalse(ready.isMuted)
            assertEquals(0, successes)
            assertEquals(0, failures)
            assertEquals(listOf<String?>(TARGET_ONE, TARGET_TWO), queriedTargets)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun targetChangeDuringFailingMutationDoesNotReportOldFailure() = runTest {
        val mutationStarted = CompletableDeferred<Unit>()
        val finishMutation = CompletableDeferred<Unit>()
        val queriedTargets = mutableListOf<String?>()
        val fixture = fixture(this) { request ->
            when (request.method) {
                HttpMethod.Get -> {
                    val target = request.url.parameters["targetUserId"]
                    queriedTargets += target
                    jsonResponse(
                        if (target == TARGET_ONE) {
                            "[${moderationJson(TARGET_ONE, "mute")}]"
                        } else {
                            "[${moderationJson(TARGET_TWO, "unmute")}]"
                        },
                    )
                }
                HttpMethod.Put -> jsonResponse(SUCCESS_JSON)
                HttpMethod.Post -> {
                    mutationStarted.complete(Unit)
                    finishMutation.await()
                    respond("update failed", HttpStatusCode.InternalServerError)
                }
                else -> error("Unexpected request: ${request.method} ${request.url}")
            }
        }

        try {
            awaitReady(fixture.controller) { it.isMuted }
            var successes = 0
            var failures = 0

            fixture.controller.toggle({ successes++ }) { failures++ }
            mutationStarted.await()
            fixture.controller.setTargetUserId(TARGET_TWO)
            finishMutation.complete(Unit)
            val ready = awaitReady(fixture.controller) { it.targetUserId == TARGET_TWO }

            assertFalse(ready.isMuted)
            assertEquals(0, successes)
            assertEquals(0, failures)
            assertEquals(listOf<String?>(TARGET_ONE, TARGET_TWO), queriedTargets)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun externalSameAccountTokenChangeRejectsOldCheckAndReloads() = runTest {
        val firstRequestStarted = CompletableDeferred<Unit>()
        val finishFirstRequest = CompletableDeferred<Unit>()
        var playerModerationGets = 0
        val fixture = fixture(this) { request ->
            playerModerationGets++
            if (playerModerationGets == 1) {
                firstRequestStarted.complete(Unit)
                finishFirstRequest.await()
                jsonResponse("[${moderationJson(TARGET_ONE, "mute")}]")
            } else {
                jsonResponse("[${moderationJson(TARGET_ONE, "unmute")}]")
            }
        }

        try {
            runCurrent()
            firstRequestStarted.await()
            val previousToken = SharedFlowCentre.currentSession.value!!.token
            SharedFlowCentre.emitAuthenticated(fixture.account)
            val replacementToken = SharedFlowCentre.currentSession.value!!.token
            assertNotEquals(previousToken, replacementToken)
            finishFirstRequest.complete(Unit)
            val ready = awaitReady(fixture.controller) { it.sessionToken == replacementToken }

            assertFalse(ready.isMuted)
            assertEquals(replacementToken, ready.sessionToken)
            assertEquals(2, playerModerationGets)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun accountSwitchReloadsAndLogoutClearsSettledState() = runTest {
        var playerModerationGets = 0
        val fixture = fixture(this) { request ->
            playerModerationGets++
            jsonResponse(
                if (playerModerationGets == 1) {
                    "[${moderationJson(TARGET_ONE, "mute")}]"
                } else {
                    "[${moderationJson(TARGET_ONE, "unmute")}]"
                },
            )
        }

        try {
            assertTrue(awaitReady(fixture.controller) { it.isMuted }.isMuted)

            val secondAccount = fixture.account.copy(
                userId = "usr_second_account",
                username = "second-account",
            )
            SharedFlowCentre.emitAuthenticated(secondAccount)
            val switched = awaitReady(fixture.controller) {
                it.sessionToken.userId == secondAccount.userId
            }
            assertFalse(switched.isMuted)
            assertEquals(secondAccount.userId, switched.sessionToken.userId)

            SharedFlowCentre.emitLogout()
            awaitUnavailable(fixture.controller)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun conflictingOverridesUseParsedTimeAndStableInvalidFallback() = runTest {
        val fixture = fixture(this) { request ->
            when (request.url.parameters["targetUserId"]) {
                TARGET_ONE -> jsonResponse(
                    "[" +
                        moderationJson(
                            TARGET_ONE,
                            "unmute",
                            created = "2026-08-31T00:30:00Z",
                        ) + "," +
                        moderationJson(
                            TARGET_ONE,
                            "mute",
                            created = "2026-08-31T01:00:00+02:00",
                        ) +
                        "]",
                )
                TARGET_TWO -> jsonResponse(
                    "[" +
                        moderationJson(TARGET_TWO, "unmute", created = "invalid-first") + "," +
                        moderationJson(TARGET_TWO, "mute", created = "invalid-last") +
                        "]",
                )
                else -> error("Unexpected request: ${request.method} ${request.url}")
            }
        }

        try {
            assertFalse(awaitReady(fixture.controller) { !it.isMuted }.isMuted)

            fixture.controller.setTargetUserId(TARGET_TWO)
            assertTrue(
                awaitReady(fixture.controller) {
                    it.targetUserId == TARGET_TWO && it.isMuted
                }.isMuted,
            )
        } finally {
            fixture.close()
        }
    }

    @Test
    fun unauthorizedCheckCommitsRetriedResponseWithRenewedToken() = runTest {
        var playerModerationGets = 0
        var authUserGets = 0
        val fixture = fixture(this) { request ->
            when (request.url.encodedPath) {
                "/api/1/auth/user/playermoderations" -> {
                    playerModerationGets++
                    if (playerModerationGets == 1) {
                        respond("expired", HttpStatusCode.Unauthorized)
                    } else {
                        jsonResponse("[${moderationJson(TARGET_ONE, "mute")}]")
                    }
                }
                "/api/1/auth/user" -> {
                    authUserGets++
                    jsonResponse(currentUserJson(fixtureAccount()))
                }
                else -> error("Unexpected request: ${request.method} ${request.url}")
            }
        }
        val initialToken = SharedFlowCentre.currentSession.value!!.token

        try {
            val ready = awaitReady(fixture.controller) { it.sessionToken != initialToken }

            val renewedToken = SharedFlowCentre.currentSession.value!!.token
            assertNotEquals(initialToken, renewedToken)
            assertEquals(renewedToken, ready.sessionToken)
            assertTrue(ready.isMuted)
            assertEquals(2, playerModerationGets)
            assertEquals(2, authUserGets)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun unauthorizedPairedUpdateRetriesTransitionAndCommitsRenewedToken() = runTest {
        var playerModerationGets = 0
        var removals = 0
        var creations = 0
        var authUserGets = 0
        val fixture = fixture(this) { request ->
            when (request.url.encodedPath) {
                "/api/1/auth/user/playermoderations" -> when (request.method) {
                    HttpMethod.Get -> {
                        playerModerationGets++
                        jsonResponse("[${moderationJson(TARGET_ONE, "mute")}]")
                    }
                    HttpMethod.Post -> {
                        creations++
                        if (creations == 1) {
                            respond("expired", HttpStatusCode.Unauthorized)
                        } else {
                            jsonResponse(moderationJson(TARGET_ONE, "unmute"))
                        }
                    }
                    else -> error("Unexpected request: ${request.method} ${request.url}")
                }
                "/api/1/auth/user/unplayermoderate" -> {
                    removals++
                    jsonResponse(SUCCESS_JSON)
                }
                "/api/1/auth/user" -> {
                    authUserGets++
                    jsonResponse(currentUserJson(fixtureAccount()))
                }
                else -> error("Unexpected request: ${request.method} ${request.url}")
            }
        }

        try {
            awaitReady(fixture.controller) { it.isMuted }
            val initialToken = SharedFlowCentre.currentSession.value!!.token
            val successes = mutableListOf<Boolean>()
            var failures = 0

            fixture.controller.toggle({ successes += it }) { failures++ }
            val ready = awaitReady(fixture.controller) {
                !it.isMuted && it.sessionToken != initialToken
            }

            assertEquals(SharedFlowCentre.currentSession.value!!.token, ready.sessionToken)
            assertEquals(listOf(false), successes)
            assertEquals(0, failures)
            assertEquals(1, playerModerationGets)
            assertEquals(2, removals)
            assertEquals(2, creations)
            assertEquals(2, authUserGets)
        } finally {
            fixture.close()
        }
    }

    private suspend fun TestScope.awaitReady(
        controller: PlayerVoiceModerationController,
        predicate: (PlayerVoiceModerationState.Ready) -> Boolean,
    ): PlayerVoiceModerationState.Ready = withTimeout(3_000) {
        while (true) {
            runCurrent()
            val ready = controller.state.value as? PlayerVoiceModerationState.Ready
            if (ready != null && predicate(ready)) return@withTimeout ready
            yield()
        }
        error("Unreachable")
    }

    private suspend fun TestScope.awaitUnavailable(
        controller: PlayerVoiceModerationController,
    ) = withTimeout(3_000) {
        while (controller.state.value !is PlayerVoiceModerationState.Unavailable) {
            runCurrent()
            yield()
        }
    }

    private data class Fixture(
        val account: AccountDto,
        val client: HttpClient,
        val controller: PlayerVoiceModerationController,
        val controllerJob: Job,
    ) {
        suspend fun close() {
            SharedFlowCentre.emitLogout()
            controllerJob.cancel()
            client.close()
        }
    }

    private suspend fun fixture(
        scope: CoroutineScope,
        handler: MockRequestHandler,
    ): Fixture {
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
        val controllerJob = SupervisorJob()
        val controllerScope = CoroutineScope(
            scope.coroutineContext.minusKey(Job) + controllerJob,
        )
        return Fixture(
            account = account,
            client = client,
            controller = PlayerVoiceModerationController(
                initialTargetUserId = TARGET_ONE,
                authService = authService,
                playerModerationApi = PlayerModerationApi(client),
                scope = controllerScope,
            ),
            controllerJob = controllerJob,
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

    private companion object {
        const val TARGET_ONE = "usr_target_one"
        const val TARGET_TWO = "usr_target_two"
        const val SUCCESS_JSON = """{"success":{"message":"removed","status_code":200}}"""
    }
}
