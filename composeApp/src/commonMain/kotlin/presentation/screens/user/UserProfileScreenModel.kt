package io.github.vrcmteam.vrcm.presentation.screens.user

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.vrcmteam.vrcm.core.extensions.pretty
import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.network.api.attributes.BlueprintType
import io.github.vrcmteam.vrcm.network.api.attributes.LocationType
import io.github.vrcmteam.vrcm.network.api.attributes.NotificationType
import io.github.vrcmteam.vrcm.network.api.attributes.UserStatus
import io.github.vrcmteam.vrcm.network.api.attributes.UserState
import io.github.vrcmteam.vrcm.network.api.avatars.AvatarsApi
import io.github.vrcmteam.vrcm.network.api.avatars.data.AvatarData
import io.github.vrcmteam.vrcm.network.api.favorite.FavoriteApi
import io.github.vrcmteam.vrcm.network.api.attributes.FavoriteType
import io.github.vrcmteam.vrcm.network.api.friends.date.FriendData
import io.github.vrcmteam.vrcm.network.api.groups.GroupsApi
import io.github.vrcmteam.vrcm.network.api.instances.InstancesApi
import io.github.vrcmteam.vrcm.network.api.invite.InviteApi
import io.github.vrcmteam.vrcm.network.api.notification.NotificationApi
import io.github.vrcmteam.vrcm.network.api.users.UsersApi
import io.github.vrcmteam.vrcm.network.api.users.data.UserData
import io.github.vrcmteam.vrcm.network.api.users.data.LimitedUserGroup
import io.github.vrcmteam.vrcm.network.api.users.data.UpdateUserInfoData
import io.github.vrcmteam.vrcm.network.api.worlds.WorldsApi
import io.github.vrcmteam.vrcm.network.api.worlds.data.FavoritedWorld
import io.github.vrcmteam.vrcm.network.api.worlds.data.WorldData
import io.github.vrcmteam.vrcm.presentation.compoments.ToastText
import io.github.vrcmteam.vrcm.presentation.screens.home.data.FriendLocation
import io.github.vrcmteam.vrcm.presentation.screens.home.data.HomeInstanceVo
import io.github.vrcmteam.vrcm.presentation.screens.home.pager.FriendLocationPagerModel
import io.github.vrcmteam.vrcm.presentation.screens.user.data.UserProfileVo
import io.github.vrcmteam.vrcm.service.AuthService
import io.github.vrcmteam.vrcm.service.FriendService
import io.github.vrcmteam.vrcm.service.FriendActivityEvent
import io.github.vrcmteam.vrcm.service.FriendActivityService
import io.github.vrcmteam.vrcm.service.FriendActivitySummary
import io.github.vrcmteam.vrcm.service.BoopResult
import io.github.vrcmteam.vrcm.service.BoopService
import io.github.vrcmteam.vrcm.storage.UserProfileCacheStore
import io.github.vrcmteam.vrcm.storage.data.FavoritedWorldGroup
import io.github.vrcmteam.vrcm.storage.data.UserProfileCache
import io.ktor.client.call.*
import io.ktor.client.statement.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.koin.core.logger.Logger

private enum class UserLoadState {
    Idle,
    Loading,
    Loaded,
}

internal class UserProfileLoadGate {
    private val mutex = Mutex()
    private var state = UserLoadState.Idle
    private var pendingForceRefresh = false

    suspend fun runLoad(
        forceRefresh: Boolean = false,
        load: suspend () -> Boolean,
    ): Boolean {
        if (!tryStart(forceRefresh)) return false

        var failure: Throwable? = null
        try {
            do {
                val succeeded = try {
                    load()
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Throwable) {
                    failure = failure ?: error
                    false
                }
            } while (finish(succeeded))
        } catch (error: CancellationException) {
            withContext(NonCancellable) { abort() }
            throw error
        }
        failure?.let { throw it }
        return true
    }

