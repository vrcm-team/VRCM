package io.github.vrcmteam.vrcm.presentation.screens.home.pager

import com.russhwolf.settings.MapSettings
import io.github.vrcmteam.vrcm.core.shared.AccountWebSocketEvent
import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.di.supports.PersistentCookiesStorage
import io.github.vrcmteam.vrcm.network.api.attributes.LocationType
import io.github.vrcmteam.vrcm.network.api.attributes.UserStatus
import io.github.vrcmteam.vrcm.network.api.auth.AuthApi
import io.github.vrcmteam.vrcm.network.api.friends.FriendsApi
import io.github.vrcmteam.vrcm.network.api.groups.GroupsApi
import io.github.vrcmteam.vrcm.network.api.instances.InstancesApi
import io.github.vrcmteam.vrcm.network.api.users.UsersApi
import io.github.vrcmteam.vrcm.network.websocket.data.WebSocketEvent
import io.github.vrcmteam.vrcm.network.websocket.data.content.FriendActiveContent
import io.github.vrcmteam.vrcm.network.websocket.data.content.UserContent
import io.github.vrcmteam.vrcm.network.websocket.data.type.FriendEvents
import io.github.vrcmteam.vrcm.service.AuthService
import io.github.vrcmteam.vrcm.service.FriendService
import io.github.vrcmteam.vrcm.service.data.AccountDto
import io.github.vrcmteam.vrcm.storage.AccountCacheManager
import io.github.vrcmteam.vrcm.storage.AccountDao
import io.github.vrcmteam.vrcm.storage.InMemorySecureStorage
import io.github.vrcmteam.vrcm.storage.FriendListCacheDao
import io.github.vrcmteam.vrcm.storage.UserProfileCacheDao
import io.github.vrcmteam.vrcm.storage.meetup.MeetupCardAssetStore
import io.github.vrcmteam.vrcm.storage.meetup.MeetupCardConfigDao
import io.github.vrcmteam.vrcm.testing.MainDispatcherTest
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.koin.core.logger.EmptyLogger
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@OptIn(ExperimentalCoroutinesApi::class)
class LateSessionConsumerIntegrationTest : MainDispatcherTest() {
    @Test
    fun currentSessionEventReachesConsumersCreatedAfterAuthentication() = runBlocking {
        SharedFlowCentre.emitLogout()
        val account = AccountDto(
            userId = "usr_late_consumer",
            username = "late-consumer",
        )
        SharedFlowCentre.emitAuthenticated(account)
        val session = assertNotNull(SharedFlowCentre.currentSession.value)

        val json = Json { ignoreUnknownKeys = true }
        val client = testClient(json)
        val accountDao = AccountDao(MapSettings(), InMemorySecureStorage()).also { it.saveAccountInfo(account) }
        val friendListCacheDao = FriendListCacheDao(MapSettings())
        val cacheManager = AccountCacheManager(
            friendListCacheDao = friendListCacheDao,
            userProfileCacheDao = UserProfileCacheDao(MapSettings()),
            friendActivityStore = io.github.vrcmteam.vrcm.storage.NoOpFriendActivityCacheStore,
            meetupCardConfigDao = MeetupCardConfigDao(MapSettings()),
            meetupCardAssetStore = MeetupCardAssetStore(
                FakeFileSystem(),
                "/meetup-assets".toPath(),
            ),
        )
        val authService = AuthService(
            authApi = AuthApi(client),
            accountDao = accountDao,
            cookiesStorage = PersistentCookiesStorage(EmptyLogger()),
            accountCacheManager = cacheManager,
        )
        val friendService = FriendService(
            friendsApi = FriendsApi(client),
            authService = authService,
            json = json,
            friendListCacheDao = friendListCacheDao,
            accountCacheManager = cacheManager,
        )
        val locationModel = FriendLocationPagerModel(
            friendService = friendService,
            usersApi = UsersApi(client),
            groupsApi = GroupsApi(client),
            instancesApi = InstancesApi(client),
            authService = authService,
        )

        try {
            val friendId = "usr_realtime_friend"
            val event = activeFriendEvent(json, friendId)
            withTimeout(3_000) {
                while (
                    friendId !in friendService.friendState.value ||
                    friendId !in locationModel.friendLocationsByUser.value
                ) {
                    SharedFlowCentre.emitWebSocket(AccountWebSocketEvent(session.token, event))
                    yield()
                }
            }

            assertEquals(friendId, friendService.friendState.value.getValue(friendId).id)
            assertEquals(
                LocationType.Web.value,
                locationModel.friendLocationsByUser.value.getValue(friendId).location,
            )
        } finally {
            locationModel.close()
            friendService.dispose()
            SharedFlowCentre.emitLogout()
            client.close()
        }
    }

    private fun testClient(json: Json) = HttpClient(MockEngine) {
        engine {
            addHandler { request ->
                when (request.url.encodedPath) {
                    "/auth/user/friends" -> respond(
                        content = "[]",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                    "/auth/user" -> respond(
                        content = "unavailable",
                        status = HttpStatusCode.InternalServerError,
                    )
                    else -> error("Unexpected request: ${request.url}")
                }
            }
        }
        install(ContentNegotiation) { json(json) }
    }

    private fun activeFriendEvent(json: Json, friendId: String) = WebSocketEvent(
        type = FriendEvents.FriendActive.typeName,
        content = json.encodeToString(
            FriendActiveContent(
                userId = friendId,
                user = UserContent(
                    allowAvatarCopying = true,
                    bio = null,
                    bioLinks = emptyList(),
                    currentAvatarImageUrl = "",
                    currentAvatarTags = emptyList(),
                    currentAvatarThumbnailImageUrl = "",
                    dateJoined = "",
                    developerType = "none",
                    displayName = "Realtime Friend",
                    friendKey = "",
                    id = friendId,
                    isFriend = true,
                    lastActivity = "",
                    lastLogin = "",
                    lastPlatform = "web",
                    profilePicOverride = "",
                    state = "active",
                    status = UserStatus.Active,
                    statusDescription = "",
                    tags = emptyList(),
                    userIcon = "",
                    pronouns = null,
                ),
            )
        ),
    )
}
