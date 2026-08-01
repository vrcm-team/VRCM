package io.github.vrcmteam.vrcm.service

import com.russhwolf.settings.MapSettings
import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.di.supports.PersistentCookiesStorage
import io.github.vrcmteam.vrcm.network.api.attributes.AuthState
import io.github.vrcmteam.vrcm.network.api.auth.AuthApi
import io.github.vrcmteam.vrcm.service.data.AccountDto
import io.github.vrcmteam.vrcm.storage.AccountCacheManager
import io.github.vrcmteam.vrcm.storage.AccountDao
import io.github.vrcmteam.vrcm.storage.FriendListCacheDao
import io.github.vrcmteam.vrcm.storage.UserProfileCacheDao
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.koin.core.logger.EmptyLogger
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AuthServiceTest {
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
        assertEquals(
            "cached-2fa",
            fixture.accountDao.currentAccountDtoOrNull()?.twoFactorAuthCookie,
        )
        fixture.client.close()
    }

    private data class Fixture(
        val service: AuthService,
        val accountDao: AccountDao,
        val client: HttpClient,
    )

    private fun fixture(handler: MockRequestHandler): Fixture {
        val cookies = PersistentCookiesStorage(EmptyLogger())
        val client = HttpClient(MockEngine) {
            engine { addHandler(handler) }
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            install(HttpCookies) { storage = cookies }
        }
        val accountDao = AccountDao(MapSettings()).also { it.saveAccountInfo(cachedAccount()) }
        val service = AuthService(
            authApi = AuthApi(client),
            accountDao = accountDao,
            cookiesStorage = cookies,
            accountCacheManager = AccountCacheManager(
                friendListCacheDao = FriendListCacheDao(MapSettings()),
                userProfileCacheDao = UserProfileCacheDao(MapSettings()),
            ),
        )
        return Fixture(service, accountDao, client)
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
}
