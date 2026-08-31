package io.github.vrcmteam.vrcm.presentation.screens.world

import androidx.lifecycle.ViewModelStore
import com.russhwolf.settings.MapSettings
import io.github.vrcmteam.vrcm.di.supports.PersistentCookiesStorage
import io.github.vrcmteam.vrcm.network.api.attributes.FavoriteType
import io.github.vrcmteam.vrcm.network.api.auth.AuthApi
import io.github.vrcmteam.vrcm.network.api.favorite.data.FavoriteData
import io.github.vrcmteam.vrcm.network.api.favorite.data.FavoriteGroupData
import io.github.vrcmteam.vrcm.network.api.files.FileApi
import io.github.vrcmteam.vrcm.network.api.files.data.PlatformType
import io.github.vrcmteam.vrcm.network.api.groups.GroupsApi
import io.github.vrcmteam.vrcm.network.api.instances.InstancesApi
import io.github.vrcmteam.vrcm.network.api.invite.InviteApi
import io.github.vrcmteam.vrcm.network.api.users.UsersApi
import io.github.vrcmteam.vrcm.network.api.worlds.WorldsApi
import io.github.vrcmteam.vrcm.presentation.favorites.FavoriteEntrySource
import io.github.vrcmteam.vrcm.presentation.screens.world.data.WorldProfileVo
import io.github.vrcmteam.vrcm.service.AuthService
import io.github.vrcmteam.vrcm.service.WorldPlatformService
import io.github.vrcmteam.vrcm.storage.AccountCacheManager
import io.github.vrcmteam.vrcm.storage.AccountDao
import io.github.vrcmteam.vrcm.storage.InMemoryFriendListCacheStore
import io.github.vrcmteam.vrcm.storage.InMemorySecureStorage
import io.github.vrcmteam.vrcm.storage.InMemoryUserProfileCacheStore
import io.github.vrcmteam.vrcm.storage.WorldProfileCacheStore
import io.github.vrcmteam.vrcm.storage.data.WorldProfileCache
import io.github.vrcmteam.vrcm.storage.meetup.MeetupCardAssetStore
import io.github.vrcmteam.vrcm.storage.meetup.MeetupCardConfigDao
import io.github.vrcmteam.vrcm.testing.MainDispatcherTest
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import kotlinx.serialization.json.Json
import org.koin.core.logger.EmptyLogger
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class WorldPlatformFileSizesTest : MainDispatcherTest() {
    private val models = mutableListOf<WorldProfileScreenModel>()
    private val clients = mutableListOf<HttpClient>()

    @AfterTest
    fun disposeResources() {
        models.forEach(::clearModel)
        clients.forEach(HttpClient::close)
    }

    @Test
    fun worldLoadPublishesAndCachesReferencedPlatformPackageSizes() = runBlocking {
        val worldRequests = atomic(0)
        val client = HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    when (request.url.encodedPath) {
                        "/worlds/$WORLD_ID" -> {
                            worldRequests.incrementAndGet()
                            jsonResponse(worldJson())
                        }
                        "/file/file_pc_current" -> jsonResponse(
                            fileJson(
                                fileId = "file_pc_current",
                                versions = listOf(1 to 64L * MEBIBYTE, 2 to 128L * MEBIBYTE),
                            )
                        )
                        "/file/file_pc_old" -> jsonResponse(
                            fileJson("file_pc_old", listOf(1 to 512L * MEBIBYTE))
                        )
                        "/file/file_android" -> jsonResponse(
                            fileJson("file_android", listOf(3 to 48L * MEBIBYTE))
                        )
                        "/file/file_ios" -> jsonResponse(
                            fileJson("file_ios", listOf(1 to 40L * MEBIBYTE))
                        )
                        else -> error("Unexpected request: ${request.url}")
                    }
                }
            }
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }.also(clients::add)
        val cache = RecordingWorldProfileCacheStore()
        val firstModel = createModel(client, cache)

        firstModel.loadWorldData(WorldProfileVo(worldId = WORLD_ID))
        awaitProfile {
            firstModel.worldProfileState.value?.platformFileSizes?.size == 3 &&
                cache.value.value?.platformFileSizes?.size == 3
        }

        val firstSizes = firstModel.worldProfileState.value.orEmptyPlatformSizes()
        assertEquals(
            listOf(PlatformType.Windows, PlatformType.Android, PlatformType.Ios),
            firstSizes.map { it.platform },
        )
        assertEquals(listOf("PC", "Android", "iOS"), firstSizes.map { it.displayName })
        assertEquals(listOf(64L, 48L, 40L), firstSizes.map { it.sizeInBytes / MEBIBYTE })
        assertEquals(1, worldRequests.value)

        clearModel(firstModel)
        models.remove(firstModel)
        val cachedModel = createModel(client, cache)
        cachedModel.loadWorldData(WorldProfileVo(worldId = WORLD_ID))
        awaitProfile { cachedModel.worldProfileState.value?.platformFileSizes?.size == 3 }

        assertEquals(firstSizes, cachedModel.worldProfileState.value.orEmptyPlatformSizes())
        assertEquals(1, worldRequests.value)
    }

    private fun createModel(
        client: HttpClient,
        cache: WorldProfileCacheStore,
    ): WorldProfileScreenModel {
        val logger = EmptyLogger()
        val authService = AuthService(
            authApi = AuthApi(client),
            accountDao = AccountDao(MapSettings(), InMemorySecureStorage()),
            cookiesStorage = PersistentCookiesStorage(logger),
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
        return WorldProfileScreenModel(
            worldsApi = WorldsApi(client),
            instancesApi = InstancesApi(client),
            usersApi = UsersApi(client),
            groupsApi = GroupsApi(client),
            authService = authService,
            favoriteEntrySource = CachedNotFavoriteSource,
            inviteApi = InviteApi(client),
            worldPlatformService = WorldPlatformService(FileApi(client)),
            worldProfileCacheStore = cache,
        ).also(models::add)
    }

    private fun clearModel(model: WorldProfileScreenModel) {
        ViewModelStore().apply {
            put("test", model)
            clear()
        }
    }

    private companion object {
        const val WORLD_ID = "wrld_platform_sizes"
        const val MEBIBYTE = 1024L * 1024L
    }
}

