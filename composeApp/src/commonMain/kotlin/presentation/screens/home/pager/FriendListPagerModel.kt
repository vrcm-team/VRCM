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
import io.github.vrcmteam.vrcm.presentation.settings.locale.LocaleStrings
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

    private val _directoryRefreshing = MutableStateFlow(false)
    val directoryRefreshing = _directoryRefreshing.asStateFlow()
    private val _directoryRefreshFailed = MutableStateFlow(false)
    val directoryRefreshFailed = _directoryRefreshFailed.asStateFlow()
    private var directoryRefreshJob: Job? = null
    private val refreshJobsByTab = mutableMapOf<Int, Job>()
    private var activeSessionToken: AccountSessionToken? = null
    private var accountGeneration = 0L
    private var friendDirectoryActivated = false
    private var favoritesPageActivated = false
    private var favoriteLocale: LocaleStrings? = null

    val friendDirectoryFriends: StateFlow<List<FriendData>> = combine(
        _friendList,
        _searchText,
        _friendGroupOptions,
        friendFavoriteGroupsFlow,
    ) { friends, query, options, favoriteGroups ->
        val favoriteIds = options.selectedGroup?.let { selectedGroup ->
            favoriteGroups[selectedGroup]?.mapTo(mutableSetOf()) { it.favoriteId }.orEmpty()
        }
        val normalizedQuery = query.trim()
        friends.asSequence()
            .filter { favoriteIds == null || it.id in favoriteIds }
            .filter {
                normalizedQuery.isEmpty() ||
                    it.displayName.contains(normalizedQuery, ignoreCase = true)
            }
            .toList()
            .sortedUserByStatus()
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = emptyList(),
    )

    init {
        viewModelScope.launch {
            SharedFlowCentre.currentSession.collect { session ->
                val nextToken = session?.token
                if (activeSessionToken != nextToken) activateAccount(nextToken)
            }
        }
        viewModelScope.launch {
            friendService.friendState.collect { friends ->
                val token = activeSessionToken ?: return@collect
                if (SharedFlowCentre.currentSession.value?.token?.userId != token.userId) return@collect
                _friendTotal.value = friends.size
                findFriendList(searchTexts[0])
            }
        }
    }

    private fun activateAccount(sessionToken: AccountSessionToken?) {
        val userChanged = activeSessionToken?.userId != sessionToken?.userId
        accountGeneration++
        activeSessionToken = sessionToken
        refreshJobsByTab.values.forEach(Job::cancel)
        refreshJobsByTab.clear()
        directoryRefreshJob?.cancel()
        directoryRefreshJob = null
        friendFilterJob?.cancel()
        friendFilterJob = null
        if (userChanged) {
            favoritedWorldMap.clear()
            favoritedAvatarMap.clear()
            offlineStatusDescriptions.clear()
            _friendList.value = emptyList()
            _friendTotal.value = 0
            _worldList.value = emptyList()
            _worldTotal.value = 0
            _avatarList.value = emptyList()
            _avatarTotal.value = 0
            _friendGroupOptions.value = FriendGroupOptions()
            _worldGroupOptions.value = WorldGroupOptions()
            _avatarGroupOptions.value = AvatarGroupOptions()
            searchTexts.indices.forEach { searchTexts[it] = "" }
            _searchText.value = ""
        }
        _refreshErrors.value = emptyMap()
        _refreshingTabs.value = emptySet()
        _directoryRefreshing.value = sessionToken != null && friendDirectoryActivated
        _directoryRefreshFailed.value = false
        if (sessionToken != null && friendDirectoryActivated) refreshFriendDirectory()
        if (sessionToken != null && favoritesPageActivated) refreshFavoritesTabs()
    }

    fun updateFavoriteLocale(locale: LocaleStrings) {
        favoriteLocale = locale
    }

    /** Marks the Favorites page as active and refreshes its world and avatar tabs. */
    fun activateFavoritesPage() {
        favoritesPageActivated = true
        if (activeSessionToken != null) refreshFavoritesTabs()
    }

    /** Marks the friend directory as active and refreshes its friend-only data. */
    fun activateFriendDirectory() {
        friendDirectoryActivated = true
        refreshFriendDirectory()
    }

    private fun refreshFavoritesTabs() {
        (1..2).forEach { tabIndex ->
            refreshCurrentTabCacheData(tabIndex = tabIndex)
        }
    }

    /**
     * 加载收藏组信息
     */
    private fun doRefreshCache(favoriteType: FavoriteType, showRefreshing: Boolean = true): Job? {
        val sessionToken = activeSessionToken ?: return null
        val generation = accountGeneration
        val tabIndex = favoriteType.tabIndex
        refreshJobsByTab[tabIndex]?.takeIf { it.isActive }?.let { activeJob ->
            if (showRefreshing) {
                _refreshingTabs.update { tabs -> tabs + tabIndex }
                activeJob.invokeOnCompletion {
                    if (acceptsAccount(sessionToken, generation)) {
                        _refreshingTabs.update { tabs -> tabs - tabIndex }
                    }
                }
            }
            return activeJob
        }
        refreshJobsByTab.remove(tabIndex)?.cancel()
        return viewModelScope.launch(Dispatchers.IO) {
            try {
                if (!acceptsAccount(sessionToken, generation)) return@launch
                if (showRefreshing) _refreshingTabs.update { it + tabIndex }
                _refreshErrors.update { it - tabIndex }
                when (favoriteType) {
                    Friend -> {
                        recordFavoriteGroupFailure(
                            favoriteType = Friend,
                            result = favoriteService.loadFavoriteByGroup(Friend),
                            sessionToken = sessionToken,
                            generation = generation,
                        )
                        if (!acceptsAccount(sessionToken, generation)) return@launch
                        doRefreshFriendList()
                    }

                    World -> {
                        restoreFavoriteListCache(World, sessionToken, generation)
                        if (!acceptsAccount(sessionToken, generation)) return@launch
                        val groupsResult = favoriteService.loadFavoriteByGroup(World)
                        recordFavoriteGroupFailure(
                            favoriteType = World,
                            result = groupsResult,
                            sessionToken = sessionToken,
                            generation = generation,
                        )
                        if (groupsResult.isFailure) return@launch
                        if (!acceptsAccount(sessionToken, generation)) return@launch
                        doRefreshWorldList(sessionToken, generation)
                    }

                    Avatar -> {
                        restoreFavoriteListCache(Avatar, sessionToken, generation)
                        if (!acceptsAccount(sessionToken, generation)) return@launch
                        val groupsResult = favoriteService.loadFavoriteByGroup(Avatar)
                        recordFavoriteGroupFailure(
                            favoriteType = Avatar,
                            result = groupsResult,
                            sessionToken = sessionToken,
                            generation = generation,
                        )
                        if (groupsResult.isFailure) return@launch
                        if (!acceptsAccount(sessionToken, generation)) return@launch
                        doRefreshAvatarList(sessionToken, generation)
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                if (acceptsAccount(sessionToken, generation)) {
                    _refreshErrors.update { it + (tabIndex to e.message.orEmpty()) }
                    showFavoriteError(favoriteLocale?.favoritesGroupLoadFailed, e)
                }
            } finally {
                if (acceptsAccount(sessionToken, generation)) {
                    _refreshingTabs.update { it - tabIndex }
                }
            }
        }
            .also { refreshJobsByTab[tabIndex] = it }
    }

    private suspend fun recordFavoriteGroupFailure(
        favoriteType: FavoriteType,
        result: Result<Unit>,
        sessionToken: AccountSessionToken,
        generation: Long,
    ) {
        val error = result.exceptionOrNull() ?: return
        if (acceptsAccount(sessionToken, generation)) {
            _refreshErrors.update { it + (favoriteType.tabIndex to error.message.orEmpty()) }
            showFavoriteError(favoriteLocale?.favoritesGroupLoadFailed, error)
        }
    }

    private suspend fun restoreFavoriteListCache(
        favoriteType: FavoriteType,
        sessionToken: AccountSessionToken,
        generation: Long,
    ) {
        val cached = try {
            favoriteListCacheStore.load(sessionToken.userId)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            if (acceptsAccount(sessionToken, generation)) {
                showFavoriteError(favoriteLocale?.favoritesCacheReadFailed, error)
            }
            null
        } ?: return
        if (!acceptsAccount(sessionToken, generation)) return

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

    /** Refreshes only the friend snapshot and VRChat friend favorite groups. */
    fun refreshFriendDirectory() {
        if (!friendDirectoryActivated) return
        if (directoryRefreshJob?.isActive == true) return
        val sessionToken = activeSessionToken ?: return
        val generation = accountGeneration
        _directoryRefreshing.value = true
        _directoryRefreshFailed.value = false
        directoryRefreshJob = viewModelScope.launch(Dispatchers.IO) {
            if (!acceptsAccount(sessionToken, generation)) return@launch
            var failed = false
            try {
                if (favoriteService.loadFavoriteByGroup(Friend).isFailure) failed = true
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                failed = true
            }
            if (!acceptsAccount(sessionToken, generation)) return@launch
            try {
                if (!friendService.refreshFriendList()) failed = true
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                failed = true
            } finally {
                if (acceptsAccount(sessionToken, generation)) {
                    _directoryRefreshFailed.value = failed
                    _directoryRefreshing.value = false
                }
            }
        }
    }

    /**
     * 设置当前选中的标签页索引
     */
    fun setSelectedTabIndex(index: Int) {
        if (syncSelectedTabIndex(index)) {
            // 切换标签页时刷新对应数据
            refreshCurrentTabCacheData(showRefreshing = false)
        }
    }

    /** 同步恢复的标签页选择，不触发额外网络刷新。 */
    fun syncSelectedTabIndex(index: Int): Boolean {
        require(index in searchTexts.indices) { "Unsupported favorites tab index: $index" }
        if (_selectedTabIndex.value == index) return false
        _selectedTabIndex.value = index
        _searchText.value = searchTexts[index]
        return true
    }

    fun setSearchText(text: String) {
        _searchText.value = text
        searchTexts[_selectedTabIndex.value] = text
        // 当搜索文本改变时，根据当前标签页刷新对应数据
        refreshCurrentTabListData()
    }

    fun setFriendDirectorySearchText(text: String) {
        _searchText.value = text
        searchTexts[0] = text
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

    fun updateFriendDirectoryGroupOptions(options: FriendGroupOptions) {
        _friendGroupOptions.value = options
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
        val sessionToken = activeSessionToken ?: run {
            _friendList.value = emptyList()
            return
        }
        val generation = accountGeneration
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
            val friends = findFriendsByName(name, favoriteIds, sessionToken, generation)
            if (acceptsAccount(sessionToken, generation)) _friendList.value = friends
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
    private suspend fun findFriendsByName(
        name: String,
        favoriteIds: Set<String>?,
        sessionToken: AccountSessionToken,
        generation: Long,
    ): List<FriendData> {

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

        return enrichOfflineStatusDescriptions(result, sessionToken, generation).sortedUserByStatus()
    }

    /**
     * The offline friends endpoint can omit the custom status description.
     * Fetch it from the user profile only for those rows, and cache the result
     * so presence updates do not turn the favorites page into a request storm.
     */
    private suspend fun enrichOfflineStatusDescriptions(
        friends: List<FriendData>,
        sessionToken: AccountSessionToken,
        generation: Long,
    ): List<FriendData> {
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
            if (acceptsAccount(sessionToken, generation)) {
                fetched.forEach { (id, description) ->
                    offlineStatusDescriptions[id] = description
                }
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
    private suspend fun doRefreshAvatarList(
        sessionToken: AccountSessionToken,
        generation: Long,
    ) {
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
            if (!acceptsAccount(sessionToken, generation)) return@onSuccess
            favoritedAvatarMap.clear()
            favoritedAvatarMap.putAll(avatars.associateBy { it.id })
            _avatarTotal.value = favoritedAvatarMap.size
            findAvatarList(searchTexts[2])
            persistFavoritedAvatars(cacheWriteToken, sessionToken, generation, avatars)
        }.onFailure {
            if (it is CancellationException) throw it
            if (acceptsAccount(sessionToken, generation)) {
                _refreshErrors.update { errors -> errors + (2 to it.message.orEmpty()) }
                showFavoriteError(favoriteLocale?.favoriteAvatarsLoadFailed, it)
            }
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
    private suspend fun doRefreshWorldList(
        sessionToken: AccountSessionToken,
        generation: Long,
    ) {
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

            if (!acceptsAccount(sessionToken, generation)) return
            val worlds = mergeFavoritedWorlds(remoteFavoritedWorlds, localFavoritedWorlds)
            replaceFavoritedWorldCache(cache = favoritedWorldMap, worlds = worlds)
            _worldTotal.value = favoritedWorldMap.size
            findWorldList(searchTexts[1])
            persistFavoritedWorlds(
                cacheWriteToken,
                sessionToken,
                generation,
                groupFavoritedWorlds(worlds, worldFavoriteGroupsFlow.value),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (acceptsAccount(sessionToken, generation)) {
                _refreshErrors.update { errors -> errors + (1 to e.message.orEmpty()) }
                showFavoriteError(favoriteLocale?.favoriteWorldsLoadFailed, e)
            }
        }
    }

    private fun acceptsAccount(sessionToken: AccountSessionToken, generation: Long): Boolean =
        accountGeneration == generation && activeSessionToken == sessionToken &&
            SharedFlowCentre.isCurrentSession(sessionToken)

    private suspend fun persistFavoritedWorlds(
        token: AccountCacheWriteToken,
        sessionToken: AccountSessionToken,
        generation: Long,
        worlds: List<FavoritedWorldGroup>,
    ) {
        if (!acceptsAccount(sessionToken, generation)) return
        try {
            accountCacheManager.saveFavoriteWorldsIfCurrent(token, worlds)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            if (acceptsAccount(sessionToken, generation)) {
                showFavoriteError(favoriteLocale?.favoriteWorldCacheSaveFailed, error)
            }
        }
    }

    private suspend fun persistFavoritedAvatars(
        token: AccountCacheWriteToken,
        sessionToken: AccountSessionToken,
        generation: Long,
        avatars: List<AvatarData>,
    ) {
        if (!acceptsAccount(sessionToken, generation)) return
        try {
            accountCacheManager.saveFavoriteAvatarsIfCurrent(token, avatars)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            if (acceptsAccount(sessionToken, generation)) {
                showFavoriteError(favoriteLocale?.favoriteAvatarCacheSaveFailed, error)
            }
        }
    }

    private suspend fun showFavoriteError(message: String?, error: Throwable) {
        val localized = message ?: return
        SharedFlowCentre.toastText.emit(ToastText.Error("$localized: ${error.message.orEmpty()}"))
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
