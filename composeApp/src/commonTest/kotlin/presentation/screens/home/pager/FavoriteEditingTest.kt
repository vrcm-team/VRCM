package io.github.vrcmteam.vrcm.presentation.screens.home.pager

import com.russhwolf.settings.MapSettings
import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.di.supports.PersistentCookiesStorage
import io.github.vrcmteam.vrcm.network.api.attributes.UserStatus
import io.github.vrcmteam.vrcm.network.api.friends.date.FriendData
import io.github.vrcmteam.vrcm.network.api.avatars.data.AvatarData
import io.github.vrcmteam.vrcm.network.api.attributes.FavoriteType
import io.github.vrcmteam.vrcm.network.api.auth.AuthApi
import io.github.vrcmteam.vrcm.network.api.avatars.AvatarsApi
import io.github.vrcmteam.vrcm.network.api.favorite.FavoriteApi
import io.github.vrcmteam.vrcm.network.api.favorite.data.AddFavoriteRequest
import io.github.vrcmteam.vrcm.network.api.favorite.data.FavoriteData
import io.github.vrcmteam.vrcm.network.api.favorite.data.FavoriteGroupData
import io.github.vrcmteam.vrcm.network.api.friends.FriendsApi
import io.github.vrcmteam.vrcm.network.api.users.UsersApi
import io.github.vrcmteam.vrcm.network.api.worlds.WorldsApi
import io.github.vrcmteam.vrcm.network.api.worlds.data.FavoritedWorld
import io.github.vrcmteam.vrcm.service.*
import io.github.vrcmteam.vrcm.service.data.AccountDto
import io.github.vrcmteam.vrcm.storage.*
import io.github.vrcmteam.vrcm.storage.meetup.MeetupCardAssetStore
import io.github.vrcmteam.vrcm.storage.meetup.MeetupCardConfigDao
import io.github.vrcmteam.vrcm.testing.MainDispatcherTest
import io.github.vrcmteam.vrcm.testing.clearAndJoin
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.*
import io.ktor.http.content.TextContent
import io.ktor.serialization.kotlinx.json.json
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import org.koin.core.logger.EmptyLogger
import kotlin.test.*

class FavoriteEditingTest : MainDispatcherTest() {
    @Test
    fun movingUpdatesBothGroupFiltersAndPersistentCacheWithoutReloadingTheWholeList() = runBlocking {
        val fixture = editingFixture()
        try {
            val reads = fixture.listReads.value
            fixture.model.updateWorldGroupOptions(WorldGroupOptions(fixture.group("worlds1")))
            assertEquals(listOf("wrld_one"), fixture.model.worldList.value.map { it.id })

            fixture.edit().getOrThrow()
            awaitEditing { fixture.model.worldList.value.isEmpty() }
            fixture.model.updateWorldGroupOptions(WorldGroupOptions(fixture.group("worlds2")))
            assertEquals(listOf("wrld_one"), fixture.model.worldList.value.map { it.id })
            assertEquals("fvrt_new", fixture.model.worldList.value.single().favoriteId)
            awaitEditing {
                fixture.cache.load("usr_editor")?.favoritedWorlds
                    ?.single { it.groupKey == "worlds2" }?.worlds?.singleOrNull()?.favoriteId == "fvrt_new"
            }
            assertEquals(1, fixture.model.worldTotal.value)
            assertEquals(reads, fixture.listReads.value)
        } finally { fixture.close() }
    }

    @Test
    fun failedAdditionPublishesConfirmedRemovalAndRetryDoesNotDeleteTheOldRecordAgain() = runBlocking {
        val fixture = editingFixture()
        try {
            fixture.failNextAdd.value = true
            assertTrue(fixture.edit().isFailure)
            awaitEditing { fixture.model.worldList.value.isEmpty() }
            assertEquals(emptyList(), fixture.favoriteService.favoritesByGroup(FavoriteType.World).value.values.flatten())

            fixture.edit().getOrThrow()
            awaitEditing { fixture.model.worldList.value.singleOrNull()?.favoriteId == "fvrt_new" }
            assertEquals(1, fixture.deletes.value)
            assertEquals(listOf("worlds2"), fixture.records.value.single().tags)
        } finally { fixture.close() }
    }