    private suspend fun tryStart(forceRefresh: Boolean): Boolean = mutex.withLock {
        when (state) {
            UserLoadState.Idle -> {
                state = UserLoadState.Loading
                true
            }
            UserLoadState.Loading -> {
                pendingForceRefresh = pendingForceRefresh || forceRefresh
                false
            }
            UserLoadState.Loaded -> {
                if (forceRefresh) {
                    state = UserLoadState.Loading
                    true
                } else {
                    false
                }
            }
        }
    }

    private suspend fun finish(succeeded: Boolean): Boolean = mutex.withLock {
        if (pendingForceRefresh) {
            pendingForceRefresh = false
            true
        } else {
            state = if (succeeded) UserLoadState.Loaded else UserLoadState.Idle
            false
        }
    }

    private suspend fun abort() = mutex.withLock {
        state = UserLoadState.Idle
        pendingForceRefresh = false
    }
}

internal class UserProfileLoadCoordinator {
    private val userGate = UserProfileLoadGate()
    private val groupsGate = UserProfileLoadGate()

    suspend fun runLoads(
        forceRefresh: Boolean = false,
        loadUser: suspend () -> Boolean,
        loadGroups: suspend () -> Boolean,
    ) {
        userGate.runLoad(forceRefresh, loadUser)
        groupsGate.runLoad(forceRefresh, loadGroups)
    }
}

internal fun resolveFriendLocation(
    userId: String,
    location: String?,
    locationsByUser: Map<String, FriendLocation>,
): FriendLocation? {
    if (location == null || LocationType.fromValue(location) != LocationType.Instance) return null
    return locationsByUser[userId]?.takeIf { it.location == location }
}

internal data class FavoritedWorldGroupLoad(
    val groupKey: String,
    val displayName: String,
    val result: Result<List<FavoritedWorld>>,
)

internal fun mergeFavoritedWorldGroups(
    cachedGroups: List<FavoritedWorldGroup>,
    loads: List<FavoritedWorldGroupLoad>,
): List<FavoritedWorldGroup> = loads.mapNotNull { load ->
    if (load.result.isSuccess) {
        FavoritedWorldGroup(
            name = load.displayName,
            worlds = load.result.getOrThrow(),
            groupKey = load.groupKey,
        )
    } else {
        cachedGroups.firstOrNull { cached ->
            cached.groupKey == load.groupKey || cached.name == load.displayName
        }?.copy(name = load.displayName, groupKey = load.groupKey)
    }
}

