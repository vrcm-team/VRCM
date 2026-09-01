package io.github.vrcmteam.vrcm.presentation.screens.world

import androidx.lifecycle.ViewModelStore
import com.russhwolf.settings.MapSettings
import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.di.supports.PersistentCookiesStorage
import io.github.vrcmteam.vrcm.network.api.attributes.FavoriteType
import io.github.vrcmteam.vrcm.network.api.files.FileApi
import io.github.vrcmteam.vrcm.network.api.groups.GroupsApi
import io.github.vrcmteam.vrcm.network.api.instances.InstancesApi
import io.github.vrcmteam.vrcm.network.api.invite.InviteApi
import io.github.vrcmteam.vrcm.network.api.users.UsersApi
import io.github.vrcmteam.vrcm.network.api.worlds.WorldsApi
import io.github.vrcmteam.vrcm.network.api.worlds.data.WorldData
import io.github.vrcmteam.vrcm.presentation.favorites.FavoriteEntrySource
import io.github.vrcmteam.vrcm.presentation.screens.world.data.WorldProfileVo
import io.github.vrcmteam.vrcm.service.AuthService
import io.github.vrcmteam.vrcm.service.HomeWorldService
import io.github.vrcmteam.vrcm.service.data.AccountDto
import io.github.vrcmteam.vrcm.service.WorldPlatformService
import io.github.vrcmteam.vrcm.storage.AccountCacheManager
import io.github.vrcmteam.vrcm.storage.AccountDao
import io.github.vrcmteam.vrcm.storage.WorldProfileCacheStore
import io.github.vrcmteam.vrcm.storage.data.WorldProfileCache
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
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import org.koin.core.logger.EmptyLogger
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class WorldProfileScreenModelTest : MainDispatcherTest() {
    @AfterTest
    fun clearSession() = runTest { SharedFlowCentre.emitLogout() }

    @Test
    fun deletionInvalidatesLateWorldRefreshBeforeItWritesCache() = runTest {
        val getStarted = CompletableDeferred<Unit>()
        val releaseGet = CompletableDeferred<Unit>()
        val updatedWorld = testWorld(name = "late response")
        var worldGetCount = 0
        val fixture = worldProfileFixture { request ->
            when {
                request.url.encodedPath == "/api/1/auth/user" ->
                    jsonResponse(currentUserJson(cachedAccount()))

                request.method == HttpMethod.Get &&
                    request.url.encodedPath == "/api/1/worlds/wrld_owned" -> {
                    worldGetCount++
                    getStarted.complete(Unit)
                    releaseGet.await()
                    jsonResponse(Json.encodeToString(WorldData.serializer(), updatedWorld))
                }

                request.method == HttpMethod.Delete &&
                    request.url.encodedPath == "/api/1/worlds/wrld_owned" ->
                    respond("", HttpStatusCode.OK)

                else -> error("Unexpected request: ${request.method} ${request.url}")
            }
        }
        val cache = RecordingWorldProfileCache(
            cached = WorldProfileCache(
                world = testWorld(name = "cached", instances = null),
                cachedAtEpochMilliseconds = 0L,
            ),
        )
        var model: WorldProfileScreenModel? = null
        try {
            fixture.service.restoreAuth()
            model = WorldProfileScreenModel(
                worldsApi = WorldsApi(fixture.client),
                instancesApi = InstancesApi(fixture.client),
                usersApi = UsersApi(fixture.client),
                groupsApi = GroupsApi(fixture.client),
                authService = fixture.service,
                favoriteEntrySource = EmptyFavoriteEntrySource(),
                inviteApi = InviteApi(fixture.client),
                worldPlatformService = WorldPlatformService(FileApi(fixture.client), fixture.service),
                worldProfileCacheStore = cache,
                homeWorldManager = HomeWorldService(UsersApi(fixture.client), fixture.service),
            )
            val screenModel = requireNotNull(model)

            screenModel.loadWorldData(WorldProfileVo(worldId = "wrld_owned", authorID = "usr_owner"))
            assertFalse(screenModel.deletionState.value.isAvailable)
            getStarted.await()
            assertTrue(screenModel.deletionState.value.canDelete)
            assertTrue(screenModel.isLoading.value)

            assertTrue(screenModel.deleteWorld())
            screenModel.refreshWorldData()
            releaseGet.complete(Unit)
            cache.deleted.await()

            assertTrue(cache.saved.isEmpty())
            assertEquals(listOf("wrld_owned"), cache.deletedWorlds)
            assertEquals("cached", screenModel.worldProfileState.value?.worldName)
            assertEquals(1, worldGetCount)
        } finally {
            model?.let {
                ViewModelStore().apply {
                    put("world-profile", it)
                    clear()
                }
            }
            fixture.client.close()
        }
    }
}

private class EmptyFavoriteEntrySource : FavoriteEntrySource {
    private val groups = MutableStateFlow(emptyMap<io.github.vrcmteam.vrcm.network.api.favorite.data.FavoriteGroupData, List<io.github.vrcmteam.vrcm.network.api.favorite.data.FavoriteData>>())

    override fun favoritesByGroup(type: FavoriteType): StateFlow<Map<io.github.vrcmteam.vrcm.network.api.favorite.data.FavoriteGroupData, List<io.github.vrcmteam.vrcm.network.api.favorite.data.FavoriteData>>> = groups

