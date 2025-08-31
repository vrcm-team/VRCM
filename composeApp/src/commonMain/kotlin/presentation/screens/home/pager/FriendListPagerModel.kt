package io.github.vrcmteam.vrcm.presentation.screens.home.pager

import androidx.compose.runtime.mutableStateMapOf
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.network.api.attributes.FavoriteType
import io.github.vrcmteam.vrcm.network.api.attributes.FavoriteType.*
import io.github.vrcmteam.vrcm.network.api.attributes.UserStatus
import io.github.vrcmteam.vrcm.network.api.favorite.data.FavoriteData
import io.github.vrcmteam.vrcm.network.api.favorite.data.FavoriteGroupData
import io.github.vrcmteam.vrcm.network.api.friends.date.FriendData
import io.github.vrcmteam.vrcm.network.api.worlds.WorldsApi
import io.github.vrcmteam.vrcm.network.api.worlds.data.FavoritedWorld
import io.github.vrcmteam.vrcm.network.api.worlds.data.WorldData
import io.github.vrcmteam.vrcm.network.supports.VRCApiException
import io.github.vrcmteam.vrcm.presentation.compoments.ToastText
import io.github.vrcmteam.vrcm.service.AuthService
import io.github.vrcmteam.vrcm.service.FavoriteService
import io.github.vrcmteam.vrcm.service.FriendService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

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

