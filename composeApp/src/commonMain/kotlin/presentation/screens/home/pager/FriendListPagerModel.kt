package io.github.vrcmteam.vrcm.presentation.screens.home.pager

import androidx.compose.runtime.mutableStateMapOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.vrcmteam.vrcm.core.shared.AccountSessionToken
import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.network.api.attributes.FavoriteType
import io.github.vrcmteam.vrcm.network.api.attributes.FavoriteType.*
import io.github.vrcmteam.vrcm.network.api.attributes.LocationType
import io.github.vrcmteam.vrcm.network.api.attributes.UserStatus
import io.github.vrcmteam.vrcm.network.api.attributes.lastSeenAt
import io.github.vrcmteam.vrcm.network.api.favorite.data.FavoriteData
import io.github.vrcmteam.vrcm.network.api.favorite.data.FavoriteGroupData
import io.github.vrcmteam.vrcm.network.api.friends.date.FriendData
import io.github.vrcmteam.vrcm.network.api.avatars.AvatarsApi
import io.github.vrcmteam.vrcm.network.api.avatars.data.AvatarData
import io.github.vrcmteam.vrcm.network.api.files.resolveOriginalImageUrl
import io.github.vrcmteam.vrcm.network.api.users.UsersApi
import io.github.vrcmteam.vrcm.network.api.users.data.UserData
import io.github.vrcmteam.vrcm.network.api.worlds.WorldsApi
import io.github.vrcmteam.vrcm.network.api.worlds.data.FavoritedWorld
import io.github.vrcmteam.vrcm.network.api.worlds.data.WorldData
import io.github.vrcmteam.vrcm.network.supports.VRCApiException
import io.github.vrcmteam.vrcm.presentation.compoments.ToastText
import io.github.vrcmteam.vrcm.presentation.screens.user.data.UserProfileVo
import io.github.vrcmteam.vrcm.service.AuthService
import io.github.vrcmteam.vrcm.service.FavoriteService
import io.github.vrcmteam.vrcm.service.FriendService
import io.github.vrcmteam.vrcm.storage.AccountCacheManager
import io.github.vrcmteam.vrcm.storage.AccountCacheWriteToken
import io.github.vrcmteam.vrcm.storage.FavoriteListCacheStore
import io.github.vrcmteam.vrcm.storage.data.FavoritedWorldGroup
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 好友分组选项数据类
 */
data class FriendGroupOptions(
    val selectedGroup: FavoriteGroupData? = null
)

/**
 * 世界分组选项数据类
 */
data class WorldGroupOptions(
    val selectedGroup: FavoriteGroupData? = null
)

/**
 * 模型分组选项数据类
 */
data class AvatarGroupOptions(
    val selectedGroup: FavoriteGroupData? = null
)