    @Test
    fun anOlderWorldListResponseCannotPutAMovedWorldBackInTheOldGroup() = runBlocking {
        val fixture = editingFixture()
        try {
            fixture.holdWorldList.value = true
            awaitEditing {
                fixture.model.refreshCurrentTabCacheData(tabIndex = 1)
                fixture.worldListStarted.isCompleted
            }
            fixture.edit().getOrThrow()
            fixture.releaseWorldList.complete(Unit)
            awaitEditing { 1 !in fixture.model.refreshingTabs.value }

            fixture.model.updateWorldGroupOptions(WorldGroupOptions(fixture.group("worlds1")))
            assertTrue(fixture.model.worldList.value.isEmpty())
            fixture.model.updateWorldGroupOptions(WorldGroupOptions(fixture.group("worlds2")))
            assertEquals("fvrt_new", fixture.model.worldList.value.single().favoriteId)
            awaitEditing {
                fixture.cache.load("usr_editor")?.favoritedWorlds
                    ?.single { it.groupKey == "worlds2" }?.worlds?.singleOrNull()?.favoriteId == "fvrt_new"
            }
        } finally { fixture.close() }
    }

    @Test
    fun authenticationRetryDuringAdditionDoesNotReplayTheSuccessfulDeletion() = runBlocking {
        val fixture = editingFixture()
        try {
            fixture.unauthorizedNextAdd.value = true
            val oldToken = assertNotNull(SharedFlowCentre.currentSession.value).token
            fixture.edit().getOrThrow()
            awaitEditing {
                fixture.favoriteService.favoritesByGroup(FavoriteType.World).value.values.flatten()
                    .singleOrNull()?.id == "fvrt_new"
            }
            assertNotEquals(oldToken, assertNotNull(SharedFlowCentre.currentSession.value).token)
            assertEquals(1, fixture.deletes.value)
            assertEquals("worlds2", fixture.records.value.single().tags.single())
        } finally { fixture.close() }
    }

    @Test
    fun newlyFavoritedWorldLoadsOnlyItsMissingDetailsAndRemovalPreservesTheOthers() = runBlocking {
        val fixture = editingFixture()
        try {
            val reads = fixture.listReads.value
            fixture.edit(FavoriteEditRequest(FavoriteType.World, "wrld_two", null, "worlds2")).getOrThrow()
            awaitEditing { fixture.model.worldList.value.size == 2 }
            assertEquals(setOf("wrld_one", "wrld_two"), fixture.model.worldList.value.map { it.id }.toSet())
            assertEquals(2, fixture.model.worldTotal.value)
            assertEquals(reads, fixture.listReads.value)

            fixture.edit(FavoriteEditRequest(FavoriteType.World, "wrld_one", "fvrt_old", null)).getOrThrow()
            awaitEditing { fixture.model.worldList.value.map { it.id } == listOf("wrld_two") }
            assertEquals(1, fixture.model.worldTotal.value)
        } finally { fixture.close() }
    }

    @Test
    fun failedMoveFromLocalKeepsTheLocalFavoriteAvailableForRetry() = runBlocking {
        val fixture = editingFixture()
        try {
            fixture.favoriteService.addFavorite("wrld_local", FavoriteType.World, "__local_world__")
            fixture.favoriteService.loadFavoriteByGroup(FavoriteType.World).getOrThrow()
            val request = FavoriteEditRequest(FavoriteType.World, "wrld_local", "local|world|wrld_local", "worlds2")
            fixture.failNextAdd.value = true
            assertTrue(fixture.edit(request).isFailure)
            assertTrue(fixture.favoriteService.favoritesByGroup(FavoriteType.World).value
                .filterKeys { it.ownerId == "local" }.values.flatten().any { it.favoriteId == "wrld_local" })
            fixture.edit(request).getOrThrow()
            assertTrue(fixture.favoriteService.favoritesByGroup(FavoriteType.World).value
                .filterKeys { it.ownerId == "local" }.values.flatten().isEmpty())
            awaitEditing { fixture.model.worldList.value.any { it.id == "wrld_local" } }
        } finally { fixture.close() }
    }
    @Test
    fun avatarMoveUpdatesBothGroupFiltersWithoutReloadingAndRemovalUpdatesAllAndCache() = runBlocking {
        val fixture = editingFixture(FavoriteType.Avatar)
        try {
            val reads = fixture.listReads.value
            fixture.model.updateAvatarGroupOptions(AvatarGroupOptions(fixture.group("avatars1")))
            assertEquals(listOf("avtr_one"), fixture.model.avatarList.value.map { it.id })
            fixture.edit(FavoriteEditRequest(FavoriteType.Avatar, "avtr_one", "fvrt_old", "avatars2")).getOrThrow()
            awaitEditing { fixture.model.avatarList.value.isEmpty() }
            fixture.model.updateAvatarGroupOptions(AvatarGroupOptions(fixture.group("avatars2")))
            assertEquals(listOf("avtr_one"), fixture.model.avatarList.value.map { it.id })
            assertEquals(reads, fixture.listReads.value)

            fixture.model.updateAvatarGroupOptions(AvatarGroupOptions())
            fixture.edit(FavoriteEditRequest(FavoriteType.Avatar, "avtr_one", "fvrt_new", null)).getOrThrow()
            awaitEditing { fixture.model.avatarList.value.isEmpty() && fixture.model.avatarTotal.value == 0 }
            awaitEditing { fixture.cache.load("usr_editor")?.favoritedAvatars?.isEmpty() == true }
        } finally { fixture.close() }
    }

