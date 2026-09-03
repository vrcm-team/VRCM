package io.github.vrcmteam.vrcm.service

import com.russhwolf.settings.MapSettings
import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.di.supports.PersistentCookiesStorage
import io.github.vrcmteam.vrcm.network.api.attributes.AUTH_COOKIE
import io.github.vrcmteam.vrcm.network.api.attributes.AuthState
import io.github.vrcmteam.vrcm.network.api.avatars.AvatarsApi
import io.github.vrcmteam.vrcm.network.api.auth.AuthApi
import io.github.vrcmteam.vrcm.network.api.users.UsersApi
import io.github.vrcmteam.vrcm.network.supports.VRCApiException
import io.github.vrcmteam.vrcm.presentation.screens.avatar.NetworkAvatarSelector
import io.github.vrcmteam.vrcm.service.data.AccountDto
import io.github.vrcmteam.vrcm.storage.AccountCacheManager
import io.github.vrcmteam.vrcm.storage.AccountDao
import io.github.vrcmteam.vrcm.storage.InMemoryFriendListCacheStore
import io.github.vrcmteam.vrcm.storage.InMemorySecureStorage
import io.github.vrcmteam.vrcm.storage.InMemoryUserProfileCacheStore
import io.github.vrcmteam.vrcm.storage.meetup.MeetupCardAssetStore
import io.github.vrcmteam.vrcm.storage.meetup.MeetupCardConfigDao
import io.github.vrcmteam.vrcm.testing.MainDispatcherTest
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.plugins.defaultRequest
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.koin.core.logger.EmptyLogger
import org.koin.core.logger.Level
import org.koin.core.logger.Logger
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AuthServiceTest : MainDispatcherTest() {
    @AfterTest
    fun clearSession() = runTest { SharedFlowCentre.emitLogout() }

    @Test
    fun cachedCookieRestoresWithOneRequest() = runTest {
        val requests = mutableListOf<Pair<String?, String?>>()
        val fixture = fixture { request ->
            requests += request.headers[HttpHeaders.Cookie] to request.headers[HttpHeaders.Authorization]
            jsonResponse(currentUserJson(cachedAccount()))
        }

        val result = fixture.service.restoreAuth()

        assertIs<AuthState.Authed>(result)
        assertEquals(1, requests.size)
        assertTrue(requests.single().first.orEmpty().contains("auth=cached-auth"))
        assertTrue(requests.single().first.orEmpty().contains("twoFactorAuth=cached-2fa"))
        assertEquals("usr_cached", fixture.service.currentUserState.value?.id)
        fixture.client.close()
    }

    @Test
    fun homeWorldUpdateRetriesAuthenticationAndPublishesServerResponse() = runTest {
        var updateRequests = 0
        val fixture = fixture { request ->
            when (request.method) {
                HttpMethod.Put -> {
                    updateRequests++
                    if (updateRequests == 1) {
                        respond(
                            content = "expired",
                            status = HttpStatusCode.Unauthorized,
                        )
                    } else {
                        jsonResponse(
                            currentUserJson(
                                account = cachedAccount(),
                                homeLocation = "wrld_server_authoritative",
                            )
                        )
                    }
                }
                else -> jsonResponse(currentUserJson(cachedAccount()))
            }
        }
        assertIs<AuthState.Authed>(fixture.service.restoreAuth())

        val result = HomeWorldService(
            usersApi = UsersApi(fixture.client),
            authService = fixture.service,
        ).setHomeWorld("wrld_requested")

        assertEquals("wrld_server_authoritative", result.getOrThrow())
        assertEquals("wrld_server_authoritative", fixture.service.currentUserState.value?.homeLocation)
        assertEquals(2, updateRequests)
        fixture.client.close()
    }

    @Test
    fun accountSwitchRejectsLateHomeWorldResponse() = runTest {
        val updateStarted = CompletableDeferred<Unit>()
        val releaseUpdate = CompletableDeferred<Unit>()
        val fixture = fixture { request ->
            if (request.method == HttpMethod.Put) {
                updateStarted.complete(Unit)
                releaseUpdate.await()
                jsonResponse(
                    currentUserJson(
                        account = cachedAccount(),
                        homeLocation = "wrld_late",
                    )
                )
            } else {
                jsonResponse(currentUserJson(cachedAccount()))
            }
        }
        assertIs<AuthState.Authed>(fixture.service.restoreAuth())
        val update = async(start = CoroutineStart.UNDISPATCHED) {
            HomeWorldService(
                usersApi = UsersApi(fixture.client),
                authService = fixture.service,
            ).setHomeWorld("wrld_requested")
        }
        updateStarted.await()

        SharedFlowCentre.emitAuthenticated(AccountDto(userId = "usr_other", username = "other"))
        releaseUpdate.complete(Unit)

        assertIs<HomeWorldSessionChangedException>(update.await().exceptionOrNull())
        assertTrue(fixture.service.currentUserState.value?.homeLocation != "wrld_late")
        fixture.client.close()
    }

    @Test
    fun duplicateHomeWorldUpdateIsRejectedUntilTheFirstRequestFinishes() = runTest {
        val firstUpdateStarted = CompletableDeferred<Unit>()
        val releaseFirstUpdate = CompletableDeferred<Unit>()
        var updateRequests = 0
        val fixture = fixture { request ->
            if (request.method == HttpMethod.Put) {
                updateRequests++
                if (updateRequests == 1) {
                    firstUpdateStarted.complete(Unit)
                    releaseFirstUpdate.await()
                }
                jsonResponse(
                    currentUserJson(
                        account = cachedAccount(),
                        homeLocation = "wrld_server_$updateRequests",
                    )
                )
            } else {
                jsonResponse(currentUserJson(cachedAccount()))
            }
        }
        assertIs<AuthState.Authed>(fixture.service.restoreAuth())
        val homeWorldService = HomeWorldService(UsersApi(fixture.client), fixture.service)
        val first = async(start = CoroutineStart.UNDISPATCHED) {
            homeWorldService.setHomeWorld("wrld_first")
        }
        firstUpdateStarted.await()

        assertIs<HomeWorldUpdateInFlightException>(
            homeWorldService.setHomeWorld("wrld_duplicate").exceptionOrNull()
        )
        releaseFirstUpdate.complete(Unit)
        assertEquals("wrld_server_1", first.await().getOrThrow())
        assertEquals(
            "wrld_server_2",
            homeWorldService.setHomeWorld("wrld_after-completion").getOrThrow(),
        )
        assertEquals(2, updateRequests)
        fixture.client.close()
    }

    @Test
    fun resetHomeWorldDoesNotClearAHomeWorldChangedBeforeSubmission() = runTest {
        var updateRequests = 0
        val fixture = fixture { request ->
            if (request.method == HttpMethod.Put) {
                updateRequests++
                jsonResponse(currentUserJson(cachedAccount(), homeLocation = ""))
            } else {
                jsonResponse(currentUserJson(cachedAccount(), homeLocation = "wrld_confirmed"))
            }
        }
        assertIs<AuthState.Authed>(fixture.service.restoreAuth())
        val sessionToken = requireNotNull(SharedFlowCentre.currentSession.value).token
        fixture.service.applyCurrentUserHomeLocation(
            sessionToken = sessionToken,
            userId = sessionToken.userId,
            homeLocation = "wrld_later",
        )

        val result = HomeWorldService(UsersApi(fixture.client), fixture.service).resetHomeWorld(
            expectedWorldId = "wrld_confirmed",
            expectedSessionToken = sessionToken,
        )

        assertIs<HomeWorldStateChangedException>(result.exceptionOrNull())
        assertEquals(0, updateRequests)
        assertEquals("wrld_later", fixture.service.currentUserState.value?.homeLocation)
        fixture.client.close()
    }

    @Test
    fun resetHomeWorldWaitsForUserRefreshBeforeSubmitting() = runTest {
        val refreshStarted = CompletableDeferred<Unit>()
        val releaseRefresh = CompletableDeferred<Unit>()
        var userRequests = 0
        var updateRequests = 0
        val fixture = fixture { request ->
            when {
                request.method == HttpMethod.Put -> {
                    updateRequests++
                    jsonResponse(currentUserJson(cachedAccount(), homeLocation = ""))
                }

                request.method == HttpMethod.Get && userRequests++ == 0 ->
                    jsonResponse(currentUserJson(cachedAccount(), homeLocation = "wrld_confirmed"))

                request.method == HttpMethod.Get -> {
                    refreshStarted.complete(Unit)
                    releaseRefresh.await()
                    jsonResponse(currentUserJson(cachedAccount(), homeLocation = "wrld_later"))
                }

                else -> error("Unexpected request: ${request.method} ${request.url}")
            }
        }
        assertIs<AuthState.Authed>(fixture.service.restoreAuth())
        val sessionToken = requireNotNull(SharedFlowCentre.currentSession.value).token
        val refresh = async(start = CoroutineStart.UNDISPATCHED) {
            fixture.service.currentUser(isRefresh = true)
        }
        refreshStarted.await()
        val reset = async(start = CoroutineStart.UNDISPATCHED) {
            HomeWorldService(UsersApi(fixture.client), fixture.service).resetHomeWorld(
                expectedWorldId = "wrld_confirmed",
                expectedSessionToken = sessionToken,
            )
        }

        assertEquals(0, updateRequests)
        releaseRefresh.complete(Unit)
        assertEquals("wrld_later", refresh.await().homeLocation)
        assertIs<HomeWorldStateChangedException>(reset.await().exceptionOrNull())
        assertEquals(0, updateRequests)
        fixture.client.close()
    }

    @Test
    fun unauthorizedCachedCookieFallsBackToSavedPassword() = runTest {
        val requests = mutableListOf<Pair<String?, String?>>()
        val fixture = fixture { request ->
            val authorization = request.headers[HttpHeaders.Authorization]
            requests += request.headers[HttpHeaders.Cookie] to authorization
            when {
                requests.size == 1 -> respond("expired", HttpStatusCode.Unauthorized)
                else -> jsonResponse(currentUserJson(cachedAccount()))
            }
        }

        val result = fixture.service.restoreAuth()

        assertIs<AuthState.Authed>(result)
        assertEquals(3, requests.size)
        assertNull(requests.first().second)
        assertTrue(requests[1].second.orEmpty().startsWith("Basic "))
        fixture.client.close()
    }

    @Test
    fun explicitLogoutDoesNotRestoreFromSavedPassword() = runTest {
        SharedFlowCentre.emitLogout()
        var requestCount = 0
        val fixture = fixture {
            requestCount++
            error("No request is expected after explicit logout")
        }

        val logoutObserved = async(start = CoroutineStart.UNDISPATCHED) {
            SharedFlowCentre.logout.first()
        }
        fixture.service.logout()
        logoutObserved.await()
        val result = fixture.service.restoreAuth()

        assertNull(result)
        assertEquals(0, requestCount)
        assertNull(fixture.accountDao.currentAccountDtoOrNull()?.authCookie)
        assertNull(fixture.service.currentUserState.value)
        assertEquals(
            "cached-2fa",
            fixture.accountDao.currentAccountDtoOrNull()?.twoFactorAuthCookie,
        )
        fixture.client.close()
    }

    @Test
    fun accountLoginWaitsForSessionBoundRequest() = runTest {
        val secondAccount = AccountDto(
            userId = "usr_second",
            username = "second-user",
            password = "second-password",
            authCookie = "second-auth",
            twoFactorAuthCookie = "second-2fa",
        )
        var requestCount = 0
        val loginStarted = CompletableDeferred<Unit>()
        val fixture = fixture { request ->
            requestCount++
            if (request.headers[HttpHeaders.Authorization] != null) {
                loginStarted.complete(Unit)
                jsonResponse(currentUserJson(secondAccount))
            } else if (requestCount == 1) {
                jsonResponse(currentUserJson(cachedAccount()))
            } else {
                jsonResponse(currentUserJson(secondAccount))
            }
        }
        fixture.accountDao.saveAccountInfo(secondAccount)
        fixture.accountDao.saveAccountInfo(cachedAccount())
        fixture.service.restoreAuth()
        val firstSession = assertNotNull(SharedFlowCentre.currentSession.value)
        val requestStarted = CompletableDeferred<Unit>()
        val finishRequest = CompletableDeferred<Unit>()

        val request = async(start = CoroutineStart.UNDISPATCHED) {
            fixture.service.runSessionBoundCatching(firstSession.token) {
                requestStarted.complete(Unit)
                finishRequest.await()
                "sent"
            }
        }
        requestStarted.await()
        val login = async(start = CoroutineStart.UNDISPATCHED) {
            fixture.service.login(secondAccount.username, secondAccount.password.orEmpty())
        }

        assertFalse(loginStarted.isCompleted)
        finishRequest.complete(Unit)
        val response = assertNotNull(request.await())
        assertEquals("sent", response.result.getOrThrow())
        assertEquals(firstSession.token, response.sessionToken)
        assertIs<AuthState.Authed>(login.await())
        assertTrue(loginStarted.isCompleted)
        assertEquals("usr_second", SharedFlowCentre.currentSession.value?.account?.userId)
        fixture.client.close()
    }

    @Test
    fun accountLoginReplacesPreviousCookieContext() = runTest {
        val secondAccount = AccountDto(
            userId = "usr_second",
            username = "second-user",
            password = "second-password",
            authCookie = "second-auth",
            twoFactorAuthCookie = null,
        )
        var requestCount = 0
        var loginCookie: String? = null
        val fixture = fixture { request ->
            requestCount++
            if (request.headers[HttpHeaders.Authorization] != null) {
                loginCookie = request.headers[HttpHeaders.Cookie]
                jsonResponse(currentUserJson(secondAccount))
            } else if (requestCount == 1) {
                jsonResponse(currentUserJson(cachedAccount()))
            } else {
                jsonResponse(currentUserJson(secondAccount))
            }
        }
        fixture.accountDao.saveAccountInfo(secondAccount)
        fixture.accountDao.saveAccountInfo(cachedAccount())
        fixture.service.restoreAuth()

        assertIs<AuthState.Authed>(
            fixture.service.login(secondAccount.username, secondAccount.password.orEmpty())
        )

        assertTrue(loginCookie.orEmpty().contains("auth=second-auth"))
        assertFalse(loginCookie.orEmpty().contains("cached-auth"))
        assertFalse(loginCookie.orEmpty().contains("cached-2fa"))
        fixture.client.close()
    }

    @Test
    fun incompleteAccountLoginInvalidatesPreviousSession() = runTest {
        val secondAccount = AccountDto(
            userId = "usr_second",
            username = "second-user",
            password = "second-password",
            authCookie = "second-auth",
        )
        val fixture = fixture { request ->
            if (request.headers[HttpHeaders.Authorization] != null) {
                jsonResponse("""{"requiresTwoFactorAuth":["totp"]}""")
            } else {
                jsonResponse(currentUserJson(cachedAccount()))
            }
        }
        fixture.accountDao.saveAccountInfo(secondAccount)
        fixture.accountDao.saveAccountInfo(cachedAccount())
        fixture.service.restoreAuth()
        val firstSession = assertNotNull(SharedFlowCentre.currentSession.value)

        assertIs<AuthState.NeedTTFA>(
            fixture.service.login(secondAccount.username, secondAccount.password.orEmpty())
        )

        assertNull(SharedFlowCentre.currentSession.value)
        var requestExecuted = false
        val response = fixture.service.runSessionBoundCatching(firstSession.token) {
            requestExecuted = true
        }
        assertNull(response)
        assertFalse(requestExecuted)
        fixture.client.close()
    }

    @Test
    fun sessionBoundRequestPreservesCancellation() = runTest {
        val fixture = fixture {
            jsonResponse(currentUserJson(cachedAccount()))
        }
        fixture.service.restoreAuth()
        val session = assertNotNull(SharedFlowCentre.currentSession.value)

        assertFailsWith<CancellationException> {
            fixture.service.runSessionBoundCatching(session.token) {
                throw CancellationException("cancel request")
            }
        }
        fixture.client.close()
    }

    @Test
    fun explicitPresenceRefreshReplacesOlderSocketLocation() = runTest {
        var requestCount = 0
        val fixture = fixture {
            requestCount++
            jsonResponse(
                if (requestCount == 1) {
                    currentUserJson(cachedAccount())
                } else {
                    currentUserJson(
                        account = cachedAccount(),
                        presenceInstance = "offline",
                    )
                }
            )
        }
        fixture.service.restoreAuth()
        val session = assertNotNull(SharedFlowCentre.currentSession.value)
        fixture.service.applySocketUserLocation("wrld_old:instance", "")

        val refreshed = assertNotNull(
            fixture.service.refreshCurrentUserPresence(session.token)
        )

        assertEquals("offline", refreshed.location)
        assertEquals("offline", fixture.service.currentUserState.value?.location)
        assertEquals("offline", fixture.service.currentUser(isRefresh = true).location)
        fixture.client.close()
    }

    @Test
    fun socketLocationReceivedDuringPresenceRefreshWins() = runTest {
        var requestCount = 0
        val refreshStarted = CompletableDeferred<Unit>()
        val finishRefresh = CompletableDeferred<Unit>()
        val fixture = fixture {
            requestCount++
            if (requestCount == 1) {
                jsonResponse(currentUserJson(cachedAccount()))
            } else {
                refreshStarted.complete(Unit)
                finishRefresh.await()
                jsonResponse(
                    currentUserJson(
                        account = cachedAccount(),
                        presenceInstance = "offline",
                    )
                )
            }
        }
        fixture.service.restoreAuth()
        val session = assertNotNull(SharedFlowCentre.currentSession.value)
        fixture.service.applySocketUserLocation("wrld_old:instance", "")
        val refresh = async {
            fixture.service.refreshCurrentUserPresence(session.token)
        }
        refreshStarted.await()

        fixture.service.applySocketUserLocation("wrld_new:instance", "")
        finishRefresh.complete(Unit)

        val refreshed = assertNotNull(refresh.await())
        assertEquals("wrld_new:instance", refreshed.location)
        assertEquals("wrld_new:instance", fixture.service.currentUserState.value?.location)
        fixture.client.close()
    }

    @Test
    fun sessionBoundRequestReturnsRetryAuthenticationFailure() = runTest {
        val retryError = IllegalStateException("reauth unavailable")
        val fixture = fixture { request ->
            if (request.headers[HttpHeaders.Authorization] != null) {
                throw retryError
            }
            jsonResponse(currentUserJson(cachedAccount()))
        }
        fixture.service.restoreAuth()
        val session = assertNotNull(SharedFlowCentre.currentSession.value)
        var attempts = 0

        val response = assertNotNull(
            fixture.service.runSessionBoundCatching(session.token) {
                attempts++
                if (attempts == 1) {
                    throw VRCApiException("Unauthorized", 401, "expired")
                }
                "sent"
            }
        )

        assertEquals("reauth unavailable", response.result.exceptionOrNull()?.message)
        assertEquals(session.token, response.sessionToken)
        assertTrue(SharedFlowCentre.isCurrentSession(session.token))
        fixture.client.close()
    }

    @Test
    fun sessionBoundRequestReturnsRefreshedSessionAfterSuccessfulRetry() = runTest {
        val fixture = fixture {
            jsonResponse(currentUserJson(cachedAccount()))
        }
        fixture.service.restoreAuth()
        val firstSession = assertNotNull(SharedFlowCentre.currentSession.value)
        var attempts = 0

        val response = assertNotNull(
            fixture.service.runSessionBoundCatching(firstSession.token) {
                attempts++
                if (attempts == 1) {
                    throw VRCApiException("Unauthorized", 401, "expired")
                }
                "sent"
            }
        )

        assertEquals(2, attempts)
        assertEquals("sent", response.result.getOrThrow())
        assertEquals(firstSession.account.userId, response.sessionToken.userId)
        assertFalse(response.sessionToken == firstSession.token)
        assertTrue(SharedFlowCentre.isCurrentSession(response.sessionToken))
        fixture.client.close()
    }

    @Test
    fun expiredRealtimeSessionReauthenticatesSavedAccount() = runTest {
        val requests = mutableListOf<Pair<String?, String?>>()
        var requestCount = 0
        val fixture = fixture { request ->
            requestCount++
            requests += request.headers[HttpHeaders.Cookie] to request.headers[HttpHeaders.Authorization]
            when (requestCount) {
                2 -> respond(
                    content = currentUserJson(cachedAccount()),
                    status = HttpStatusCode.OK,
                    headers = headersOf(
                        HttpHeaders.ContentType to listOf("application/json"),
                        HttpHeaders.SetCookie to listOf("auth=new-auth; Path=/"),
                    ),
                )
                else -> jsonResponse(currentUserJson(cachedAccount()))
            }
        }
        fixture.service.restoreAuth()
        val expiredSession = assertNotNull(SharedFlowCentre.currentSession.value)

        fixture.service.recoverExpiredSession(expiredSession.token)

        val recoveredSession = assertNotNull(SharedFlowCentre.currentSession.value)
        assertEquals(expiredSession.account.userId, recoveredSession.account.userId)
        assertFalse(expiredSession.token == recoveredSession.token)
        assertFalse(requests[1].first.orEmpty().contains("auth=cached-auth"))
        assertTrue(requests[1].first.orEmpty().contains("twoFactorAuth=cached-2fa"))
        assertTrue(requests[1].second.orEmpty().startsWith("Basic "))
        assertTrue(requests[2].first.orEmpty().contains("auth=new-auth"))
        assertEquals("new-auth", fixture.accountDao.currentAccountDtoOrNull()?.authCookie)
        fixture.client.close()
    }

    @Test
    fun expiredRealtimeSessionWithoutSavedPasswordInvalidatesSession() = runTest {
        val accountWithoutPassword = cachedAccount().copy(password = null)
        var requestCount = 0
        val fixture = fixture(accountWithoutPassword) {
            requestCount++
            jsonResponse(currentUserJson(accountWithoutPassword))
        }
        fixture.service.restoreAuth()
        val expiredSession = assertNotNull(SharedFlowCentre.currentSession.value)

        fixture.service.recoverExpiredSession(expiredSession.token)

        assertNull(SharedFlowCentre.currentSession.value)
        assertNull(fixture.accountDao.currentAccountDtoOrNull()?.authCookie)
        assertNull(fixture.service.restoreAuth())
        assertEquals(1, requestCount)
        fixture.client.close()
    }

    @Test
    fun temporaryRealtimeReauthenticationFailureKeepsSessionAndStoredCookie() = runTest {
        var requestCount = 0
        val fixture = fixture { request ->
            requestCount++
            if (requestCount == 2) {
                respond(
                    content = "temporarily unavailable",
                    status = HttpStatusCode.ServiceUnavailable,
                )
            } else {
                jsonResponse(currentUserJson(cachedAccount()))
            }
        }
        fixture.service.restoreAuth()
        val session = assertNotNull(SharedFlowCentre.currentSession.value)

        assertFailsWith<VRCApiException> {
            fixture.service.recoverExpiredSession(session.token)
        }

        assertTrue(SharedFlowCentre.isCurrentSession(session.token))
        assertEquals("cached-auth", fixture.accountDao.currentAccountDtoOrNull()?.authCookie)
        assertEquals("cached-auth", fixture.cookies.cookieValue(AUTH_COOKIE))
        fixture.client.close()
    }

    @Test
    fun successfulRealtimeReauthenticationCancellationKeepsNewCookie() = runTest {
        var requestCount = 0
        val fixture = fixture { request ->
            requestCount++
            when (requestCount) {
                2 -> respond(
                    content = currentUserJson(cachedAccount()),
                    status = HttpStatusCode.OK,
                    headers = headersOf(
                        HttpHeaders.ContentType to listOf("application/json"),
                        HttpHeaders.SetCookie to listOf("auth=new-auth; Path=/"),
                    ),
                )
                else -> jsonResponse(currentUserJson(cachedAccount()))
            }
        }
        fixture.service.restoreAuth()
        val previousSession = assertNotNull(SharedFlowCentre.currentSession.value)
        val authenticated = CompletableDeferred<Unit>()
        lateinit var recoveryJob: Job
        val cancellationCollector = launch(start = CoroutineStart.UNDISPATCHED) {
            SharedFlowCentre.authed.collect {
                if (!authenticated.isCompleted) {
                    authenticated.complete(Unit)
                    recoveryJob.cancel()
                }
            }
        }

        recoveryJob = launch(start = CoroutineStart.UNDISPATCHED) {
            fixture.service.recoverExpiredSession(previousSession.token)
        }
        authenticated.await()
        recoveryJob.join()

        val currentSession = assertNotNull(SharedFlowCentre.currentSession.value)
        assertFalse(currentSession.token == previousSession.token)
        assertEquals("new-auth", fixture.cookies.cookieValue(AUTH_COOKIE))
        assertEquals("new-auth", fixture.accountDao.currentAccountDtoOrNull()?.authCookie)

        cancellationCollector.cancelAndJoin()
        fixture.client.close()
    }

    @Test
    fun failedAccountSwitchInvalidatesPreviousSession() = runTest {
        val secondAccount = AccountDto(
            userId = "usr_second",
            username = "second-user",
            password = "second-password",
            authCookie = "second-auth",
        )
        val fixture = fixture { request ->
            if (request.headers[HttpHeaders.Authorization] != null) {
                throw IllegalStateException("login unavailable")
            }
            jsonResponse(currentUserJson(cachedAccount()))
        }
        fixture.accountDao.saveAccountInfo(secondAccount)
        fixture.accountDao.saveAccountInfo(cachedAccount())
        fixture.service.restoreAuth()

        assertFailsWith<IllegalStateException> {
            fixture.service.login(secondAccount.username, secondAccount.password.orEmpty())
        }

        assertNull(SharedFlowCentre.currentSession.value)
        fixture.client.close()
    }

    @Test
    fun networkAvatarSelectionResponseUpdatesCurrentUserState() = runTest {
        var requestCount = 0
        val fixture = fixture {
            requestCount++
            if (requestCount == 1) {
                jsonResponse(currentUserJson(cachedAccount()))
            } else {
                jsonResponse("""{"currentAvatar":"avtr_selected"}""")
            }
        }
        fixture.service.restoreAuth()
        val result = NetworkAvatarSelector(
            avatarsApi = AvatarsApi(fixture.client),
            authService = fixture.service,
            logger = EmptyLogger(),
        ).select("avtr_selected")

        assertTrue(result.isSuccess)
        assertEquals(2, requestCount)
        assertEquals(
            "avtr_selected",
            fixture.service.currentUserState.value?.currentAvatar,
        )
        fixture.client.close()
    }

    @Test
    fun networkAvatarSelectionFailureLogsResponseDetailsWithoutCredentials() = runTest {
        var requestCount = 0
        val responseBody = """{"error":{"message":"Avatar not available","status_code":403}}"""
        val fixture = fixture {
            requestCount++
            if (requestCount == 1) {
                jsonResponse(currentUserJson(cachedAccount()))
            } else {
                respond(
                    content = responseBody,
                    status = HttpStatusCode.Forbidden,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            }
        }
        fixture.service.restoreAuth()
        val logger = CapturingLogger()
        val application = koinApplication {
            modules(
                module {
                    single { AvatarsApi(fixture.client) }
                    single { fixture.service }
                    single<Logger> { logger }
                    singleOf(::NetworkAvatarSelector)
                }
            )
        }

        val result = application.koin.get<NetworkAvatarSelector>().select("avtr_blocked")

        assertTrue(result.isFailure)
        val (level, message) = logger.entries.single()
        assertEquals(Level.ERROR, level)
        assertTrue(message.contains("method=PUT"))
        assertTrue(message.contains("path=/avatars/avtr_blocked/select"))
        assertTrue(message.contains("status=403"))
        assertTrue(message.contains("description=Forbidden"))
        assertTrue(message.contains("body=$responseBody"))
        assertFalse(message.contains("cached-auth"))
        assertFalse(message.contains(HttpHeaders.Authorization))
        assertFalse(message.contains(HttpHeaders.Cookie))
        application.close()
        fixture.client.close()
    }

    private data class Fixture(
        val service: AuthService,
        val accountDao: AccountDao,
        val cookies: PersistentCookiesStorage,
        val client: HttpClient,
    )

    private fun fixture(
        account: AccountDto = cachedAccount(),
        handler: MockRequestHandler,
    ): Fixture {
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
                friendActivityStore = io.github.vrcmteam.vrcm.storage.NoOpFriendActivityCacheStore,
                meetupCardConfigDao = MeetupCardConfigDao(MapSettings()),
                meetupCardAssetStore = MeetupCardAssetStore(
                    FakeFileSystem(),
                    "/meetup-assets".toPath(),
                ),
            ),
        )
        return Fixture(service, accountDao, cookies, client)
    }

    private fun cachedAccount() = AccountDto(
        userId = "usr_cached",
        username = "cached-user",
        password = "cached-password",
        current = true,
        authCookie = "cached-auth",
        twoFactorAuthCookie = "cached-2fa",
    )

    private fun MockRequestHandleScope.jsonResponse(body: String) = respond(
        content = body,
        status = HttpStatusCode.OK,
        headers = headersOf(HttpHeaders.ContentType, "application/json"),
    )

    private fun currentUserJson(
        account: AccountDto,
        currentAvatar: String = "",
        presenceWorld: String = "",
        presenceInstance: String = "",
        homeLocation: String = "",
    ): String = """
        {
          "requiresTwoFactorAuth":null,
          "ageVerificationStatus":"verified","ageVerified":true,
          "acceptedPrivacyVersion":0,"acceptedTOSVersion":0,
          "accountDeletionDate":null,"accountDeletionLog":null,"activeFriends":[],
          "allowAvatarCopying":true,"bio":null,"bioLinks":[],
          "currentAvatar":"$currentAvatar","currentAvatarAssetUrl":null,"currentAvatarImageUrl":"",
          "currentAvatarTags":[],"currentAvatarThumbnailImageUrl":"","date_joined":"",
          "developerType":"none","displayName":"${account.username}","emailVerified":true,
          "fallbackAvatar":"","friendGroupNames":[],"friendKey":"","friends":[],
          "googleId":"","hasBirthday":true,"hasEmail":true,
          "hasLoggedInFromClient":true,"hasPendingEmail":false,
          "hideContentFilterSettings":false,"homeLocation":"$homeLocation","id":"${account.userId}",
          "isFriend":false,"last_activity":"","last_login":"",
          "last_platform":"standalonewindows","obfuscatedEmail":"",
          "obfuscatedPendingEmail":"","oculusId":"","offlineFriends":[],
          "onlineFriends":[],"pastDisplayNames":[],"picoId":"",
          "presence":{
            "avatarThumbnail":null,"displayName":"${account.username}","groups":[],
            "id":"${account.userId}","instance":"$presenceInstance","instanceType":"",
            "isRejoining":null,"platform":"standalonewindows","profilePicOverride":null,
            "status":"active","travelingToInstance":"","travelingToWorld":"","world":"$presenceWorld"
          },
          "profilePicOverride":"","state":"online","status":"active",
          "statusDescription":"","statusFirstTime":false,"statusHistory":[],
          "steamDetails":{},"steamId":"","tags":[],"twoFactorAuthEnabled":false,
          "twoFactorAuthEnabledDate":null,"unsubscribe":false,"updated_at":"",
          "userIcon":"","userLanguage":null,"userLanguageCode":null,
          "username":"${account.username}","viveId":"","pronouns":null
        }
    """.trimIndent()
}

private class CapturingLogger : Logger(Level.DEBUG) {
    val entries = mutableListOf<Pair<Level, String>>()

    override fun display(level: Level, msg: String) {
        entries += level to msg
    }
}