class FriendListPagerModel(
    private val usersApi: UsersApi,
    private val friendService: FriendService,
    private val authService: AuthService,
    private val favoriteService: FavoriteService,
    private val worldsApi: WorldsApi,
    private val avatarsApi: AvatarsApi,
    private val favoriteListCacheStore: FavoriteListCacheStore,
    private val accountCacheManager: AccountCacheManager,
) : ViewModel() {

    // 当前选中的标签页索引
    private val _selectedTabIndex = MutableStateFlow(0)
    val selectedTabIndex: StateFlow<Int> = _selectedTabIndex.asStateFlow()

    /**
     * 好友组选项状态
     */
    private val _friendGroupOptions = MutableStateFlow(FriendGroupOptions())
    var friendGroupOptions = _friendGroupOptions.asStateFlow()

    /**
     * 世界组选项状态
     */
    private val _worldGroupOptions = MutableStateFlow(WorldGroupOptions())
    var worldGroupOptions = _worldGroupOptions.asStateFlow()

    private val _friendList = MutableStateFlow(friendService.friendMap.values.toList().sortedUserByStatus())
    val friendList: StateFlow<List<FriendData>> = _friendList.asStateFlow()

    private val _friendTotal = MutableStateFlow(friendService.friendMap.size)
    val friendTotal: StateFlow<Int> = _friendTotal.asStateFlow()

    // 缓存世界数据，以ID为键
    private val favoritedWorldMap: MutableMap<String, FavoritedWorld> = mutableStateMapOf()

    private val _worldList = MutableStateFlow(emptyList<WorldData>())
    val worldList: StateFlow<List<WorldData>> = _worldList.asStateFlow()

    private val _worldTotal = MutableStateFlow(0)
    val worldTotal: StateFlow<Int> = _worldTotal.asStateFlow()

    // 缓存模型数据，以ID为键
    private val favoritedAvatarMap: MutableMap<String, AvatarData> = mutableStateMapOf()

    private val _avatarList = MutableStateFlow(emptyList<AvatarData>())
    val avatarList: StateFlow<List<AvatarData>> = _avatarList.asStateFlow()

    private val _avatarTotal = MutableStateFlow(0)
    val avatarTotal: StateFlow<Int> = _avatarTotal.asStateFlow()

    /**
     * 模型组选项状态
     */
    private val _avatarGroupOptions = MutableStateFlow(AvatarGroupOptions())
    var avatarGroupOptions = _avatarGroupOptions.asStateFlow()

    /**
     * 获取好友组数据流
     */
    val friendFavoriteGroupsFlow: StateFlow<Map<FavoriteGroupData, List<FavoriteData>>> =
        favoriteService.favoritesByGroup(Friend)

    /**
     * 获取世界组数据流
     */
    val worldFavoriteGroupsFlow: StateFlow<Map<FavoriteGroupData, List<FavoriteData>>> =
        favoriteService.favoritesByGroup(World)

    /**
     * 获取模型组数据流
     */
    val avatarFavoriteGroupsFlow: StateFlow<Map<FavoriteGroupData, List<FavoriteData>>> =
        favoriteService.favoritesByGroup(Avatar)

    /**
     * 刷新状态,一次登录成功后只会自动刷新一次
     */
    private val _refreshingTabs = MutableStateFlow(setOf(0, 1, 2))
    val refreshingTabs = _refreshingTabs.asStateFlow()
    private val _refreshErrors = MutableStateFlow<Map<Int, String>>(emptyMap())
    val refreshErrors = _refreshErrors.asStateFlow()
    val isRefreshing: StateFlow<Boolean> = refreshingTabs
        .map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    private val searchTexts = MutableList(3) { "" }
    private val _searchText = MutableStateFlow("")
    val searchText: StateFlow<String> = _searchText.asStateFlow()

    private var friendFilterJob: Job? = null
    private val offlineStatusDescriptions = mutableMapOf<String, String>()

    init {
        // 监听登录状态,用于重新登录后更新刷新状态
        viewModelScope.launch {
            SharedFlowCentre.authed.collect {
                favoritedWorldMap.clear()
                favoritedAvatarMap.clear()
                offlineStatusDescriptions.clear()
                _refreshingTabs.value = setOf(0, 1, 2)
                _refreshErrors.value = emptyMap()
            }
        }
        viewModelScope.launch {
            friendService.friendState.collect { friends ->
                _friendTotal.value = friends.size
                findFriendList(searchTexts[0])
            }
        }
    }

    /**
     * 加载收藏组信息
     */
    private fun doRefreshCache(favoriteType: FavoriteType, showRefreshing: Boolean = true) =
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val tabIndex = favoriteType.tabIndex
                if (showRefreshing) _refreshingTabs.update { it + tabIndex }
                _refreshErrors.update { it - tabIndex }
                when (favoriteType) {
                    Friend -> {
                        // 加载收藏组信息，用于分组过滤
                        favoriteService.loadFavoriteByGroup(Friend)
                        doRefreshFriendList()
                    }

                    World -> {
                        val sessionToken = SharedFlowCentre.currentSession.value?.token ?: return@launch
                        restoreFavoriteListCache(World, sessionToken)
                        if (!SharedFlowCentre.isCurrentSession(sessionToken)) return@launch
                        // 加载收藏组信息，用于分组过滤
                        favoriteService.loadFavoriteByGroup(World)
                        if (!SharedFlowCentre.isCurrentSession(sessionToken)) return@launch
                        // 直接从API获取收藏世界列表
                        doRefreshWorldList(sessionToken)
                    }

                    Avatar -> {
                        val sessionToken = SharedFlowCentre.currentSession.value?.token ?: return@launch
                        restoreFavoriteListCache(Avatar, sessionToken)
                        if (!SharedFlowCentre.isCurrentSession(sessionToken)) return@launch
                        favoriteService.loadFavoriteByGroup(Avatar)
                        if (!SharedFlowCentre.isCurrentSession(sessionToken)) return@launch
                        doRefreshAvatarList(sessionToken)
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                _refreshErrors.update { it + (favoriteType.tabIndex to e.message.orEmpty()) }
                SharedFlowCentre.toastText.emit(ToastText.Error("加载收藏组信息失败: ${e.message}"))
            } finally {
                _refreshingTabs.update { it - favoriteType.tabIndex }
            }
        }

    private suspend fun restoreFavoriteListCache(
        favoriteType: FavoriteType,
        sessionToken: AccountSessionToken,
    ) {
        val cached = try {
            favoriteListCacheStore.load(sessionToken.userId)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            SharedFlowCentre.toastText.emit(ToastText.Error("读取收藏缓存失败: ${error.message}"))
            null
        } ?: return
        if (!SharedFlowCentre.isCurrentSession(sessionToken)) return

        when (favoriteType) {
            World -> {
                replaceFavoritedWorldCache(
                    cache = favoritedWorldMap,
                    worlds = cached.favoritedWorlds.flatMap { it.worlds },
                )
                _worldTotal.value = favoritedWorldMap.size
                findWorldList(searchTexts[1])
            }

            Avatar -> {
                favoritedAvatarMap.clear()
                favoritedAvatarMap.putAll(cached.favoritedAvatars.associateBy { it.id })
                _avatarTotal.value = favoritedAvatarMap.size
                findAvatarList(searchTexts[2])
            }

            Friend -> Unit
        }
    }

    private suspend fun doRefreshFriendList() {
        friendService.refreshFriendList()
    }

    /**
     * 设置当前选中的标签页索引
     */
    fun setSelectedTabIndex(index: Int) {
        if (_selectedTabIndex.value != index) {
            _selectedTabIndex.value = index
            _searchText.value = searchTexts[index]
            // 切换标签页时刷新对应数据
            refreshCurrentTabCacheData(showRefreshing = false)
        }
    }

    fun setSearchText(text: String) {
        _searchText.value = text
        searchTexts[_selectedTabIndex.value] = text
        // 当搜索文本改变时，根据当前标签页刷新对应数据
        refreshCurrentTabListData()
    }

    /**
     * 根据当前选中的标签页刷新对应数据
     */
    private fun refreshCurrentTabListData() {
        when (_selectedTabIndex.value) {
            0 -> {
                // 好友标签页
                findFriendList(searchTexts[0])
            }

            1 -> {
                // 世界标签页
                findWorldList(searchTexts[1])
            }

            2 -> {
                // 模型标签页
                findAvatarList(searchTexts[2])
            }
        }
    }

    /**
     * 根据当前选中的标签页刷新对应数据
     */
    fun refreshCurrentTabCacheData(showRefreshing: Boolean = true, tabIndex: Int = _selectedTabIndex.value) {
        when (tabIndex) {
            0 ->
                // 好友标签页
                doRefreshCache(Friend, showRefreshing)

            1 ->
                // 世界标签页
                doRefreshCache(World, showRefreshing)

            2 ->
                // 模型标签页
                doRefreshCache(Avatar, showRefreshing)
        }
    }

    /**
     * 更新好友组选项
     */
    fun updateFriendGroupOptions(options: FriendGroupOptions) {
        _friendGroupOptions.value = options
        refreshCurrentTabListData()
    }

    /**
     * 更新世界组选项
     */
    fun updateWorldGroupOptions(options: WorldGroupOptions) {
        _worldGroupOptions.value = options
        refreshCurrentTabListData()
    }

    /**
     * 更新模型组选项
     */
    fun updateAvatarGroupOptions(options: AvatarGroupOptions) {
        _avatarGroupOptions.value = options
        refreshCurrentTabListData()
    }

    /**
     * 基于搜索文本和当前选择的好友组过滤好友列表
     * 使用FriendService提供的方法
     */
    private fun findFriendList(name: String) {
        val favoriteIds = if (friendGroupOptions.value.selectedGroup != null) {
            // 获取选中好友组的favoriteId列表
            friendFavoriteGroupsFlow.value[friendGroupOptions.value.selectedGroup]
                ?.map { it.favoriteId }
                ?.toSet()
        } else {
            null
        }
        friendFilterJob?.cancel()
        friendFilterJob = viewModelScope.launch {
            _friendList.value = findFriendsByName(name, favoriteIds)
        }
    }

    private fun UserData.toFriendData(): FriendData {
        return  FriendData(
            id = id,
            displayName = displayName,
            status = status,
            lastLogin = lastLogin,
            lastActivity = lastActivity,
            lastPlatform = lastPlatform,
            bio = bio,
            bioLinks = bioLinks,
            currentAvatarImageUrl = currentAvatarImageUrl,
            currentAvatarThumbnailImageUrl = currentAvatarThumbnailImageUrl,
            currentAvatarTags = currentAvatarTags,
            developerType = developerType,
            tags = tags,
            isFriend = false,
            profilePicOverride = profilePicOverride,
            friendKey = "",
            imageUrl = profileImageUrl,
            location = LocationType.Offline.value,
            statusDescription = statusDescription,
            userIcon = userIcon,
            pronouns = pronouns,
        )
    }

    /**
     * 基于搜索文本和当前选择的世界组过滤世界列表
     *
     * @param favoriteIds 为 null 时返回所有好友， 代表没有选择好友组
     */
    suspend fun findFriendsByName(name: String, favoriteIds: Set<String>?): List<FriendData> {

        val unFriendData = favoriteIds?.filterNot { friendService.friendMap.contains(it) }?.map {
            withContext(Dispatchers.IO) { usersApi.fetchUser(it) }
                .toFriendData()
        } ?: emptyList()

        // 先按名称过滤
        val nameFilteredList = (friendService.friendMap.values + unFriendData).let { friends ->
            if (name.isEmpty()) friends
            else friends.filter { name.isEmpty() || it.displayName.lowercase().contains(name.lowercase()) }
        }

        // 再按好友组过滤
        val result =
            if (favoriteIds != null) nameFilteredList.filter { friend -> favoriteIds.contains(friend.id) }
            else nameFilteredList

        return enrichOfflineStatusDescriptions(result).sortedUserByStatus()
    }

    /**
     * The offline friends endpoint can omit the custom status description.
     * Fetch it from the user profile only for those rows, and cache the result
     * so presence updates do not turn the favorites page into a request storm.
     */
    private suspend fun enrichOfflineStatusDescriptions(friends: List<FriendData>): List<FriendData> {
        val candidates = friends.filter {
            it.status == UserStatus.Offline && it.statusDescription.isBlank()
        }
        if (candidates.isEmpty()) return friends

        val missing = candidates.filterNot { offlineStatusDescriptions.containsKey(it.id) }
        missing.chunked(8).forEach { batch ->
            val fetched = coroutineScope {
                batch.map { friend ->
                    async {
                        friend.id to runCatching {
                            withContext(Dispatchers.IO) {
                                usersApi.fetchUser(friend.id).statusDescription.trim()
                            }
                        }.getOrDefault("")
                    }
                }.awaitAll()
            }
            fetched.forEach { (id, description) ->
                offlineStatusDescriptions[id] = description
            }
        }

        return friends.map { friend ->
            val description = offlineStatusDescriptions[friend.id].orEmpty()
            if (friend.status == UserStatus.Offline &&
                friend.statusDescription.isBlank() &&
                description.isNotBlank()
            ) {
                friend.copy(statusDescription = description)
            } else {
                friend
            }
        }
    }

    // 先按状态排序, 如果是离线就再按最后登录时间排序, 再按名字排序
    /**
     * 查找收藏的世界列表
     * 只从缓存中筛选数据，不调用API
     */
    private fun findWorldList(name: String) {
        // 获取选中的世界分组
        val selectedGroup = worldGroupOptions.value.selectedGroup

        // 从缓存中获取世界数据并过滤
        val filteredWorlds = if (selectedGroup != null) {
            // 如果选中了特定分组，按分组名过滤
            val groupName = selectedGroup.name
            favoritedWorldMap.values
                .filter { world ->
                    world.favoriteGroup == groupName &&
                            (name.isEmpty() || world.name.lowercase().contains(name.lowercase())
                                    || (world.id == "???" && world.favoriteId.orEmpty().lowercase().contains(name.lowercase())))
                }
        } else {
            // 如果没有选择特定分组，仅按名称过滤
            favoritedWorldMap.values
                .filter { world ->
                    name.isEmpty() || world.name.lowercase().contains(name.lowercase())
                            || (world.id == "???" && world.favoriteId.orEmpty().lowercase().contains(name.lowercase()))
                }
        }.sortedWith(compareBy<FavoritedWorld> { it.id == "???" }.thenBy { it.name }) // 隐藏世界排到最后，再按名称排序

        // 将FavoritedWorld列表转换为WorldData列表
        _worldList.value = filteredWorlds.map { it.toSearchWorldData() }
    }

    /**
     * 查找收藏的模型列表
     * 只从缓存中筛选数据，不调用API
     */
    private fun findAvatarList(name: String) {
        val selectedGroup = avatarGroupOptions.value.selectedGroup

        // 从缓存中获取模型数据并过滤
        val filteredAvatars = if (selectedGroup != null) {
            val favoriteIds = avatarFavoriteGroupsFlow.value[selectedGroup]
                ?.map { it.favoriteId }?.toSet() ?: emptySet()
            favoritedAvatarMap.values
                .filter { avatar ->
                    favoriteIds.contains(avatar.id) &&
                            (name.isEmpty() || avatar.name.lowercase().contains(name.lowercase()) || avatar.id.lowercase().contains(name.lowercase()))
                }
        } else {
            favoritedAvatarMap.values
                .filter { avatar ->
                    name.isEmpty() || avatar.name.lowercase().contains(name.lowercase()) || avatar.id.lowercase().contains(name.lowercase())
                }
        }.sortedWith(compareBy<AvatarData> { it.releaseStatus == "hidden" }.thenBy { it.name })

        _avatarList.value = filteredAvatars
    }

    /**
     * 刷新收藏的模型列表
     */
    private suspend fun doRefreshAvatarList(sessionToken: AccountSessionToken) {
        val cacheWriteToken = accountCacheManager.captureWriteToken(sessionToken.userId)
        authService.reTryAuthCatching {
            // /avatars/favorites 默认只返回默认收藏组，必须按组 tag 分别请求。
            val remoteTags = remoteAvatarFavoriteTags(avatarFavoriteGroupsFlow.value).let { tags ->
                // 保留没有远程分组数据时的兼容行为（例如分组接口暂时不可用）。
                if (tags.isEmpty()) listOf<String?>(null) else tags
            }
            val remoteAvatars = remoteTags.flatMap { tag ->
                fetchFavoritedAvatars(tag)
            }

            val localAvatars = localFavoritedAvatarIds(avatarFavoriteGroupsFlow.value).mapNotNull { avatarId ->
                authService.reTryAuthCatching { avatarsApi.getAvatarById(avatarId) }.getOrNull()
            }
            mergeFavoritedAvatars(remoteAvatars, localAvatars)
        }.onSuccess { avatars ->
            if (!SharedFlowCentre.isCurrentSession(sessionToken)) return@onSuccess
            favoritedAvatarMap.clear()
            favoritedAvatarMap.putAll(avatars.associateBy { it.id })
            _avatarTotal.value = favoritedAvatarMap.size
            findAvatarList(searchTexts[2])
            persistFavoritedAvatars(cacheWriteToken, avatars)
        }.onFailure {
            if (it is CancellationException) throw it
            _refreshErrors.update { errors -> errors + (2 to it.message.orEmpty()) }
            SharedFlowCentre.toastText.emit(ToastText.Error("获取收藏模型失败: ${it.message}"))
        }
    }

    private suspend fun fetchFavoritedAvatars(tag: String?): List<AvatarData> {
        val avatars = mutableListOf<AvatarData>()
        val pageSize = 50
        var offset = 0
        do {
            val page = avatarsApi.getFavoritedAvatars(
                n = pageSize,
                offset = offset,
                tag = tag,
            )
            avatars.addAll(page)
            offset += pageSize
        } while (page.size >= pageSize)
        return avatars
    }

    /**
     * 刷新收藏的世界列表
     * 使用流式分页API获取全部收藏世界列表
     */
    private suspend fun doRefreshWorldList(sessionToken: AccountSessionToken) {
        val cacheWriteToken = accountCacheManager.captureWriteToken(sessionToken.userId)
        try {
            val localEntry = worldFavoriteGroupsFlow.value.entries.firstOrNull { (group, _) ->
                group.ownerId == "local" && group.type == World.value
            }
            val localFavoritedWorlds = localEntry?.let { (localGroup, favorites) ->
                favorites.map { favoriteData ->
                    withContext(Dispatchers.IO) { worldsApi.getWorldById(favoriteData.favoriteId) }
                        .toFavoritedWorldForLocal(localGroup.name, favoriteData.favoriteId)
                }
            }.orEmpty()

            // 每页流只包含当前页；收齐后再替换缓存，避免刷新期间留下失效记录。
            val remoteFavoritedWorlds = worldsApi.favoritedWorldsFlow()
                .retry { if (it is VRCApiException) authService.doReTryAuth() else false }
                .toList()
                .flatten()

            if (!SharedFlowCentre.isCurrentSession(sessionToken)) return
            val worlds = mergeFavoritedWorlds(remoteFavoritedWorlds, localFavoritedWorlds)
            replaceFavoritedWorldCache(cache = favoritedWorldMap, worlds = worlds)
            _worldTotal.value = favoritedWorldMap.size
            findWorldList(searchTexts[1])
            persistFavoritedWorlds(
                cacheWriteToken,
                groupFavoritedWorlds(worlds, worldFavoriteGroupsFlow.value),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _refreshErrors.update { errors -> errors + (1 to e.message.orEmpty()) }
            SharedFlowCentre.toastText.emit(ToastText.Error("获取收藏世界失败: ${e.message}"))
        }
    }

    private suspend fun persistFavoritedWorlds(
        token: AccountCacheWriteToken,
        worlds: List<FavoritedWorldGroup>,
    ) {
        try {
            accountCacheManager.saveFavoriteWorldsIfCurrent(token, worlds)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            SharedFlowCentre.toastText.emit(ToastText.Error("保存收藏世界缓存失败: ${error.message}"))
        }
    }

    private suspend fun persistFavoritedAvatars(
        token: AccountCacheWriteToken,
        avatars: List<AvatarData>,
    ) {
        try {
            accountCacheManager.saveFavoriteAvatarsIfCurrent(token, avatars)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            SharedFlowCentre.toastText.emit(ToastText.Error("保存收藏模型缓存失败: ${error.message}"))
        }
    }
}

private val FavoriteType.tabIndex: Int
    get() = when (this) {
        Friend -> 0
        World -> 1
        Avatar -> 2
    }

internal fun Iterable<FriendData>.sortedUserByStatus() = sortedByDescending {
    val isOffline = it.status == UserStatus.Offline
    val locationType = LocationType.fromValue(it.location)
    buildString {
        append(
            when {
                isOffline -> "0"
                locationType == LocationType.Offline || locationType == LocationType.Web -> "1"
                else -> "2"
            }
        )
        append('-')
        append(
            when (locationType) {
                LocationType.Instance -> "0"
                LocationType.Traveling -> "1"
                LocationType.Private -> "2"
                LocationType.Offline -> "3"
                LocationType.Web -> "3"
            }
        )
        append('-')
        append(it.location)
        append('-')
        append(if (isOffline) it.lastSeenAt().orEmpty() else "1")
        append('-')
        append(it.displayName)
    }
}

internal fun FavoritedWorld.toSearchWorldData(): WorldData = WorldData(
    id = id,
    favoriteId = favoriteId,
    name = name,
    authorId = authorId.orEmpty(),
    authorName = authorName.orEmpty(),
    capacity = capacity ?: 0,
    createdAt = createdAt.orEmpty(),
    description = description.orEmpty(),
    favorites = favorites ?: 0,
    featured = featured == true,
    heat = heat ?: 0,
    imageUrl = resolveOriginalImageUrl(imageUrl, thumbnailImageUrl).orEmpty(),
    labsPublicationDate = labsPublicationDate.orEmpty(),
    organization = organization.orEmpty(),
    popularity = popularity ?: 0,
    publicationDate = publicationDate.orEmpty(),
    recommendedCapacity = recommendedCapacity ?: 0,
    releaseStatus = releaseStatus.orEmpty(),
    tags = tags,
    thumbnailImageUrl = thumbnailImageUrl.orEmpty(),
    udonProducts = udonProducts,
    unityPackages = unityPackages,
    updatedAt = updatedAt.orEmpty(),
    version = version ?: 0,
    visits = visits ?: 0,
    namespace = null,
    privateOccupants = null,
    publicOccupants = null,
    instances = null,
    previewYoutubeId = previewYoutubeId,
)


private fun WorldData.toFavoritedWorldForLocal(localGroupName: String, wid: String): FavoritedWorld {
    return FavoritedWorld(
        authorId = this.authorId,
        authorName = this.authorName,
        id = this.id,
        name = this.name,
        description = this.description,
        capacity = this.capacity,
        recommendedCapacity = this.recommendedCapacity,
        releaseStatus = this.releaseStatus,
        imageUrl = this.imageUrl,
        thumbnailImageUrl = this.thumbnailImageUrl,
        organization = this.organization,
        version = this.version,
        favoriteId = "local|${World.value}|$wid",
        favoriteGroup = localGroupName,
        favorites = this.favorites,
        featured = this.featured,
        heat = this.heat,
        popularity = this.popularity,
        occupants = null,
        visits = this.visits,
        tags = this.tags,
        isSecure = true,
        createdAt = this.createdAt,
        updatedAt = this.updatedAt,
        publicationDate = this.publicationDate,
        labsPublicationDate = this.labsPublicationDate,
        unityPackages = this.unityPackages,
        udonProducts = this.udonProducts,
        urlList = null,
        defaultContentSettings = null,
        previewYoutubeId = this.previewYoutubeId
    )
}

internal fun mergeFavoritedAvatars(
    remoteAvatars: List<AvatarData>,
    localAvatars: List<AvatarData>,
): List<AvatarData> = (remoteAvatars + localAvatars).distinctBy { it.id }

internal fun favoritedWorldCacheKey(world: FavoritedWorld): String =
    if (world.id == "???") world.favoriteId else world.id

internal fun mergeFavoritedWorlds(
    remoteWorlds: List<FavoritedWorld>,
    localWorlds: List<FavoritedWorld>,
): List<FavoritedWorld> = (remoteWorlds + localWorlds).distinctBy(::favoritedWorldCacheKey)

internal fun groupFavoritedWorlds(
    worlds: List<FavoritedWorld>,
    favoriteGroups: Map<FavoriteGroupData, List<FavoriteData>>,
): List<FavoritedWorldGroup> {
    val worldsByGroup = worlds.groupBy { it.favoriteGroup }
    val groups = favoriteGroups.keys
        .filter { it.type == World.value }
        .distinctBy { it.name }
    val knownGroupKeys = groups.mapTo(mutableSetOf()) { it.name }
    return groups.map { group ->
        FavoritedWorldGroup(
            name = group.displayName,
            worlds = worldsByGroup[group.name].orEmpty(),
            groupKey = group.name,
        )
    } + worldsByGroup
        .filterKeys { it !in knownGroupKeys }
        .map { (groupKey, groupedWorlds) ->
            FavoritedWorldGroup(
                name = groupKey,
                worlds = groupedWorlds,
                groupKey = groupKey,
            )
        }
}

internal fun replaceFavoritedWorldCache(
    cache: MutableMap<String, FavoritedWorld>,
    worlds: List<FavoritedWorld>,
) {
    cache.clear()
    cache.putAll(worlds.associateBy(::favoritedWorldCacheKey))
}

internal fun localFavoritedAvatarIds(
    groups: Map<FavoriteGroupData, List<FavoriteData>>,
): List<String> = groups.entries
    .firstOrNull { (group, _) -> group.ownerId == "local" && group.type == Avatar.value }
    ?.value
    .orEmpty()
    .map { it.favoriteId }

internal fun remoteAvatarFavoriteTags(
    groups: Map<FavoriteGroupData, List<FavoriteData>>,
): List<String> = groups.keys
    .asSequence()
    .filter { group -> group.ownerId != "local" && group.type == Avatar.value }
    .map { it.name }
    .filter(String::isNotBlank)
    .distinct()
    .toList()
