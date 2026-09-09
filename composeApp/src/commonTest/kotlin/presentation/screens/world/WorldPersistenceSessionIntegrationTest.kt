package io.github.vrcmteam.vrcm.presentation.screens.world

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
import io.github.vrcmteam.vrcm.storage.NoOpFriendActivityCacheStore
import io.github.vrcmteam.vrcm.storage.meetup.MeetupCardAssetStore
import io.github.vrcmteam.vrcm.storage.meetup.MeetupCardConfigDao
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.plugins.defaultRequest
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import org.koin.core.logger.EmptyLogger
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class WorldPersistenceSessionIntegrationTest {
    @AfterTest
    fun clearSession() = runBlocking { SharedFlowCentre.emitLogout() }

    @Test
    fun unauthorizedCheckReauthenticatesAndCommitsWithRefreshedToken() = runBlocking {
        SharedFlowCentre.emitLogout()
        val account = savedAccount()
        var existenceAttempts = 0
        var authAttempts = 0
        val cookies = PersistentCookiesStorage(EmptyLogger())
        val client = HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    when (request.url.encodedPath) {
                        "/api/1/auth/user" -> {
                            authAttempts++
                            currentUserResponse(account)
                        }
                        "/api/1/users/${account.userId}/wrld_target/persist/exists" -> {
                            existenceAttempts++
                            if (existenceAttempts == 1) {
                                respond("expired", HttpStatusCode.Unauthorized)
                            } else {
                                respond("", HttpStatusCode.OK)
                            }
                        }
                        else -> error("Unexpected request: ${request.url.encodedPath}")
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
            accountCacheManager = testAccountCacheManager(),
        )
        val requestScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        try {
            authService.restoreAuth()
            val firstSession = assertNotNull(SharedFlowCentre.currentSession.value)
            val model = WorldPersistenceStateModel(
                request = NetworkWorldPersistenceRequest(authService, UsersApi(client)),
                scope = requestScope,
                requestDispatcher = Dispatchers.Default,
            )
            model.bindWorld("wrld_target")

            model.check()
            val finalState = withTimeout(5_000) {
                model.state.first { it.status != WorldPersistenceStatus.Checking }
            }

            val refreshedSession = assertNotNull(SharedFlowCentre.currentSession.value)
            assertEquals(
                2,
                existenceAttempts,
                "authAttempts=$authAttempts state=${model.state.value}",
            )
            assertEquals(firstSession.account.userId, refreshedSession.account.userId)
            assertFalse(firstSession.token == refreshedSession.token)
            assertTrue(SharedFlowCentre.isCurrentSession(refreshedSession.token))
            assertEquals(WorldPersistenceStatus.Exists, finalState.status)
        } finally {
            requestScope.cancel()
            client.close()
            SharedFlowCentre.emitLogout()
        }
    }
}

private fun testAccountCacheManager() = AccountCacheManager(
    friendListCacheStore = InMemoryFriendListCacheStore(),
    userProfileCacheStore = InMemoryUserProfileCacheStore(),
    friendActivityStore = NoOpFriendActivityCacheStore,
    meetupCardConfigDao = MeetupCardConfigDao(MapSettings()),
    meetupCardAssetStore = MeetupCardAssetStore(
        FakeFileSystem(),
        "/meetup-assets".toPath(),
    ),
)

private fun savedAccount() = AccountDto(
    userId = "usr_saved",
    username = "saved-user",
    password = "saved-password",
    current = true,
    authCookie = "saved-auth",
    twoFactorAuthCookie = "saved-2fa",
)

private fun MockRequestHandleScope.currentUserResponse(account: AccountDto) = respond(
    content = """
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
          "isFriend":false,"last_activity":"","last_login":"","last_platform":"standalonewindows",
          "obfuscatedEmail":"","obfuscatedPendingEmail":"","oculusId":"",
          "offlineFriends":[],"onlineFriends":[],"pastDisplayNames":[],"picoId":"",
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
    """.trimIndent(),
    status = HttpStatusCode.OK,
    headers = headersOf(HttpHeaders.ContentType, "application/json"),
)
