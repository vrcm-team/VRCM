package io.github.vrcmteam.vrcm.presentation.screens.world

import androidx.lifecycle.ViewModelStore
import com.russhwolf.settings.MapSettings
import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.di.supports.PersistentCookiesStorage
import io.github.vrcmteam.vrcm.network.api.auth.AuthApi
import io.github.vrcmteam.vrcm.network.api.files.FileApi
import io.github.vrcmteam.vrcm.network.api.groups.GroupsApi
import io.github.vrcmteam.vrcm.network.api.instances.InstancesApi
import io.github.vrcmteam.vrcm.network.api.invite.InviteApi
import io.github.vrcmteam.vrcm.network.api.users.UsersApi
import io.github.vrcmteam.vrcm.network.api.worlds.WorldsApi
import io.github.vrcmteam.vrcm.network.api.worlds.data.WorldData
import io.github.vrcmteam.vrcm.presentation.favorites.FavoriteEntrySource
import io.github.vrcmteam.vrcm.presentation.screens.world.data.InstanceVo
import io.github.vrcmteam.vrcm.presentation.screens.world.data.WorldProfileVo
import io.github.vrcmteam.vrcm.presentation.settings.locale.LocaleStringsZhHans
import io.github.vrcmteam.vrcm.service.AuthService
import io.github.vrcmteam.vrcm.service.HomeWorldService
import io.github.vrcmteam.vrcm.service.InstanceCreationService
import io.github.vrcmteam.vrcm.service.NetworkInstanceCreationRequest
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
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import kotlinx.serialization.json.Json
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import org.koin.core.logger.EmptyLogger
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertTrue

class WorldProfileInstanceCloseTest : MainDispatcherTest() {
    private val models = mutableListOf<WorldProfileScreenModel>()
    private val authServices = mutableListOf<AuthService>()

    @AfterTest
    fun cleanUp() = runBlocking {
        models.forEach { model ->
            ViewModelStore().apply {
                put("test", model)
                clear()
            }
        }
        authServices.forEach { it.closeAndJoin() }
        SharedFlowCentre.emitLogout()
    }