    override suspend fun load(type: FavoriteType): Result<Unit> = Result.success(Unit)
}

private class RecordingWorldProfileCache(
    private val cached: WorldProfileCache?,
) : WorldProfileCacheStore {
    val saved = mutableListOf<WorldProfileCache>()
    val deletedWorlds = mutableListOf<String>()
    val deleted = CompletableDeferred<Unit>()

    override suspend fun load(worldId: String): WorldProfileCache? = cached

    override suspend fun save(cache: WorldProfileCache) {
        saved += cache
    }

    override suspend fun delete(worldId: String) {
        deletedWorlds += worldId
        deleted.complete(Unit)
    }

    override suspend fun clearAll() = Unit
}

private data class WorldProfileFixture(
    val service: AuthService,
    val client: HttpClient,
)

private fun worldProfileFixture(handler: MockRequestHandler): WorldProfileFixture {
    val cookies = PersistentCookiesStorage(EmptyLogger())
    val client = HttpClient(MockEngine) {
        engine { addHandler(handler) }
        defaultRequest { url("https://api.vrchat.cloud/api/1/") }
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        install(HttpCookies) { storage = cookies }
    }
    val account = cachedAccount()
    val accountDao = AccountDao(MapSettings(), InMemorySecureStorage()).also { it.saveAccountInfo(account) }
    val service = AuthService(
        authApi = io.github.vrcmteam.vrcm.network.api.auth.AuthApi(client),
        accountDao = accountDao,
        cookiesStorage = cookies,
        accountCacheManager = AccountCacheManager(
            friendListCacheStore = InMemoryFriendListCacheStore(),
            userProfileCacheStore = InMemoryUserProfileCacheStore(),
            friendActivityStore = NoOpFriendActivityCacheStore,
            meetupCardConfigDao = MeetupCardConfigDao(MapSettings()),
            meetupCardAssetStore = MeetupCardAssetStore(FakeFileSystem(), "/meetup-assets".toPath()),
        ),
    )
    return WorldProfileFixture(service, client)
}

private fun testWorld(
    name: String,
    instances: List<List<String>>? = emptyList(),
) = WorldData(
    authorId = "usr_owner",
    authorName = "owner",
    capacity = 16,
    createdAt = "2026-01-01T00:00:00.000Z",
    description = name,
    favorites = 0,
    featured = false,
    heat = 0,
    id = "wrld_owned",
    imageUrl = "",
    labsPublicationDate = "2026-01-01T00:00:00.000Z",
    name = name,
    namespace = null,
    organization = "vrchat",
    popularity = 0,
    publicationDate = "2026-01-01T00:00:00.000Z",
    recommendedCapacity = 16,
    releaseStatus = "public",
    tags = emptyList(),
    thumbnailImageUrl = null,
    udonProducts = emptyList(),
    unityPackages = emptyList(),
    instances = instances,
    updatedAt = "2026-01-01T00:00:00.000Z",
    version = 1,
    visits = 0,
)

private fun cachedAccount() = AccountDto(
    userId = "usr_owner",
    username = "owner",
    password = "owner-password",
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
      "requiresTwoFactorAuth":null,"ageVerificationStatus":"verified","ageVerified":true,
      "acceptedPrivacyVersion":0,"acceptedTOSVersion":0,"accountDeletionDate":null,
      "accountDeletionLog":null,"activeFriends":[],"allowAvatarCopying":true,"bio":null,
      "bioLinks":[],"currentAvatar":"","currentAvatarAssetUrl":null,"currentAvatarImageUrl":"",
      "currentAvatarTags":[],"currentAvatarThumbnailImageUrl":"","date_joined":"",
      "developerType":"none","displayName":"${account.username}","emailVerified":true,
      "fallbackAvatar":"","friendGroupNames":[],"friendKey":"","friends":[],"googleId":"",
      "hasBirthday":true,"hasEmail":true,"hasLoggedInFromClient":true,"hasPendingEmail":false,
      "hideContentFilterSettings":false,"homeLocation":"","id":"${account.userId}",
      "isFriend":false,"last_activity":"","last_login":"","last_platform":"standalonewindows",
      "obfuscatedEmail":"","obfuscatedPendingEmail":"","oculusId":"","offlineFriends":[],
      "onlineFriends":[],"pastDisplayNames":[],"picoId":"","presence":{"avatarThumbnail":null,
      "displayName":"${account.username}","groups":[],"id":"${account.userId}","instance":"",
      "instanceType":"","isRejoining":null,"platform":"standalonewindows","profilePicOverride":null,
      "status":"active","travelingToInstance":"","travelingToWorld":"","world":""},
      "profilePicOverride":"","state":"online","status":"active","statusDescription":"",
      "statusFirstTime":false,"statusHistory":[],"steamDetails":{},"steamId":"","tags":[],
      "twoFactorAuthEnabled":false,"twoFactorAuthEnabledDate":null,"unsubscribe":false,"updated_at":"",
      "userIcon":"","userLanguage":null,"userLanguageCode":null,"username":"${account.username}",
      "viveId":"","pronouns":null
    }
""".trimIndent()