private data object CachedNotFavoriteSource : FavoriteEntrySource {
    private val favorites = MutableStateFlow<Map<FavoriteGroupData, List<FavoriteData>>>(emptyMap())

    override fun favoritesByGroup(type: FavoriteType): StateFlow<Map<FavoriteGroupData, List<FavoriteData>>> =
        favorites

    override suspend fun load(type: FavoriteType): Result<Unit> = Result.success(Unit)

    override suspend fun cachedFavorite(type: FavoriteType, favoriteId: String): Boolean = false
}

private class RecordingWorldProfileCacheStore : WorldProfileCacheStore {
    val value = MutableStateFlow<WorldProfileCache?>(null)

    override suspend fun load(worldId: String): WorldProfileCache? = value.value

    override suspend fun save(cache: WorldProfileCache) {
        value.value = cache
    }

    override suspend fun clearAll() {
        value.value = null
    }
}

private fun WorldProfileVo?.orEmptyPlatformSizes() = this?.platformFileSizes.orEmpty()

private suspend fun awaitProfile(predicate: () -> Boolean) {
    withTimeout(2_000) {
        while (!predicate()) yield()
    }
}

private fun MockRequestHandleScope.jsonResponse(content: String) = respond(
    content = content,
    status = HttpStatusCode.OK,
    headers = headersOf(HttpHeaders.ContentType, "application/json"),
)

private fun worldJson() = """
    {
      "authorId":"usr_author","authorName":"Author","capacity":16,"created_at":null,
      "description":null,"favorites":0,"featured":false,"heat":0,"id":"wrld_platform_sizes",
      "imageUrl":"","labsPublicationDate":"","name":"Platform World","namespace":null,
      "organization":"","popularity":0,"privateOccupants":0,"publicOccupants":0,
      "publicationDate":"","recommendedCapacity":16,"releaseStatus":"public","tags":[],
      "thumbnailImageUrl":null,"udonProducts":[],"instances":[],"updated_at":null,"version":1,
      "visits":0,
      "unityPackages":[
        {
          "assetUrl":"https://api.vrchat.cloud/api/1/file/file_pc_old/1/file","assetVersion":1,
          "created_at":"2025-01-01T00:00:00.000Z","id":"unp_pc_old",
          "platform":"standalonewindows","pluginUrl":null,"unitySortNumber":1,
          "unityVersion":"2022.3.22f1"
        },
        {
          "assetUrl":"https://api.vrchat.cloud/api/1/file/file_pc_current/1/file","assetVersion":1,
          "created_at":"2026-01-01T00:00:00.000Z","id":"unp_pc_current",
          "platform":"standalonewindows","pluginUrl":null,"unitySortNumber":2,
          "unityVersion":"2022.3.22f1"
        },
        {
          "assetUrl":"https://api.vrchat.cloud/api/1/file/file_android/3/file","assetVersion":3,
          "created_at":"2026-01-01T00:00:00.000Z","id":"unp_android",
          "platform":"android","pluginUrl":null,"unitySortNumber":2,"unityVersion":"2022.3.22f1"
        },
        {
          "assetUrl":"https://api.vrchat.cloud/api/1/file/file_ios/1/file","assetVersion":1,
          "created_at":"2026-01-01T00:00:00.000Z","id":"unp_ios",
          "platform":"ios","pluginUrl":null,"unitySortNumber":2,"unityVersion":"2022.3.22f1"
        }
      ]
    }
""".trimIndent()

private fun fileJson(fileId: String, versions: List<Pair<Int, Long>>) = """
    {
      "id":"$fileId","name":"$fileId","ownerId":"usr_author",
      "mimeType":"application/x-avatar","extension":".vrcw",
      "versions":[
        ${versions.joinToString(",") { (version, size) ->
            """
              {
                "version":$version,"status":"complete","created_at":"2026-01-01T00:00:00.000Z",
                "file":{
                  "category":"simple","fileName":"world.vrcw","md5":"md5","sizeInBytes":$size,
                  "status":"complete","uploadId":"upl_$version","url":"https://example.com/world.vrcw"
                }
              }
            """.trimIndent()
        }}
      ]
    }
""".trimIndent()
