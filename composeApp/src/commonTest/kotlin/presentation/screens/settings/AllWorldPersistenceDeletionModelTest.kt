package io.github.vrcmteam.vrcm.presentation.screens.settings

import androidx.lifecycle.ViewModelStore
import com.russhwolf.settings.MapSettings
import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.di.supports.PersistentCookiesStorage
import io.github.vrcmteam.vrcm.network.api.auth.AuthApi
import io.github.vrcmteam.vrcm.network.api.users.UsersApi
import io.github.vrcmteam.vrcm.service.AuthService
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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import kotlinx.serialization.json.Json
import org.koin.core.logger.EmptyLogger
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals

class AllWorldPersistenceDeletionModelTest : MainDispatcherTest() {
    private val models = mutableListOf<AllWorldPersistenceDeletionModel>()
    private val clients = mutableListOf<HttpClient>()

    @BeforeTest
    fun clearSession() = runBlocking {
        SharedFlowCentre.emitLogout()
    }

    @AfterTest
    fun cleanUp() = runBlocking {
        models.forEachIndexed { index, model ->
            ViewModelStore().apply {
                put("deletion-$index", model)
                clear()
            }
        }
        clients.forEach(HttpClient::close)
        SharedFlowCentre.emitLogout()
    }

    @Test
    fun repeatedSubmissionSendsOnlyOneRequestUntilCompletion() = runBlocking {
        val requestStarted = CompletableDeferred<Unit>()
        val releaseRequest = CompletableDeferred<Unit>()
        var deleteCount = 0
        val fixture = fixture {
            deleteCount++
            requestStarted.complete(Unit)
            releaseRequest.await()
            respond("", HttpStatusCode.OK)
        }
        SharedFlowCentre.emitAuthenticated(primaryAccount)
        val model = fixture.model()

        model.deleteAllWorldSaveData()
        requestStarted.await()
        model.deleteAllWorldSaveData()

        assertEquals(1, deleteCount)
        assertIs<AllWorldPersistenceDeletionState.Deleting>(model.state.value)

        releaseRequest.complete(Unit)
        awaitUntil { model.state.value is AllWorldPersistenceDeletionState.Deleted }
        assertEquals(1, deleteCount)
    }

    @Test
    fun failedSubmissionCanBeRetried() = runBlocking {
        var deleteCount = 0
        val fixture = fixture {
            deleteCount++
            if (deleteCount == 1) {
                respond("failed", HttpStatusCode.InternalServerError)
            } else {
                respond("", HttpStatusCode.OK)
            }
        }
        SharedFlowCentre.emitAuthenticated(primaryAccount)
        val model = fixture.model()

        model.deleteAllWorldSaveData()
        awaitUntil { model.state.value is AllWorldPersistenceDeletionState.Failed }

        model.deleteAllWorldSaveData()
        awaitUntil { model.state.value is AllWorldPersistenceDeletionState.Deleted }
        assertEquals(2, deleteCount)
    }

    @Test
    fun accountSwitchInvalidatesOldResultAndAllowsCurrentAccountRequest() = runBlocking {
        val oldRequestStarted = CompletableDeferred<Unit>()
        val releaseOldRequest = CompletableDeferred<Unit>()
        val currentRequestStarted = CompletableDeferred<Unit>()
        val releaseCurrentRequest = CompletableDeferred<Unit>()
        val requestedUsers = mutableListOf<String>()
        val fixture = fixture { request ->
            val userId = request.url.segments.dropLast(1).last()
            requestedUsers += userId
            when (userId) {
                primaryAccount.userId -> {
                    oldRequestStarted.complete(Unit)
                    releaseOldRequest.await()
                }
                secondaryAccount.userId -> {
                    currentRequestStarted.complete(Unit)
                    releaseCurrentRequest.await()
                }
            }
            respond("", HttpStatusCode.OK)
        }
        SharedFlowCentre.emitAuthenticated(primaryAccount)
        val model = fixture.model()

        model.deleteAllWorldSaveData()
        oldRequestStarted.await()
        SharedFlowCentre.emitAuthenticated(secondaryAccount)
        awaitUntil { model.state.value is AllWorldPersistenceDeletionState.Ready }
        model.deleteAllWorldSaveData()
        releaseOldRequest.complete(Unit)
        currentRequestStarted.await()

        assertIs<AllWorldPersistenceDeletionState.Deleting>(model.state.value)

        releaseCurrentRequest.complete(Unit)
        awaitUntil { model.state.value is AllWorldPersistenceDeletionState.Deleted }
        assertEquals(listOf(primaryAccount.userId, secondaryAccount.userId), requestedUsers)
    }

    @Test
    fun logoutInvalidatesOldResultAndDisablesSubmission() = runBlocking {
        val requestStarted = CompletableDeferred<Unit>()
        val releaseRequest = CompletableDeferred<Unit>()
        var deleteCount = 0
        val fixture = fixture {
            deleteCount++
            requestStarted.complete(Unit)
            releaseRequest.await()
            respond("", HttpStatusCode.OK)
        }
        SharedFlowCentre.emitAuthenticated(primaryAccount)
        val model = fixture.model()

        model.deleteAllWorldSaveData()
        requestStarted.await()
        SharedFlowCentre.emitLogout()
        awaitUntil { model.state.value is AllWorldPersistenceDeletionState.Unavailable }
        model.deleteAllWorldSaveData()
        releaseRequest.complete(Unit)

        assertIs<AllWorldPersistenceDeletionState.Unavailable>(model.state.value)
        assertEquals(1, deleteCount)
    }

