package io.github.vrcmteam.vrcm.presentation.screens.user

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import io.github.vrcmteam.vrcm.core.extensions.pretty
import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.network.api.attributes.BlueprintType
import io.github.vrcmteam.vrcm.network.api.attributes.LocationType
import io.github.vrcmteam.vrcm.network.api.attributes.NotificationType
import io.github.vrcmteam.vrcm.network.api.attributes.UserStatus
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
import io.github.vrcmteam.vrcm.presentation.screens.user.data.UserProfileVo
import io.github.vrcmteam.vrcm.service.AuthService
import io.github.vrcmteam.vrcm.service.FriendService
import io.ktor.client.call.*
import io.ktor.client.statement.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import org.koin.core.logger.Logger

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
) : ScreenModel {

    private val _userState = mutableStateOf(userProfileVO)
    val userState by _userState

    private val _friendLocation = mutableStateOf<FriendLocation?>(null)
    val friendLocation by _friendLocation

    private val _userJson = mutableStateOf("")
    val userJson by _userJson

    private val _userGroups = mutableStateOf<List<LimitedUserGroup>>(emptyList())
    val userGroups by _userGroups

    private val _mutualGroups = mutableStateOf<List<LimitedUserGroup>>(emptyList())
    val mutualGroups by _mutualGroups

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

    fun refreshUser(userId: String) =
        screenModelScope.launch(Dispatchers.IO) {
            _mutualGroups.value = emptyList()
            authService.reTryAuthCatching {
                usersApi.fetchUserResponse(userId)
            }.onFailure {
                handleError(it)
            }.onSuccess { response ->
                // 防止body序列化异常
                runCatching { UserProfileVo(response.body<UserData>()) }
                    .onSuccess {
                        _userState.value = it
                        computeFriendLocation(it.location)
                    }
                    .onFailure { handleError(it) }
                _userJson.value = response.bodyAsText().pretty()
                loadUserGroups(userId)
            }
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
        screenModelScope.launch(Dispatchers.IO) {
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
            refreshUser(userState.id)
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
        screenModelScope.async(Dispatchers.IO) {
            action()
                .onFailure {
                    handleError(it)
                }.onSuccess {
                    SharedFlowCentre.toastText.emit(ToastText.Success(message))
                    runCatching { _userState.value.id.also { refreshUser(it) } }
                        .onFailure { handleError(it) }
                }.isSuccess
        }.await()

    private suspend fun handleError(it: Throwable) {
        logger.error(it.message.toString())
        SharedFlowCentre.toastText.emit(ToastText.Error(it.message.toString()))
    }

    private suspend fun loadUserGroups(userId: String) {
        authService.reTryAuthCatching {
            usersApi.getUserGroups(userId)
        }.onSuccess { groups ->
            _userGroups.value = visibleUserGroups(groups, userState.isSelf)
            _mutualGroups.value = groups.filter { it.mutualGroup }
        }.onFailure {
            handleError(it)
        }
    }

    /**
     * 加载用户创建的世界列表
     * 先用搜索API快速展示卡片，再懒加载description
     */
    fun loadCreatedWorlds(userId: String) {
        if (_isLoadingWorlds.value) return
        if (_createdWorlds.value.isNotEmpty()) return
        _isLoadingWorlds.value = true
        screenModelScope.launch(Dispatchers.IO) {
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
                _createdWorlds.value = allWorlds
                _isLoadingWorlds.value = false

                // 逐批获取完整详情以填充description
                val worldIds = allWorlds.map { it.id }
                if (worldIds.isNotEmpty()) {
                    val fullWorlds = worldsApi.fetchWorldsByIds(worldIds)
                    val fullMap = fullWorlds.associateBy { it.id }
                    _createdWorlds.value = allWorlds.map { world ->
                        fullMap[world.id] ?: world
                    }
                }
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
        if (_createdAvatars.value.isNotEmpty()) return
        _isLoadingAvatars.value = true
        screenModelScope.launch(Dispatchers.IO) {
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
                    _createdAvatars.value = allAvatars.toList()
                }
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
        screenModelScope.launch(Dispatchers.IO) {
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
                        runCatching {
                            authService.reTryAuthCatching {
                                worldsApi.getFavoritedWorlds(
                                    ownerId = userId,
                                    userId = userId,
                                    tag = group.name,
                                    n = 100
                                )
                            }.getOrNull()?.takeIf { it.isNotEmpty() }?.let { group.displayName to it }
                        }.getOrNull()
                    }
                }
                _favoritedWorlds.value = deferreds.mapNotNull { it.await() }.toMutableList()
            } catch (_: Exception) {}
            _isLoadingFavoritedWorlds.value = false
        }
    }

    fun saveUserNote(note: String, successMessage: String) {
        screenModelScope.launch(Dispatchers.IO) {
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

    fun boop(userId: String, successMessage: String) {
        screenModelScope.launch(Dispatchers.IO) {
            authService.reTryAuthCatching {
                usersApi.boop(userId)
            }.onSuccess {
                SharedFlowCentre.toastText.emit(ToastText.Success(successMessage))
            }.onFailure {
                handleError(it)
            }
        }
    }

    fun inviteToMyInstance(
        userId: String,
        successMessage: String,
        notInInstanceMessage: String,
    ) {
        screenModelScope.launch(Dispatchers.IO) {
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
        if (location.isEmpty() || type != LocationType.Instance || _friendLocation.value != null) {
            return
        }

        val friendsInSameRoom: MutableMap<String, MutableState<FriendData>> =
            (mapOf(userState.id to userState.toFriendData()) + friendService.friendMap).values
                .filter { it.location == location }
                .associate { it.id to mutableStateOf(it) }
                .toMutableMap()
        val friendLocation = FriendLocation(
            location = location,
            friends = friendsInSameRoom
        )
        _friendLocation.value = friendLocation
        // Fetch instance details
        screenModelScope.launch(Dispatchers.IO) {
            authService.reTryAuthCatching {
                instancesApi.instanceByLocation(location)
            }.onSuccess { instance ->
                val homeInstanceVo = HomeInstanceVo(instance)
                friendLocation.instants.value = homeInstanceVo
                fetchAndSetOwner(instance.ownerId, homeInstanceVo)
            }.onFailure {
                handleError(it)
            }
        }
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
