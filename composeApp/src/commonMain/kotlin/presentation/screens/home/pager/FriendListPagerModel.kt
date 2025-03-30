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
    private val worldMap: MutableMap<String, WorldData> = mutableStateMapOf()

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
                worldMap.clear()
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
                    doRefreshFriendList()
                    favoriteService.loadFavoriteByGroup(Friend)
                }

                World -> {
                    // 需要先刷新收藏世界列表：因为数据来源收藏夹中的世界ID
                    favoriteService.loadFavoriteByGroup(World)
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
    fun refreshCurrentTabCacheData(showRefreshing: Boolean = true) {
        when (_selectedTabIndex.value) {
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

        // 获取对应分组中的收藏世界ID
        val favoriteWorldIds = if (selectedGroup != null) {
            // 如果选中了特定分组，获取该分组下的收藏
            worldFavoriteGroupsFlow.value[selectedGroup]?.map { it.favoriteId }?.toSet()
        } else {
            // 如果没有选中特定分组，获取所有收藏世界
            worldFavoriteGroupsFlow.value.values.flatten().map { it.favoriteId }.toSet()
        } ?: emptySet()

        // 如果没有收藏的世界，返回空列表
        if (favoriteWorldIds.isEmpty()) {
            _worldList.value = emptyList()
            return
        }

        // 从缓存中获取世界数据并过滤
        val filteredWorlds = worldMap.values
            .filter { world ->
                favoriteWorldIds.contains(world.id) &&
                        (name.isEmpty() || world.name.lowercase().contains(name.lowercase()))
            }
            .sortedBy { it.name } // 按名称排序

        _worldList.value = filteredWorlds
    }

    // 先按状态排序, 如果是离线就再按最后登录时间排序, 再按名字排序
    private fun Iterable<FriendData>.sortedUserByStatus() = sortedByDescending {
        val isOffline = it.status == UserStatus.Offline
        (if (isOffline) "0" else "1") + (if (isOffline) it.lastLogin else "") + it.displayName
    }


    /**
     * 刷新收藏的世界列表
     * 从API获取数据并更新缓存
     */
    private suspend fun doRefreshWorldList() {


//            worldFavoriteGroupsFlow.value.values.flatten().filter { it.favoriteId == "wrld_bb1908e9-bd8e-4357-9d50-3f3506c4a3df" }
        // 获取所有收藏的世界ID
        val allFavoriteWorldIds = worldFavoriteGroupsFlow.value.values
            .flatten()
            .map { it.favoriteId }
            .toSet()

        if (allFavoriteWorldIds.isEmpty()) {
            worldMap.clear()
            _worldList.value = emptyList()
            return
        }

        // 创建未缓存ID的集合
        val uncachedWorldIds = allFavoriteWorldIds.filter { worldId ->
            !worldMap.containsKey(worldId)
        }

        var loadedCount = 0
        val totalToLoad = uncachedWorldIds.size

        // 获取未缓存的世界数据
        screenModelScope.launch(Dispatchers.IO) {
            // 同时获取无缓存的世界数据
            for (worldId in uncachedWorldIds) {
                try {
                    val worldData = worldsApi.getWorldById(worldId)
                    worldMap[worldId] = worldData
                    loadedCount++

                    // 每加载几个世界后更新UI，提供渐进式加载体验
                    if (loadedCount % 3 == 0 || loadedCount == totalToLoad) {
                        findWorldList(_searchText.value)
                    }
                } catch (e: Exception) {
                    SharedFlowCentre.toastText.emit(ToastText.Error("${e.message}"))
                }
            }

            // 移除已不在收藏列表中的缓存数据
            worldMap.keys
                .filter { it !in allFavoriteWorldIds }
                .forEach { worldMap.remove(it) }

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