    @Test
    fun externalTokenReplacementRejectsOldResponse() = runBlocking {
        val firstRequestStarted = CompletableDeferred<Unit>()
        val releaseFirstRequest = CompletableDeferred<Unit>()
        var deleteCount = 0
        val fixture = fixture {
            deleteCount++
            if (deleteCount == 1) {
                firstRequestStarted.complete(Unit)
                releaseFirstRequest.await()
            }
            respond("", HttpStatusCode.OK)
        }
        SharedFlowCentre.emitAuthenticated(primaryAccount)
        val model = fixture.model()

        model.deleteAllWorldSaveData()
        firstRequestStarted.await()
        SharedFlowCentre.emitAuthenticated(primaryAccount)
        releaseFirstRequest.complete(Unit)
        awaitUntil { model.state.value is AllWorldPersistenceDeletionState.Ready }

        model.deleteAllWorldSaveData()
        awaitUntil { model.state.value is AllWorldPersistenceDeletionState.Deleted }
        assertEquals(2, deleteCount)
    }

    @Test
    fun successfulDeletionRemainsTerminalAfterSameAccountTokenReplacement() = runBlocking {
        var deleteCount = 0
        val fixture = fixture {
            deleteCount++
            respond("", HttpStatusCode.OK)
        }
        SharedFlowCentre.emitAuthenticated(primaryAccount)
        val model = fixture.model()

        model.deleteAllWorldSaveData()
        awaitUntil { model.state.value is AllWorldPersistenceDeletionState.Deleted }
        val deletionToken = SharedFlowCentre.currentSession.value?.token
        SharedFlowCentre.emitAuthenticated(primaryAccount)
        val replacementToken = SharedFlowCentre.currentSession.value?.token
        assertNotEquals(deletionToken, replacementToken)
        yield()
        assertIs<AllWorldPersistenceDeletionState.Deleted>(model.state.value)

        model.deleteAllWorldSaveData()

        assertIs<AllWorldPersistenceDeletionState.Deleted>(model.state.value)
        assertEquals(1, deleteCount)
    }

    @Test
    fun unauthorizedRequestCommitsWithExactRefreshedToken() = runBlocking {
        var deleteCount = 0
        var authCount = 0
        val fixture = fixture { request ->
            when {
                request.method == HttpMethod.Delete -> {
                    deleteCount++
                    if (deleteCount == 1) {
                        respond("expired", HttpStatusCode.Unauthorized)
                    } else {
                        respond("", HttpStatusCode.OK)
                    }
                }
                request.url.encodedPath == "/api/1/auth/user" -> {
                    authCount++
                    jsonResponse(currentUserJson(primaryAccount))
                }
                else -> error("Unexpected request: ${request.method} ${request.url.encodedPath}")
            }
        }
        SharedFlowCentre.emitAuthenticated(primaryAccount)
        val initialToken = SharedFlowCentre.currentSession.value?.token
        val model = fixture.model()

        model.deleteAllWorldSaveData()
        awaitUntil { model.state.value is AllWorldPersistenceDeletionState.Deleted }

        assertEquals(2, deleteCount)
        assertEquals(2, authCount)
        val refreshedToken = SharedFlowCentre.currentSession.value?.token
        assertEquals(primaryAccount.userId, refreshedToken?.userId)
        assertNotEquals(initialToken, refreshedToken)
    }

    private fun fixture(handler: MockRequestHandler): Fixture {
        val cookies = PersistentCookiesStorage(EmptyLogger())
        val client = HttpClient(MockEngine) {
            engine { addHandler(handler) }
            defaultRequest { url("https://api.vrchat.cloud/api/1/") }
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            install(HttpCookies) { storage = cookies }
        }.also(clients::add)
        val accountDao = AccountDao(MapSettings(), InMemorySecureStorage()).also {
            it.saveAccountInfo(primaryAccount)
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
        return Fixture(client, authService)
    }

    private inner class Fixture(
        val client: HttpClient,
        val authService: AuthService,
    ) {
        fun model() = AllWorldPersistenceDeletionModel(
            authService = authService,
            usersApi = UsersApi(client),
            requestDispatcher = Dispatchers.Unconfined,
        ).also(models::add)
    }

    private companion object {
        val primaryAccount = AccountDto(
            userId = "usr_primary",
            username = "primary-user",
            password = "primary-password",
            current = true,
            authCookie = "primary-auth",
            twoFactorAuthCookie = "primary-2fa",
        )
        val secondaryAccount = AccountDto(
            userId = "usr_secondary",
            username = "secondary-user",
            password = "secondary-password",
        )
    }
}

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
        "id":"${account.userId}","instance":"offline","instanceType":"",
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

private suspend fun awaitUntil(predicate: () -> Boolean) {
    withTimeout(2_000) {
        while (!predicate()) yield()
    }
}
