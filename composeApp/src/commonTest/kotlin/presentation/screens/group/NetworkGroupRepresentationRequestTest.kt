package io.github.vrcmteam.vrcm.presentation.screens.group

import com.russhwolf.settings.MapSettings
import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.di.supports.PersistentCookiesStorage
import io.github.vrcmteam.vrcm.network.api.auth.AuthApi
import io.github.vrcmteam.vrcm.network.api.groups.GroupsApi
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
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import org.koin.core.logger.EmptyLogger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class NetworkGroupRepresentationRequestTest : MainDispatcherTest() {
    @Test
    fun updateIsFollowedByAnAuthoritativeRefreshInTheBoundSession() = runBlocking {
        val account = AccountDto(userId = REPRESENTATION_USER_ID, username = "representation-test")
        SharedFlowCentre.emitAuthenticated(account)
        val sessionToken = checkNotNull(SharedFlowCentre.currentSession.value).token
        val observedRequests = mutableListOf<ObservedRequest>()
        val client = HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    observedRequests += ObservedRequest(
                        method = request.method,
                        path = request.url.encodedPath,
                        includeRoles = request.url.parameters["includeRoles"],
                    )
                    when (request.method) {
                        HttpMethod.Put -> respondJson(
                            """{"success":{"message":"updated","status_code":200}}"""
                        )

                        HttpMethod.Get -> respondJson(representationGroupJson(isRepresenting = true))
                        else -> error("Unexpected request: ${request.method} ${request.url}")
                    }
                }
            }
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
        try {
            val request = NetworkGroupRepresentationRequest(
                groupsApi = GroupsApi(client),
                authService = createRepresentationAuthService(client, account),
            )

            val response = checkNotNull(
                request.update(sessionToken, REPRESENTATION_GROUP_ID, isRepresenting = true)
            )

            assertEquals(
                listOf(
                    ObservedRequest(
                        HttpMethod.Put,
                        "/groups/$REPRESENTATION_GROUP_ID/representation",
                        null,
                    ),
                    ObservedRequest(HttpMethod.Get, "/groups/$REPRESENTATION_GROUP_ID", "true"),
                ),
                observedRequests,
            )
            assertEquals(sessionToken, response.sessionToken)
            assertTrue(response.result.getOrThrow().myMember!!.isRepresenting)
        } finally {
            client.close()
            SharedFlowCentre.emitLogout()
        }
    }

    private data class ObservedRequest(
        val method: HttpMethod,
        val path: String,
        val includeRoles: String?,
    )

}

internal fun createRepresentationAuthService(
    client: HttpClient,
    account: AccountDto,
): AuthService {
    val accountDao = AccountDao(MapSettings(), InMemorySecureStorage()).also {
        it.saveAccountInfo(account)
    }
    return AuthService(
        authApi = AuthApi(client),
        accountDao = accountDao,
        cookiesStorage = PersistentCookiesStorage(EmptyLogger()),
        accountCacheManager = AccountCacheManager(
            friendListCacheStore = InMemoryFriendListCacheStore(),
            userProfileCacheStore = InMemoryUserProfileCacheStore(),
            friendActivityStore = io.github.vrcmteam.vrcm.storage.NoOpFriendActivityCacheStore,
            meetupCardConfigDao = MeetupCardConfigDao(MapSettings()),
            meetupCardAssetStore = MeetupCardAssetStore(
                FakeFileSystem(),
                "/assets".toPath(),
            ),
        ),
    )
}

internal fun io.ktor.client.engine.mock.MockRequestHandleScope.respondJson(content: String) =
    respond(
        content = content,
        status = HttpStatusCode.OK,
        headers = headersOf(HttpHeaders.ContentType, "application/json"),
    )

internal fun representationGroupJson(isRepresenting: Boolean) = """
    {
      "id":"$REPRESENTATION_GROUP_ID",
      "membershipStatus":"member",
      "myMember":{
        "groupId":"$REPRESENTATION_GROUP_ID",
        "has2FA":true,
        "id":"gmem_1",
        "isRepresenting":$isRepresenting,
        "isSubscribedToAnnouncements":false,
        "joinedAt":"2026-01-01T00:00:00.000Z",
        "lastPostReadAt":null,
        "mRoleIds":[],
        "membershipStatus":"member",
        "permissions":[],
        "roleIds":[],
        "userId":"$REPRESENTATION_USER_ID",
        "visibility":"visible"
      }
    }
""".trimIndent()

internal const val REPRESENTATION_GROUP_ID = "grp_00000000-0000-0000-0000-000000000001"
internal const val REPRESENTATION_USER_ID = "usr_00000000-0000-0000-0000-000000000001"
