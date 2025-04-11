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
import io.github.vrcmteam.vrcm.network.api.friends.FriendsApi
import io.github.vrcmteam.vrcm.network.api.friends.date.FriendData
import io.github.vrcmteam.vrcm.network.api.worlds.WorldsApi
import io.github.vrcmteam.vrcm.network.api.worlds.data.FavoritedWorld
import io.github.vrcmteam.vrcm.network.api.worlds.data.WorldData
import io.github.vrcmteam.vrcm.network.supports.VRCApiException
import io.github.vrcmteam.vrcm.network.websocket.data.type.FriendEvents
import io.github.vrcmteam.vrcm.presentation.compoments.ToastText
import io.github.vrcmteam.vrcm.service.AuthService
import io.github.vrcmteam.vrcm.service.FavoriteService
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
    private val friendsApi: FriendsApi,
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

    // 缓存好友数据，以ID为键
    private val friendMap: MutableMap<String, FriendData> = mutableStateMapOf()

    private val _friendList = MutableStateFlow(emptyList<FriendData>())

    /**
     * 获取好友列表按在线状态最后登录时间与id排序
     * 使用sortedByDescending是因为匹配最后登录时间倒序
     */
    val friendList: StateFlow<List<FriendData>> = _friendList.asStateFlow()

    // 缓存世界数据，以ID为键
    private val favoritedWorldMap: MutableMap<String, FavoritedWorld> = mutableStateMapOf()

    private val _worldList = MutableStateFlow(emptyList<WorldData>())

    /**
     * 收藏的世界列表
     */
    val worldList: StateFlow<List<WorldData>> = _worldList.asStateFlow()

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
        // 监听WebSocket事件
        screenModelScope.launch {
            SharedFlowCentre.webSocket.collect { socketEvent ->
                when (socketEvent.type) {
                    FriendEvents.FriendActive.typeName,
                    FriendEvents.FriendOffline.typeName,
                    FriendEvents.FriendAdd.typeName,
                    FriendEvents.FriendDelete.typeName,
                    FriendEvents.FriendOnline.typeName,
                        -> {
                        // 这里监听到一个好友的状态变化就会全量刷新一次
                        // 只更新监听到的那个好友的状态的话怕数据不一致所以全量刷新(暂时)
                        doRefreshFriendList()
                    }

                    else -> return@collect
                }
            }
        }
        // 监听登录状态,用于重新登录后更新刷新状态
        screenModelScope.launch {
            SharedFlowCentre.authed.collect {
                friendMap.clear()
                favoritedWorldMap.clear()
                _isRefreshing.value = true
            }
        }
    }

    /**
     * 加载收藏组信息
     */
    private fun doRefreshCache(favoriteType: FavoriteType, showRefreshing: Boolean = true) = screenModelScope.launch(Dispatchers.IO) {
        try {
            if (showRefreshing) _isRefreshing.value = true
            when (favoriteType) {
                Friend -> {
                    // 不需要先刷新好友收藏列表
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
     * 只从缓存中筛选数据，不调用API
     */
    private fun findFriendList(name: String) {
        // 先按名称过滤
        val nameFilteredList = friendMap.values
            .filter { name.isEmpty() || it.displayName.lowercase().contains(name.lowercase()) }

        // 再按好友组过滤
        val result = if (friendGroupOptions.value.selectedGroup != null) {
            // 获取选中好友组的favoriteId列表
            val favoriteIds = friendFavoriteGroupsFlow.value[friendGroupOptions.value.selectedGroup]
                ?.map { it.favoriteId }
                ?.toSet()
                ?: emptySet()

            nameFilteredList.filter { friend ->
                favoriteIds.contains(friend.id)
            }
        } else {
            nameFilteredList
        }.sortedUserByStatus()

        _friendList.value = result
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

    // 先按状态排序, 如果是离线就再按最后登录时间排序, 再按名字排序
    private fun Iterable<FriendData>.sortedUserByStatus() = sortedByDescending {
        val isOffline = it.status == UserStatus.Offline
        (if (isOffline) "0" else "1") + (if (isOffline) it.lastLogin else "") + it.displayName
    }


    /**
     * 刷新收藏的世界列表
     * 使用流式分页API获取全部收藏世界列表
     */
    private suspend fun doRefreshWorldList() {
        screenModelScope.launch(Dispatchers.IO) {

            // 计数器，用于跟踪是否获取了任何数据
            var dataReceived = false
            
            worldsApi.favoritedWorldsFlow()
            .retry {
                // 如果是登录失效了就会重新登录并重试一次
                if (it is VRCApiException) authService.doReTryAuth() else false
            }
            .catch { e ->
                SharedFlowCentre.toastText.emit(ToastText.Error("获取收藏世界失败: ${e.message}"))
            }
            .onEach { favoritedWorlds ->
                dataReceived = true
                
                // 更新收到每页数据后都更新一下UI，保持响应性
                // TODO 暂时先过滤下isSecure, 不展示失效和私有的世界
                favoritedWorldMap.putAll(favoritedWorlds.filter { it.isSecure != false } .associateBy { it.id })
                findWorldList(_searchText.value)
            }
            .collect() // 收集所有页面的数据
            
            // 如果没有收到任何数据，清空列表
            if (!dataReceived) {
                favoritedWorldMap.clear()
                _worldList.value = emptyList()
            }
        }.join()
    }


    private suspend fun doRefreshFriendList() {
        screenModelScope.launch(Dispatchers.IO) {
            val count = friendsApi.allFriendsFlow()
                .retry(1) {
                    // 如果是登录失效了就会重新登录并重试一次
                    if (it is VRCApiException) authService.doReTryAuth() else false
                }.catch {
                    SharedFlowCentre.toastText.emit(ToastText.Error(it.message.toString()))
                }.onEach { friends ->
                    update(friends)
                }.count()
            if (count == 0) {
                friendMap.clear()
                _friendList.value = emptyList()
            }
        }.join()
    }

    private fun update(
        friends: List<FriendData>,
    ) = screenModelScope.launch(Dispatchers.Default) {
        runCatching {
            friendMap.putAll(friends.associateBy { it.id })
            findFriendList(searchText.value)
        }.onFailure {
            SharedFlowCentre.toastText.emit(ToastText.Error(it.message.toString()))
        }
    }
}