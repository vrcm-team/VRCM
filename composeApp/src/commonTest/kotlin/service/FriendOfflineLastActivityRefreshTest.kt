package io.github.vrcmteam.vrcm.service

import com.russhwolf.settings.MapSettings
import io.github.vrcmteam.vrcm.core.shared.AccountWebSocketEvent
import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.di.supports.PersistentCookiesStorage
import io.github.vrcmteam.vrcm.network.api.attributes.LocationType
import io.github.vrcmteam.vrcm.network.api.attributes.UserStatus
import io.github.vrcmteam.vrcm.network.api.auth.AuthApi
import io.github.vrcmteam.vrcm.network.api.friends.FriendsApi
import io.github.vrcmteam.vrcm.network.api.friends.date.FriendData
import io.github.vrcmteam.vrcm.network.websocket.data.WebSocketEvent
import io.github.vrcmteam.vrcm.network.websocket.data.content.FriendOfflineContent
import io.github.vrcmteam.vrcm.network.websocket.data.type.FriendEvents
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
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.atomicfu.atomic
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
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class FriendOfflineLastActivityRefreshTest : MainDispatcherTest() {
    @Test
    fun offlineSocketRefreshesLastActivityFromOfflineFriendsEndpoint() = runBlocking {
        SharedFlowCentre.emitLogout()
        val account = AccountDto(userId = "usr_owner", username = "owner")
        SharedFlowCentre.emitAuthenticated(account)
        val session = assertNotNull(SharedFlowCentre.currentSession.value)
        val friend = friend()
        val json = Json { ignoreUnknownKeys = true }
        val serveUpdatedOfflineFriend = atomic(false)
        val offlineRequestCount = atomic(0)
        val client = HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    when (request.url.encodedPath) {
                        "/auth/user/friends" -> {
                            val offline = request.url.parameters["offline"] == "true"
                            val offset = request.url.parameters["offset"]?.toIntOrNull() ?: 0
                            if (offline) offlineRequestCount.incrementAndGet()
                            val friends = when {
                                offset > 0 -> emptyList()
                                !offline -> listOf(friend)
                                serveUpdatedOfflineFriend.value -> listOf(
                                    friend.copy(
                                        lastActivity = UPDATED_LAST_ACTIVITY,
                                        location = LocationType.Offline.value,
                                        status = UserStatus.Offline,
                                    )
                                )
                                else -> emptyList()
                            }
                            respond(
                                content = json.encodeToString(friends),
                                status = HttpStatusCode.OK,
                                headers = headersOf(HttpHeaders.ContentType, "application/json"),
                            )
                        }
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
        val accountDao = AccountDao(MapSettings(), InMemorySecureStorage()).also {
            it.saveAccountInfo(account)
        }
        val friendListCacheStore = InMemoryFriendListCacheStore()
        val cacheManager = AccountCacheManager(
            friendListCacheStore = friendListCacheStore,
            userProfileCacheStore = InMemoryUserProfileCacheStore(),
            friendActivityStore = NoOpFriendActivityCacheStore,
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
            friendListCacheStore = friendListCacheStore,
            accountCacheManager = cacheManager,
        )

        try {
            withTimeout(3_000L) {
                while (!friendService.initialRefreshCompleted.value) yield()
            }
            assertEquals(UserStatus.Active, friendService.friendState.value.getValue(friend.id).status)
            val initialOfflineRequestCount = offlineRequestCount.value
            serveUpdatedOfflineFriend.value = true
            val offlineEvent = AccountWebSocketEvent(
                token = session.token,
                event = WebSocketEvent(
                    type = FriendEvents.FriendOffline.typeName,
                    content = json.encodeToString(FriendOfflineContent(userId = friend.id)),
                ),
            )

            SharedFlowCentre.emitWebSocket(offlineEvent)
            withTimeout(3_000L) {
                while (friendService.friendLastActivitySource.value
                        ?.friends
                        ?.firstOrNull { it.id == friend.id }
                        ?.lastActivity != UPDATED_LAST_ACTIVITY
                ) {
                    yield()
                }
            }

            assertTrue(offlineRequestCount.value > initialOfflineRequestCount)
            assertEquals(
                UserStatus.Offline,
                friendService.friendLastActivitySource.value
                    ?.friends
                    ?.first { it.id == friend.id }
                    ?.status,
            )
        } finally {
            friendService.dispose()
            SharedFlowCentre.emitLogout()
            client.close()
        }
    }

    private fun friend() = FriendData(
        bio = null,
        currentAvatarImageUrl = "",
        currentAvatarThumbnailImageUrl = "",
        developerType = "none",
        displayName = "Friend",
        friendKey = "",
        id = "usr_friend",
        imageUrl = "",
        isFriend = true,
        lastLogin = "",
        lastActivity = "",
        lastPlatform = "standalonewindows",
        location = "wrld_world:instance_a",
        profilePicOverride = "",
        status = UserStatus.Active,
        statusDescription = "",
        userIcon = "",
        pronouns = null,
    )

    private companion object {
        const val UPDATED_LAST_ACTIVITY = "2026-08-10T17:15:00.000Z"
    }
}