    @Test
    fun newAvatarFetchesItsDetailsAndFailedMoveCanBeRetriedWithoutLosingOtherFavorites() = runBlocking {
        val fixture = editingFixture(FavoriteType.Avatar)
        try {
            val reads = fixture.listReads.value
            fixture.edit(FavoriteEditRequest(FavoriteType.Avatar, "avtr_two", null, "avatars2")).getOrThrow()
            awaitEditing { fixture.model.avatarList.value.size == 2 }
            assertEquals(reads, fixture.listReads.value)
            fixture.failNextAdd.value = true
            val move = FavoriteEditRequest(FavoriteType.Avatar, "avtr_one", "fvrt_old", "avatars2")
            assertTrue(fixture.edit(move).isFailure)
            awaitEditing { fixture.model.avatarList.value.map { it.id } == listOf("avtr_two") }
            fixture.edit(move).getOrThrow()
            awaitEditing { fixture.model.avatarList.value.size == 2 }
            awaitEditing { fixture.cache.load("usr_editor")?.favoritedAvatars?.map { it.id }?.toSet() == setOf("avtr_one", "avtr_two") }
        } finally { fixture.close() }
    }

    @Test
    fun friendMoveUpdatesBothGroupsAndRemovingTheFavoriteDoesNotRemoveTheFriendship() = runBlocking {
        val fixture = editingFixture(FavoriteType.Friend)
        try {
            val reads = fixture.listReads.value
            fixture.model.updateFriendDirectoryGroupOptions(FriendGroupOptions(fixture.group("group_0")))
            awaitEditing { fixture.model.friendDirectoryFriends.value.map { it.id } == listOf("usr_friend_member") }
            fixture.edit(FavoriteEditRequest(FavoriteType.Friend, "usr_friend_member", "fvrt_old", "group_1")).getOrThrow()
            awaitEditing { fixture.model.friendDirectoryFriends.value.isEmpty() }
            fixture.model.updateFriendDirectoryGroupOptions(FriendGroupOptions(fixture.group("group_1")))
            awaitEditing { fixture.model.friendDirectoryFriends.value.map { it.id } == listOf("usr_friend_member") }
            assertEquals(reads, fixture.listReads.value)
            fixture.edit(FavoriteEditRequest(FavoriteType.Friend, "usr_friend_member", "fvrt_new", null)).getOrThrow()
            awaitEditing { fixture.model.friendDirectoryFriends.value.isEmpty() }
            assertTrue("usr_friend_member" in fixture.friends.friendState.value)
        } finally { fixture.close() }
    }

}