class UserProfileScreenModel(
    userProfileVO: UserProfileVo,
    private val authService: AuthService,
    private val usersApi: UsersApi,
    private val groupsApi: GroupsApi,
    private val friendService: FriendService,
    private val notificationApi: NotificationApi,
    private val logger: Logger,
    private val instancesApi: InstancesApi,
    private val worldsApi: WorldsApi,
    private val avatarsApi: AvatarsApi,
    private val favoriteApi: FavoriteApi,
    private val inviteApi: InviteApi,
    private val userProfileCacheStore: UserProfileCacheStore,
    private val friendLocationPagerModel: FriendLocationPagerModel,
    friendActivityService: FriendActivityService,
    private val boopService: BoopService,
) : ViewModel() {

    private val cacheOwnerUserId = authService.accountDto().userId
    private val _userState = mutableStateOf(userProfileVO.withSelfIdentity())
    val userState by _userState

    private val _friendLocation = mutableStateOf<FriendLocation?>(null)
    val friendLocation by _friendLocation

    private val _userJson = mutableStateOf("")
    val userJson by _userJson

    private val _userGroups = mutableStateOf<List<LimitedUserGroup>>(emptyList())
    val userGroups by _userGroups

    private val _mutualGroups = mutableStateOf<List<LimitedUserGroup>>(emptyList())
    val mutualGroups by _mutualGroups

    private val _friendActivitySummary = mutableStateOf<FriendActivitySummary?>(null)
    val friendActivitySummary by _friendActivitySummary

    private val _friendActivityEvents = mutableStateOf<List<FriendActivityEvent>>(emptyList())
    val friendActivityEvents by _friendActivityEvents

    private val _isBoopAllowed = mutableStateOf(authService.currentUserState.value?.isBoopingEnabled != false)
    val isBoopAllowed by _isBoopAllowed

    private val _createdWorlds = mutableStateOf<List<WorldData>>(emptyList())
    val createdWorlds by _createdWorlds

    private val _createdAvatars = mutableStateOf<List<AvatarData>>(emptyList())
    val createdAvatars by _createdAvatars

    private val _isLoadingWorlds = mutableStateOf(false)
    val isLoadingWorlds by _isLoadingWorlds

    private val _isLoadingAvatars = mutableStateOf(false)
    val isLoadingAvatars by _isLoadingAvatars

    private val _isLoadingFavoritedWorlds = mutableStateOf(false)

    private val _favoritedWorlds = mutableStateOf<List<Pair<String, List<FavoritedWorld>>>>(emptyList())
    val favoritedWorlds by _favoritedWorlds
    private var favoritedWorldGroups = emptyList<FavoritedWorldGroup>()

    // 保存滚动位置，用于导航返回时恢复
    var savedOuterScrollPosition: Int = 0
    var savedInnerScrollPosition: Int = 0

    private val loadCoordinator = UserProfileLoadCoordinator()
    private val cacheMutex = Mutex()
    private var cachedUserData: UserData? = null

    /**
     * The user endpoint does not reliably include the fields used by
     * UserProfileVo's UserData constructor to identify the current user.
     * The account id is the authoritative source for this screen.
     */
    private fun UserProfileVo.withSelfIdentity(): UserProfileVo =
        copy(isSelf = id == cacheOwnerUserId)

    init {
        viewModelScope.launch {
            authService.currentUserState.collect { currentUser ->
                _isBoopAllowed.value = currentUser?.isBoopingEnabled != false
            }
        }
        viewModelScope.launch {
            friendActivityService.observeSummary(userProfileVO.id).collect {
                _friendActivitySummary.value = it
            }
        }
        viewModelScope.launch {
            friendActivityService.observeEvents(userProfileVO.id).collect {
                _friendActivityEvents.value = it
            }
        }
        viewModelScope.launch {
            // Room 读取是挂起的，缓存会比首帧稍晚一点到；只在网络结果尚未覆盖时回填。
            userProfileCacheStore.load(cacheOwnerUserId, userProfileVO.id)
                ?.let(::restoreCachedProfile)
        }
        if (userProfileVO.id == cacheOwnerUserId) {
            viewModelScope.launch {
                authService.currentUserState.collect { currentUser ->
                    currentUser?.let { _userState.value = UserProfileVo(it).withSelfIdentity() }
                }
            }
        }
        viewModelScope.launch {
            combine(
                friendService.friendState,
                friendLocationPagerModel.friendLocationsByUser,
                friendService.currentUserLocation,
            ) { friends, locationsByUser, ownLocation ->
                val friend = friends[userProfileVO.id]
                friend to resolveFriendLocation(
                    userId = userProfileVO.id,
                    location = friend?.location
                        ?: ownLocation?.location.takeIf { userProfileVO.id == cacheOwnerUserId },
                    locationsByUser = locationsByUser,
                )
            }.collect { (friend, location) ->
                _friendLocation.value = location
                if (friend != null && userProfileVO.id != cacheOwnerUserId) {
                    _userState.value = _userState.value.withCurrentFriendPresence(friend)
                }
            }
        }
    }

    private fun restoreCachedProfile(cache: UserProfileCache) {
        cachedUserData = cache.user
        val cachedUser = cache.user.asCachedOffline()
        val profile = UserProfileVo(cachedUser)
            .withSelfIdentity()
            .let { restored ->
                friendService.friendState.value[cache.user.id]
                    ?.let(restored::withCurrentFriendPresence)
                    ?: restored
            }
        _userState.value = profile
        computeFriendLocation(profile.location)
        _userGroups.value = visibleUserGroups(cache.groups, profile.isSelf)
        _mutualGroups.value = cache.mutualGroups
        _createdWorlds.value = cache.createdWorlds
        _createdAvatars.value = cache.createdAvatars
        setFavoritedWorldGroups(cache.favoritedWorlds)
    }

    private suspend fun saveCache(user: UserData? = null) {
        cacheMutex.withLock {
            user?.let { cachedUserData = it }
            val cachedUser = cachedUserData ?: return
            userProfileCacheStore.save(
                ownerUserId = cacheOwnerUserId,
                userId = cachedUser.id,
                cache = UserProfileCache(
                    user = cachedUser,
                    groups = _userGroups.value,
                    mutualGroups = _mutualGroups.value,
                    createdWorlds = _createdWorlds.value,
                    createdAvatars = _createdAvatars.value,
                    favoritedWorlds = favoritedWorldGroups,
                ),
            )
        }
    }

    fun refreshUser(userId: String, forceRefresh: Boolean = false) =
        viewModelScope.launch(Dispatchers.IO) {
            loadCoordinator.runLoads(
                forceRefresh = forceRefresh,
                loadUser = {
                    var userLoaded = false
                    authService.reTryAuthCatching {
                        usersApi.fetchUserResponse(userId)
                    }.onFailure {
                        handleError(it)
                    }.onSuccess { response ->
                        // 防止body序列化异常
                        runCatching { response.body<UserData>() }
                            .onSuccess {
                                if (it.id == cacheOwnerUserId) {
                                    authService.applyOwnProfileRefresh(it)
                                }
                                val profile = UserProfileVo(it).withSelfIdentity()
                                if (_userState.value != profile) {
                                    _userState.value = profile
                                    computeFriendLocation(profile.location)
                                }
                                saveCache(it)
                                userLoaded = true
                            }
                            .onFailure { handleError(it) }
                        _userJson.value = response.bodyAsText().pretty()
                    }
                    userLoaded
                },
                loadGroups = { loadUserGroups(userId) },
            )
        }

    fun updateUserProfile(
        bio: String? = null,
        bioLinks: List<String>? = null,
        status: UserStatus? = null,
        statusDescription: String? = null,
        pronouns: String? = null,
        languages: List<String>? = null,
        successMessage: String = "Profile updated",
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            // 更新基本信息
            authService.reTryAuthCatching {
                usersApi.updateUserInfo(
                    userId = userState.id,
                    updateUserInfoData = UpdateUserInfoData(
                        bio = bio,
                        bioLinks = bioLinks,
                        status = status,
                        statusDescription = statusDescription,
                        pronouns = pronouns,
                    )
                )
            }.onFailure {
                handleError(it)
                return@launch
            }

            // 更新语言 tags
            if (languages != null) {
                val currentLangs = userState.tags
                    .filter { it.startsWith("language_") }
                    .map { it.removePrefix("language_") }
                val toAdd = languages.filter { it !in currentLangs }
                val toRemove = currentLangs.filter { it !in languages }

                if (toAdd.isNotEmpty()) {
                    authService.reTryAuthCatching {
                        usersApi.addTags(userState.id, toAdd.map { "language_$it" })
                    }.onFailure {
                        handleError(it)
                        return@launch
                    }
                }
                if (toRemove.isNotEmpty()) {
                    authService.reTryAuthCatching {
                        usersApi.removeTags(userState.id, toRemove.map { "language_$it" })
                    }.onFailure {
                        handleError(it)
                        return@launch
                    }
                }
            }

            SharedFlowCentre.toastText.emit(ToastText.Success(successMessage))
            refreshUser(userState.id, forceRefresh = true)
        }
    }

    suspend fun sendFriendRequest(userId: String, message: String): Boolean =
        friendAction(message) {
            friendService.sendFriendRequest(userId)
        }

    suspend fun deleteFriendRequest(userId: String, message: String): Boolean = friendAction(message) {
        friendService.deleteFriendRequest(userId)
    }

    suspend fun unfriend(userId: String, message: String): Boolean = friendAction(message) {
        friendService.unfriend(userId)
    }

    suspend fun acceptFriendRequest(userId: String, message: String) = friendAction(message) {
        // 看看要不要加载大于 100 条的通知
        // 先看没有hidden的, 如果没有再看hidden的 TODO:是不是要单独抽成一个独立方法
        return@friendAction authService.reTryAuthCatching {
            (notificationApi.fetchNotificationsV2(
                type = NotificationType.FriendRequest.value,
            ).firstOrNull { it.senderUserId == userId }
                ?: notificationApi.fetchNotificationsV2(
                    type = NotificationType.FriendRequest.value,
                    hidden = true
                ).firstOrNull { it.senderUserId == userId })
                ?.let {
                    notificationApi.acceptFriendRequest(it.id).isSuccess
                } ?: error("Not found notification")
        }
    }

    private suspend fun friendAction(message: String, action: suspend () -> Result<*>): Boolean =
        viewModelScope.async(Dispatchers.IO) {
            action()
                .onFailure {
                    handleError(it)
                }.onSuccess {
                    SharedFlowCentre.toastText.emit(ToastText.Success(message))
                    runCatching { _userState.value.id.also { refreshUser(it, forceRefresh = true) } }
                        .onFailure { handleError(it) }
                }.isSuccess
        }.await()

    private suspend fun handleError(it: Throwable) {
        logger.error(it.message.toString())
        SharedFlowCentre.toastText.emit(ToastText.Error(it.message.toString()))
    }

    private suspend fun loadUserGroups(userId: String): Boolean {
        var groupsLoaded = false
        authService.reTryAuthCatching {
            usersApi.getUserGroups(userId)
        }.onSuccess { groups ->
            val visibleGroups = visibleUserGroups(groups, userState.isSelf)
            val mutual = groups.filter { it.mutualGroup }
            if (_userGroups.value != visibleGroups) _userGroups.value = visibleGroups
            if (_mutualGroups.value != mutual) _mutualGroups.value = mutual
            saveCache()
            groupsLoaded = true
        }.onFailure {
            handleError(it)
        }
        return groupsLoaded
    }

    /**
     * 加载用户创建的世界列表
     * 先用搜索API快速展示卡片，再懒加载description
     */
    fun loadCreatedWorlds(userId: String) {
        if (_isLoadingWorlds.value) return
        _isLoadingWorlds.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val isSelf = userState.isSelf
                val allWorlds = mutableListOf<WorldData>()
                worldsApi.userWorldsFlow(
                    user = if (isSelf) "me" else null,
                    userId = if (isSelf) null else userId,
                    sort = "updated",
                    order = "descending",
                    releaseStatus = if (isSelf) "all" else "public",
                    n = 100,
                ).collect { worldList ->
                    allWorlds.addAll(worldList)
                }
                // 展示列表
                if (_createdWorlds.value != allWorlds) _createdWorlds.value = allWorlds
                _isLoadingWorlds.value = false

                // 逐批获取完整详情以填充description
                val worldIds = allWorlds.map { it.id }
                if (worldIds.isNotEmpty()) {
                    val fullWorlds = worldsApi.fetchWorldsByIds(worldIds)
                    val fullMap = fullWorlds.associateBy { it.id }
                    val completeWorlds = allWorlds.map { world ->
                        fullMap[world.id] ?: world
                    }
                    if (_createdWorlds.value != completeWorlds) _createdWorlds.value = completeWorlds
                }
                saveCache()
            } catch (e: Exception) {
                handleError(e)
                _isLoadingWorlds.value = false
            }
        }
    }

    /**
     * 加载用户创建的模型列表
     * 注意：VRChat API 仅支持 user=me 查询自己的模型，不支持查询他人模型
     */
    fun loadCreatedAvatars() {
        if (_isLoadingAvatars.value) return
        if (!userState.isSelf) return
        _isLoadingAvatars.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val allAvatars = mutableListOf<AvatarData>()
                avatarsApi.avatarsFlow(
                    user = "me",
                    sort = "updated",
                    order = "descending",
                    releaseStatus = "all",
                    n = 50,
                ).collect { avatarList ->
                    allAvatars.addAll(avatarList)
                    val avatars = allAvatars.toList()
                    if (_createdAvatars.value != avatars) _createdAvatars.value = avatars
                }
                saveCache()
            } catch (e: Exception) {
                handleError(e)
            }
            _isLoadingAvatars.value = false
        }
    }

    /**
     * 加载用户收藏的世界列表（按分组并行获取）
     */
    fun loadFavoritedWorlds(userId: String) {
        if (_isLoadingFavoritedWorlds.value) return
        _isLoadingFavoritedWorlds.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val groups = authService.reTryAuthCatching {
                    favoriteApi.getFavoriteGroupsByType(
                        favoriteType = FavoriteType.World,
                        ownerId = userId,
                        n = 100
                    )
                }.getOrNull() ?: run {
                    _isLoadingFavoritedWorlds.value = false
                    return@launch
                }

                val deferreds = groups.map { group ->
                    async {
                        FavoritedWorldGroupLoad(
                            groupKey = group.name,
                            displayName = group.displayName,
                            result = authService.reTryAuthCatching {
                                worldsApi.getFavoritedWorlds(
                                    ownerId = userId,
                                    userId = userId,
                                    tag = group.name,
                                    n = 100
                                )
                            },
                        )
                    }
                }
                val mergedGroups = mergeFavoritedWorldGroups(
                    cachedGroups = favoritedWorldGroups,
                    loads = deferreds.map { it.await() },
                )
                setFavoritedWorldGroups(mergedGroups)
                saveCache()
            } catch (_: Exception) {}
            _isLoadingFavoritedWorlds.value = false
        }
    }

    private fun setFavoritedWorldGroups(groups: List<FavoritedWorldGroup>) {
        favoritedWorldGroups = groups
        val worlds = groups.map { it.name to it.worlds }
        if (_favoritedWorlds.value != worlds) _favoritedWorlds.value = worlds
    }

    fun saveUserNote(note: String, successMessage: String) {
        viewModelScope.launch(Dispatchers.IO) {
            authService.reTryAuthCatching {
                usersApi.saveUserNote(userState.id, note)
            }.onFailure {
                handleError(it)
            }.onSuccess {
                _userState.value = _userState.value.copy(note = note)
                SharedFlowCentre.toastText.emit(ToastText.Success(successMessage))
            }
        }
    }

    suspend fun boop(
        userId: String,
        emojiId: String?,
        successMessage: String,
        cooldownMessage: String,
        disabledMessage: String,
    ): BoopResult = when (val result = boopService.send(userId, emojiId)) {
        BoopResult.Sent -> result.also {
            SharedFlowCentre.toastText.emit(ToastText.Success(successMessage))
        }
        BoopResult.Cooldown -> result.also {
            SharedFlowCentre.toastText.emit(ToastText.Info(cooldownMessage))
        }
        BoopResult.Disabled -> result.also {
            SharedFlowCentre.toastText.emit(ToastText.Error(disabledMessage))
        }
        is BoopResult.Failed -> result.also { handleError(it.error) }
        BoopResult.InFlight, BoopResult.SessionChanged -> result
    }

    fun inviteToMyInstance(
        userId: String,
        successMessage: String,
        notInInstanceMessage: String,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            authService.reTryAuthCatching {
                val instanceLocation = authService.currentUser().presence.instance
                require(instanceLocation.isNotBlank() && instanceLocation != "offline") {
                    notInInstanceMessage
                }
                inviteApi.inviteUser(userId, instanceLocation)
            }.onSuccess {
                SharedFlowCentre.toastText.emit(ToastText.Success(successMessage))
            }.onFailure {
                handleError(it)
            }
        }
    }

    fun computeFriendLocation(location: String) {
        val type = LocationType.fromValue(location)
        // 防止从用户详情页跳到世界页再点进非好友主页时 friendLocation 不刷新(按理来说看不到非好友位置)
        if (type == LocationType.Offline) {
            _friendLocation.value = null
            return
        }
        if (location.isEmpty() || type != LocationType.Instance) {
            _friendLocation.value = null
            return
        }
        if (_friendLocation.value != null) {
            return
        }

        _friendLocation.value = resolveFriendLocation(
            userId = userState.id,
            location = location,
            locationsByUser = friendLocationPagerModel.friendLocationsByUser.value,
        )
    }

    /**
     * 获取房间实例的拥有者名称
     *
     * @param instance 房间实例
     * @param instantsVo 房间实例的视图对象
     */
    private suspend fun fetchAndSetOwner(
        ownerId: String?,
        instantsVo: HomeInstanceVo,
    ) {
        val ownerId = ownerId ?: return
        val fetchOwner: suspend (String) -> HomeInstanceVo.Owner =
            when (BlueprintType.fromValue(ownerId)) {
                BlueprintType.User -> {
                    {
                        val user = usersApi.fetchUser(ownerId)
                        HomeInstanceVo.Owner(
                            id = user.id,
                            displayName = user.displayName,
                            type = BlueprintType.User
                        )
                    }
                }

                BlueprintType.Group -> {
                    {
                        val group = groupsApi.fetchGroup(ownerId)
                        HomeInstanceVo.Owner(
                            id = group.id,
                            displayName = group.name,
                            type = BlueprintType.Group
                        )

                    }
                }

                else -> return
            }
        authService.reTryAuthCatching {
            fetchOwner(ownerId)
        }.onSuccess {
            instantsVo.owner = it
        }.onFailure {
            SharedFlowCentre.toastText.emit(ToastText.Error(it.message.toString()))
        }
    }
}