    @Test
    fun closeWinsOverAnEarlierInstanceRefreshResponse() = runBlocking {
        val refreshStarted = CompletableDeferred<Unit>()
        val releaseRefresh = CompletableDeferred<Unit>()
        val client = HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    when {
                        request.method == HttpMethod.Get && request.url.encodedPath ==
                            "/worlds/$WORLD_ID/$INSTANCE_ID" -> {
                            refreshStarted.complete(Unit)
                            releaseRefresh.await()
                            respondJson(instanceJson(active = true, closedAt = null))
                        }

                        request.method == HttpMethod.Delete && request.url.encodedPath ==
                            "/instances/$LOCATION" ->
                            respondJson(instanceJson(active = false, closedAt = CLOSED_AT))

                        request.url.encodedPath.startsWith("/users/") ->
                            respond("owner unavailable", HttpStatusCode.InternalServerError)

                        else -> error("Unexpected request: ${request.method} ${request.url}")
                    }
                }
            }
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
        val model = createModel(client)
        SharedFlowCentre.emitAuthenticated(
            io.github.vrcmteam.vrcm.service.data.AccountDto(userId = USER_ID, username = "owner")
        )

        try {
            model.loadWorldData(
                WorldProfileVo(
                    worldId = WORLD_ID,
                    instances = listOf(InstanceVo(id = LOCATION, instanceId = INSTANCE_ID,
                        worldId = WORLD_ID, location = LOCATION, ownerId = USER_ID)),
                )
            )
            refreshStarted.await()

            model.requestInstanceClose(model.worldProfileState.value!!.instances.single(), LocaleStringsZhHans)
            awaitUntil { model.instanceCloseState.value is InstanceCloseState.AwaitingConfirmation }
            model.confirmInstanceClose(LocaleStringsZhHans)
            awaitUntil { model.worldProfileState.value?.instances?.isEmpty() == true }

            releaseRefresh.complete(Unit)
            awaitUntil { !model.isLoading.value }

            assertTrue(model.worldProfileState.value!!.instances.isEmpty())
        } finally {
            client.close()
        }
    }

    private fun createModel(client: HttpClient): WorldProfileScreenModel {
        val authService = AuthService(
            authApi = AuthApi(client),
            accountDao = AccountDao(MapSettings(), InMemorySecureStorage()),
            cookiesStorage = PersistentCookiesStorage(EmptyLogger()),
            accountCacheManager = AccountCacheManager(
                friendListCacheStore = InMemoryFriendListCacheStore(),
                userProfileCacheStore = InMemoryUserProfileCacheStore(),
                friendActivityStore = io.github.vrcmteam.vrcm.storage.NoOpFriendActivityCacheStore,
                meetupCardConfigDao = MeetupCardConfigDao(MapSettings()),
                meetupCardAssetStore = MeetupCardAssetStore(FakeFileSystem(), "/assets".toPath()),
            ),
        ).also(authServices::add)
        val worldData = worldData()
        val cacheStore = object : WorldProfileCacheStore {
            override suspend fun load(worldId: String) = WorldProfileCache(
                world = worldData,
                cachedAtEpochMilliseconds = Long.MAX_VALUE,
                platformFileSizes = emptyList(),
            )

            override suspend fun save(cache: WorldProfileCache) = Unit
            override suspend fun delete(worldId: String) = Unit
            override suspend fun saveAndCommitIfCurrent(
                cache: WorldProfileCache,
                canStart: () -> Boolean,
                commit: () -> Boolean,
            ) = canStart() && commit()
            override suspend fun clearAll() = Unit
        }
        val favoriteSource = object : FavoriteEntrySource {
            private val favorites = MutableStateFlow(emptyMap<
                io.github.vrcmteam.vrcm.network.api.favorite.data.FavoriteGroupData,
                List<io.github.vrcmteam.vrcm.network.api.favorite.data.FavoriteData>,
            >())

            override fun favoritesByGroup(
                type: io.github.vrcmteam.vrcm.network.api.attributes.FavoriteType,
            ) = favorites

            override suspend fun load(
                type: io.github.vrcmteam.vrcm.network.api.attributes.FavoriteType,
            ) = Result.success(Unit)
        }
        return WorldProfileScreenModel(
            worldsApi = WorldsApi(client),
            instancesApi = InstancesApi(client),
            usersApi = UsersApi(client),
            groupsApi = GroupsApi(client),
            authService = authService,
            instanceCreationService = InstanceCreationService(
                NetworkInstanceCreationRequest(authService, InstancesApi(client))
            ),
            favoriteEntrySource = favoriteSource,
            inviteApi = InviteApi(client),
            worldPlatformService = WorldPlatformService(FileApi(client), authService),
            worldProfileCacheStore = cacheStore,
            homeWorldManager = HomeWorldService(UsersApi(client), authService),
            worldEditor = TestNoOpWorldEditor,
        ).also(models::add)
    }

    private fun worldData() = WorldData(
        authorId = USER_ID,
        authorName = "Owner",
        capacity = 16,
        createdAt = null,
        description = null,
        favorites = 0,
        featured = false,
        heat = 0,
        id = WORLD_ID,
        imageUrl = "",
        labsPublicationDate = "",
        name = "World",
        namespace = null,
        organization = "",
        popularity = 0,
        publicationDate = "",
        recommendedCapacity = 16,
        releaseStatus = "public",
        tags = emptyList(),
        thumbnailImageUrl = null,
        udonProducts = emptyList(),
        unityPackages = emptyList(),
        instances = listOf(listOf(INSTANCE_ID)),
        updatedAt = null,
        version = 1,
        visits = 0,
    )

    private fun instanceJson(active: Boolean, closedAt: String?): String =
        """
        {
          "active":$active,"canRequestInvite":false,"capacity":16,"clientNumber":"1",
          "closedAt":${closedAt?.let { "\"$it\"" } ?: "null"},"full":false,"hidden":null,
          "id":"$LOCATION","instanceId":"$INSTANCE_ID","location":"$LOCATION","n_users":0,
          "name":"12345","ownerId":"$USER_ID","permanent":false,"photonRegion":"us",
          "platforms":{"android":0,"ios":0,"standalonewindows":0},"queueEnabled":false,
          "queueSize":0,"recommendedCapacity":16,"region":"us","secureName":"secure",
          "strict":false,"tags":[],"type":"private","userCount":0,
          "world":${worldJson()},"worldId":"$WORLD_ID"
        }
        """.trimIndent()

    private fun worldJson(): String =
        """
        {"authorId":"$USER_ID","authorName":"Owner","capacity":16,"created_at":null,
        "description":null,"favorites":0,"featured":false,"heat":0,"id":"$WORLD_ID",
        "imageUrl":"","labsPublicationDate":"","name":"World","namespace":null,
        "organization":"","popularity":0,"publicationDate":"","recommendedCapacity":16,
        "releaseStatus":"public","tags":[],"thumbnailImageUrl":null,"udonProducts":[],
        "unityPackages":[],"updated_at":null,"version":1,"visits":0}
        """.trimIndent()

    private fun io.ktor.client.engine.mock.MockRequestHandleScope.respondJson(content: String) =
        respond(
            content = content,
            status = HttpStatusCode.OK,
            headers = headersOf(HttpHeaders.ContentType, "application/json"),
        )

    private suspend fun awaitUntil(predicate: () -> Boolean) {
        withTimeout(2_000) {
            while (!predicate()) yield()
        }
    }

    private companion object {
        const val USER_ID = "usr_00000000-0000-0000-0000-000000000001"
        const val WORLD_ID = "wrld_00000000-0000-0000-0000-000000000002"
        const val INSTANCE_ID = "12345~region(us)"
        const val LOCATION = "$WORLD_ID:$INSTANCE_ID"
        const val CLOSED_AT = "2026-09-01T00:00:00.000Z"
    }
}