private class EditingFixture(val type: FavoriteType) {
    private val firstGroup = when (type) {
        FavoriteType.World -> "worlds1"
        FavoriteType.Avatar -> "avatars1"
        FavoriteType.Friend -> "group_0"
    }
    private val secondGroup = when (type) {
        FavoriteType.World -> "worlds2"
        FavoriteType.Avatar -> "avatars2"
        FavoriteType.Friend -> "group_1"
    }
    private val targetId = when (type) {
        FavoriteType.World -> "wrld_one"
        FavoriteType.Avatar -> "avtr_one"
        FavoriteType.Friend -> "usr_friend_member"
    }
    val account = AccountDto(userId = "usr_editor", username = "editor", password = "password", authCookie = "auth")
    val json = Json { ignoreUnknownKeys = true }
    val records = MutableStateFlow(listOf(FavoriteData(targetId, "fvrt_old", listOf(firstGroup), type.value)))
    val deletes = atomic(0)
    val listReads = atomic(0)
    val failNextAdd = atomic(false)
    val unauthorizedNextAdd = atomic(false)
    val holdWorldList = atomic(false)
    val worldListStarted = CompletableDeferred<Unit>()
    val releaseWorldList = CompletableDeferred<Unit>()
    val cache = InMemoryFavoriteListCacheStore()
    val friendsCache = InMemoryFriendListCacheStore()
    val cacheManager = AccountCacheManager(
        friendsCache, InMemoryUserProfileCacheStore(), NoOpFriendActivityCacheStore,
        MeetupCardConfigDao(MapSettings()), MeetupCardAssetStore(FakeFileSystem(), "/assets".toPath()), cache,
    )
    val client = HttpClient(MockEngine) {
        engine { addHandler { request ->
            if (request.method == HttpMethod.Get && (request.url.parameters["offset"]?.toIntOrNull() ?: 0) > 0) {
                return@addHandler editingJson("[]")
            }
            when (val path = request.url.encodedPath) {
                "/auth/user/favoritelimits" -> editingJson("""{
                    "maxFavoriteGroups":{"avatar":1,"friend":1,"world":4},
                    "maxFavoritesPerGroup":{"avatar":100,"friend":100,"world":100},
                    "defaultMaxFavoriteGroups":1,"defaultMaxFavoritesPerGroup":100
                }""")
                "/auth/user" -> editingJson(editingCurrentUser(account))
                "/auth/user/friends" -> editingJson(json.encodeToString(
                    if (type == FavoriteType.Friend && request.url.parameters["offline"] == "false") listOf(editingFriend()) else emptyList(),
                ))
                "/avatars", "/worlds" -> editingJson("[]")
                "/avatars/favorites" -> {
                    listReads.incrementAndGet()
                    val tag = request.url.parameters["tag"]
                    editingJson(json.encodeToString(records.value.filter {
                        it.type == "avatar" && (tag == null || tag in it.tags)
                    }.map { AvatarData(id = it.favoriteId, name = it.favoriteId) }))
                }
                "/favorite/groups" -> editingJson(json.encodeToString(
                    if (request.url.parameters["type"] == type.value) listOf(group(firstGroup), group(secondGroup)) else emptyList(),
                ))
                "/favorites" -> if (request.method == HttpMethod.Post) {
                    when {
                        failNextAdd.compareAndSet(true, false) -> respond("failed", HttpStatusCode.InternalServerError)
                        unauthorizedNextAdd.compareAndSet(true, false) -> respond("expired", HttpStatusCode.Unauthorized)
                        else -> {
                            val body = json.decodeFromString<AddFavoriteRequest>((request.body as TextContent).text)
                            val favorite = FavoriteData(body.favoriteId, if (body.favoriteId == targetId) "fvrt_new" else "fvrt_${body.favoriteId}", body.tags, body.type)
                            records.value = records.value + favorite
                            editingJson(json.encodeToString(favorite))
                        }
                    }
                } else {
                    listReads.incrementAndGet()
                    editingJson(json.encodeToString(records.value.filter { it.type == request.url.parameters["type"] }))
                }
                "/worlds/favorites" -> {
                    listReads.incrementAndGet()
                    val snapshot = records.value.filter { it.type == "world" }.map { FavoritedWorld(
                        id = it.favoriteId, name = it.favoriteId, favoriteId = it.id, favoriteGroup = it.tags.single(),
                    ) }
                    if (holdWorldList.compareAndSet(true, false)) {
                        worldListStarted.complete(Unit)
                        releaseWorldList.await()
                    }
                    editingJson(json.encodeToString(snapshot))
                }
                else -> when {
                    path.startsWith("/favorites/") && request.method == HttpMethod.Delete -> {
                        deletes.incrementAndGet()
                        val id = path.substringAfterLast('/')
                        check(records.value.any { it.id == id }) { "Cannot delete the same record twice" }
                        records.value = records.value.filterNot { it.id == id }
                        editingJson("")
                    }
                    path.startsWith("/avatars/avtr_") -> editingJson(json.encodeToString(AvatarData(
                        id = path.substringAfterLast('/'), name = path.substringAfterLast('/'),
                    )))
                    path.startsWith("/worlds/wrld_") -> editingJson(json.encodeToString(FavoritedWorld(
                        id = path.substringAfterLast('/'), name = path.substringAfterLast('/'),
                        favoriteId = "", favoriteGroup = "",
                    ).toSearchWorldData()))
                    else -> error("Unexpected request: ${request.url}")
                }
            }
        } }
        install(ContentNegotiation) { json(json) }
    }
    val auth = AuthService(AuthApi(client), AccountDao(MapSettings(), InMemorySecureStorage()).also {
        it.saveAccountInfo(account)
    }, PersistentCookiesStorage(EmptyLogger()), cacheManager)
    val favoriteService = FavoriteService(FavoriteApi(client), FavoriteLocalDao(MapSettings()))
    val friends = FriendService(FriendsApi(client), auth, json, friendsCache, cacheManager, EmptyLogger())
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val model = FriendListPagerModel(
        UserProfileEnrichmentService(UsersApi(client), scope), friends, auth, favoriteService,
        WorldsApi(client), AvatarsApi(client), cache, cacheManager,
    )
    fun group(name: String) = FavoriteGroupData(
        id = name, ownerId = account.userId, type = type.value, visibility = "private",
        displayName = name, name = name, ownerDisplayName = "Editor", tags = emptyList(),
    )
    suspend fun edit(request: FavoriteEditRequest = FavoriteEditRequest(FavoriteType.World, "wrld_one", "fvrt_old", "worlds2")) =
        FavoriteEditService(auth, favoriteService).edit(assertNotNull(SharedFlowCentre.currentSession.value).token, request)