internal fun visibleUserGroups(
    groups: List<LimitedUserGroup>,
    isSelf: Boolean,
): List<LimitedUserGroup> = if (isSelf) groups else groups.filterNot { it.mutualGroup }

private fun UserProfileVo.toFriendData() =
    FriendData(
        bio = bio,
        bioLinks = bioLinks,
        currentAvatarImageUrl = currentAvatarImageUrl,
        currentAvatarTags = currentAvatarTags,
        currentAvatarThumbnailImageUrl = currentAvatarThumbnailImageUrl,
        developerType = developerType,
        displayName = displayName,
        friendKey = "",
        id = id,
        imageUrl = profileImageUrl,
        isFriend = isFriend,
        lastLogin = lastLogin,
        lastPlatform = lastPlatform,
        location = location,
        profilePicOverride = profilePicOverride,
        status = status,
        statusDescription = statusDescription,
        userIcon = userIcon,
        pronouns = pronouns,
    )

internal fun UserData.asCachedOffline(): UserData = copy(
    state = UserState.Offline,
    status = UserStatus.Offline,
    location = LocationType.Offline.value,
    instanceId = "",
    worldId = "",
    travelingToInstance = null,
    travelingToLocation = null,
    travelingToWorld = null,
)

internal fun UserProfileVo.withCurrentFriendPresence(friend: FriendData): UserProfileVo = copy(
    status = friend.status,
    statusDescription = friend.statusDescription,
    location = friend.location,
    lastLogin = friend.lastLogin,
    lastPlatform = friend.lastPlatform,
)