class FriendListPagerModel(
    private val friendService: FriendService,
    private val authService: AuthService,
    private val favoriteService: FavoriteService,
    private val worldsApi: WorldsApi
) : ScreenModel {

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

    private val _friendTotal = MutableStateFlow(0)
    val friendTotal: StateFlow<Int> = _friendTotal.asStateFlow()

    // 缓存世界数据，以ID为键
    private val favoritedWorldMap: MutableMap<String, FavoritedWorld> = mutableStateMapOf()

    private val _worldList = MutableStateFlow(emptyList<WorldData>())
    val worldList: StateFlow<List<WorldData>> = _worldList.asStateFlow()

    private val _worldTotal = MutableStateFlow(0)
    val worldTotal: StateFlow<Int> = _worldTotal.asStateFlow()


    /**
     * 获取好友组数据流
     */
    val friendFavoriteGroupsFlow: StateFlow<Map<FavoriteGroupData, List<FavoriteData>>>
        get() = favoriteService.favoritesByGroup(Friend)

    /**
     * 获取世界组数据流
     */
    val worldFavoriteGroupsFlow: StateFlow<Map<FavoriteGroupData, List<FavoriteData>>> =
        favoriteService.favoritesByGroup(World)

    /**
     * 刷新状态,一次登录成功后只会自动刷新一次
     */
    private val _isRefreshing = MutableStateFlow(true)
    var isRefreshing = _isRefreshing.asStateFlow()

    // 搜索文本 - 两个页面共享
    private val _searchText = MutableStateFlow("")
    val searchText: StateFlow<String> = _searchText.asStateFlow()

    init {
        // 监听登录状态,用于重新登录后更新刷新状态
        screenModelScope.launch {
            SharedFlowCentre.authed.collect {
                favoritedWorldMap.clear()
                _isRefreshing.value = true
            }
        }
    }

    /**
     * 加载收藏组信息
     */
    private fun doRefreshCache(favoriteType: FavoriteType, showRefreshing: Boolean = true) =
        screenModelScope.launch(Dispatchers.IO) {
            try {
                if (showRefreshing) _isRefreshing.value = true
                when (favoriteType) {
                    Friend -> {
                        // 加载收藏组信息，用于分组过滤
                        favoriteService.loadFavoriteByGroup(Friend)
                        doRefreshFriendList()
                    }

                    World -> {
                        // 加载收藏组信息，用于分组过滤
                        favoriteService.loadFavoriteByGroup(World)
                        // 直接从API获取收藏世界列表
                        doRefreshWorldList()
                    }

                    Avatar -> TODO()
                }
            } catch (e: Exception) {
                SharedFlowCentre.toastText.emit(ToastText.Error("加载收藏组信息失败: ${e.message}"))
            } finally {
                _isRefreshing.value = false
            }
        }

    private suspend fun doRefreshFriendList() {
        friendService.refreshFriendList {
            _friendTotal.value = friendService.friendMap.size
            findFriendList(_searchText.value)
        }
    }

    /**
     * 设置当前选中的标签页索引
     */
    fun setSelectedTabIndex(index: Int) {
        if (_selectedTabIndex.value != index) {
            _selectedTabIndex.value = index
            // 切换标签页时刷新对应数据
            refreshCurrentTabCacheData(showRefreshing = false)
        }
    }

    fun setSearchText(text: String) {
        _searchText.value = text
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
                findFriendList(_searchText.value)
            }

            1 -> {
                // 世界标签页
                findWorldList(_searchText.value)
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
     * 基于搜索文本和当前选择的好友组过滤好友列表
     * 使用FriendService提供的方法
     */
    private fun findFriendList(name: String) {
        val favoriteIds = if (friendGroupOptions.value.selectedGroup != null) {
            // 获取选中好友组的favoriteId列表
            friendFavoriteGroupsFlow.value[friendGroupOptions.value.selectedGroup]
                ?.map { it.favoriteId }
                ?.toSet() ?: emptySet()
        } else {
            emptySet()
        }

        _friendList.value = findFriendsByName(name, favoriteIds)
    }

    fun findFriendsByName(name: String, favoriteIds: Set<String>): List<FriendData> {
        // 先按名称过滤
        val nameFilteredList = friendService.friendMap.values.let { friends ->
            if (name.isEmpty()) friends
            else friends.filter { name.isEmpty() || it.displayName.lowercase().contains(name.lowercase()) }
        }


        // 再按好友组过滤
        val result =
            if (favoriteIds.isNotEmpty()) nameFilteredList.filter { friend -> favoriteIds.contains(friend.id) }
            else nameFilteredList

        return result.sortedUserByStatus()
    }

    // 先按状态排序, 如果是离线就再按最后登录时间排序, 再按名字排序
    private fun Iterable<FriendData>.sortedUserByStatus() = sortedByDescending {
        val isOffline = it.status == UserStatus.Offline
        (if (isOffline) "0" else "1") + (if (isOffline) it.lastLogin else "") + it.displayName
    }

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
                            (name.isEmpty() || world.name.lowercase().contains(name.lowercase()))
                }
        } else {
            // 如果没有选择特定分组，仅按名称过滤
            favoritedWorldMap.values
                .filter { world ->
                    name.isEmpty() || world.name.lowercase().contains(name.lowercase())
                }
        }.sortedBy { it.name } // 按名称排序

        // 将FavoritedWorld列表转换为WorldData列表
        _worldList.value = filteredWorlds.map { world ->
            WorldData(
                id = world.id,
                name = world.name,
                authorId = world.authorId.orEmpty(),
                authorName = world.authorName.orEmpty(),
                capacity = world.capacity ?: 0,
                createdAt = world.createdAt.orEmpty(),
                description = world.description.orEmpty(),
                favorites = world.favorites ?: 0,
                featured = world.featured == true,
                heat = world.heat ?: 0,
                imageUrl = world.imageUrl.orEmpty(),
                labsPublicationDate = world.labsPublicationDate.orEmpty(),
                organization = world.organization.orEmpty(),
                popularity = world.popularity ?: 0,
                publicationDate = world.publicationDate.orEmpty(),
                recommendedCapacity = world.recommendedCapacity ?: 0,
                releaseStatus = world.releaseStatus.orEmpty(),
                tags = world.tags,
                thumbnailImageUrl = world.thumbnailImageUrl.orEmpty(),
                udonProducts = world.udonProducts,
                unityPackages = world.unityPackages,
                updatedAt = world.updatedAt.orEmpty(),
                version = world.version ?: 0,
                visits = world.visits ?: 0,
                // 设置其他可能需要的字段
                namespace = null,
                privateOccupants = null,
                publicOccupants = null,
                instances = null,
                previewYoutubeId = world.previewYoutubeId
            )
        }
    }

    /**
     * 刷新收藏的世界列表
     * 使用流式分页API获取全部收藏世界列表
     */
    private fun doRefreshWorldList() = screenModelScope.launch(Dispatchers.IO) {

        // 1) 获取本地收藏世界列表
        val localFavoritedWorlds = worldFavoriteGroupsFlow.mapNotNull {
            val localEntry = it.entries.firstOrNull { (group, _) ->
                group.ownerId == "local" && group.type == World.value
            } ?: return@mapNotNull null
            val localGroup = localEntry.key
            localEntry.value.map { favoriteData ->
                worldsApi.getWorldById(favoriteData.favoriteId)
                    .toFavoritedWorldForLocal(localGroup.name, favoriteData.favoriteId)
            }
        }

        // 2) 订阅服务器端的收藏世界流
        val remoteFavoritedWorlds = worldsApi.favoritedWorldsFlow()
            // 如果是登录失效了就会重新登录并重试一次
            .retry { if (it is VRCApiException) authService.doReTryAuth() else false }

        // 3) 合并数据
        var dataReceived = false

        combine(localFavoritedWorlds, remoteFavoritedWorlds) { favoritedWorlds, localWorlds ->
            localWorlds + favoritedWorlds
        }.catch { e ->
            SharedFlowCentre.toastText.emit(ToastText.Error("获取收藏世界失败: ${e.message}"))
        }.collect { favoritedWorlds ->
            dataReceived = true
            // 更新缓存，过滤掉不可见/非公开等情况
            favoritedWorldMap.putAll(
                favoritedWorlds.filter { it.isSecure != false }.associateBy { it.id }
            )
            _worldTotal.value = favoritedWorldMap.size
            findWorldList(_searchText.value)
        }

        // 如果没有收到数据，清空缓存
        if (!dataReceived) {
            favoritedWorldMap.clear()
        }
    }
}


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