    suspend fun close() {
        releaseWorldList.complete(Unit)
        model.clearAndJoin()
        friends.dispose()
        favoriteService.dispose()
        auth.closeAndJoin()
        scope.cancel()
        SharedFlowCentre.emitLogout()
        client.close()
    }
}

private suspend fun editingFixture(type: FavoriteType = FavoriteType.World): EditingFixture {
    SharedFlowCentre.emitLogout()
    val fixture = EditingFixture(type)
    SharedFlowCentre.emitAuthenticated(fixture.account)
    fixture.model.syncSelectedTabIndex(when (type) {
        FavoriteType.World -> 1
        FavoriteType.Avatar -> 2
        FavoriteType.Friend -> 0
    })
    if (type == FavoriteType.Friend) fixture.model.activateFriendDirectory() else fixture.model.activateFavoritesPage()
    try {
        awaitEditing {
            when (type) {
                FavoriteType.World -> 1 !in fixture.model.refreshingTabs.value && fixture.model.worldList.value.size == 1
                FavoriteType.Avatar -> 2 !in fixture.model.refreshingTabs.value && fixture.model.avatarList.value.size == 1
                FavoriteType.Friend -> !fixture.model.directoryRefreshing.value && fixture.model.friendDirectoryFriends.value.size == 1
            }
        }
    } catch (error: Throwable) {
        fixture.close()
        throw error
    }
    return fixture
}

private suspend fun awaitEditing(predicate: suspend () -> Boolean) {
    withTimeout(5_000) { while (!predicate()) yield() }
}

private fun MockRequestHandleScope.editingJson(content: String) = respond(
    content, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"),
)

private fun editingCurrentUser(account: AccountDto) = """
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

private fun editingFriend() = FriendData(
    bio = null,
    currentAvatarImageUrl = "",
    currentAvatarThumbnailImageUrl = "",
    developerType = "none",
    displayName = "Favorite Friend",
    friendKey = "",
    id = "usr_friend_member",
    imageUrl = "",
    isFriend = true,
    lastLogin = "",
    lastPlatform = "web",
    location = "offline",
    profilePicOverride = "",
    status = UserStatus.Active,
    statusDescription = "",
    userIcon = "",
    pronouns = null,
)
