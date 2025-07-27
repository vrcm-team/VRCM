package io.github.vrcmteam.vrcm.presentation.screens.home.pager

import androidx.compose.runtime.*
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.network.api.attributes.BlueprintType
import io.github.vrcmteam.vrcm.network.api.attributes.LocationType
import io.github.vrcmteam.vrcm.network.api.friends.date.FriendData
import io.github.vrcmteam.vrcm.network.api.groups.GroupsApi
import io.github.vrcmteam.vrcm.network.api.instances.InstancesApi
import io.github.vrcmteam.vrcm.network.api.users.UsersApi
import io.github.vrcmteam.vrcm.network.websocket.data.WebSocketEvent
import io.github.vrcmteam.vrcm.network.websocket.data.content.FriendActiveContent
import io.github.vrcmteam.vrcm.network.websocket.data.content.FriendLocationContent
import io.github.vrcmteam.vrcm.network.websocket.data.content.FriendOfflineContent
import io.github.vrcmteam.vrcm.network.websocket.data.type.FriendEvents
import io.github.vrcmteam.vrcm.presentation.compoments.ToastText
import io.github.vrcmteam.vrcm.presentation.extensions.onApiFailure
import io.github.vrcmteam.vrcm.presentation.screens.home.data.FriendLocation
import io.github.vrcmteam.vrcm.presentation.screens.home.data.HomeInstanceVo
import io.github.vrcmteam.vrcm.service.AuthService
import io.github.vrcmteam.vrcm.service.FriendService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

class FriendLocationPagerModel(
    private val friendService: FriendService,
    private val usersApi: UsersApi,
    private val groupsApi: GroupsApi,
    private val instancesApi: InstancesApi,
    private val authService: AuthService,
    private val json: Json,
) : ScreenModel {
    val friendLocationMap: MutableMap<LocationType, MutableList<FriendLocation>> =
        mutableStateMapOf()

    private val friendMap: MutableMap<String, FriendData> = mutableMapOf()

    private val updateMutex = Mutex()

    /**
     * 刷新状态,一次登录成功后只会自动刷新一次
     */
    var isRefreshing by mutableStateOf(true)
        private set

    init {
        // 监听websocket事件
        screenModelScope.launch {
            SharedFlowCentre.webSocket.collect { socketEvent ->
                onWebSocketEvent(socketEvent)
            }
        }
        // 监听登录状态,用于重新登录后更新刷新状态
        screenModelScope.launch {
            SharedFlowCentre.authed.collect {
                // 因为是第一个, 并且有移除不存在的元素的机制故无需clear
                friendLocationMap.clear()
                friendMap.clear()
                isRefreshing = true
            }
        }
    }

    private fun onWebSocketEvent(socketEvent: WebSocketEvent) {
        when (socketEvent.type) {
            FriendEvents.FriendActive.typeName -> {
                val friendActiveContent =
                    json.decodeFromString<FriendActiveContent>(socketEvent.content)
                update(listOf(friendActiveContent.toFriendData()))
            }

            // 当用户下线时根据id找到缓存的数据并根据缓存数据的location找到对应的实例房间,将此id的好友移除实例房间
            FriendEvents.FriendOffline.typeName -> {
                val friendOfflineContent =
                    json.decodeFromString<FriendOfflineContent>(socketEvent.content)
                friendMap[friendOfflineContent.userId]?.let { friend ->
                    removeFriend(friend.id)
                }
            }
            // 这两个事件接受的content都差不多，所以用一个
            // 当用户登录游戏时会先发送Online事件这时候location是traveling
            FriendEvents.FriendLocation.typeName, FriendEvents.FriendOnline.typeName -> {
                val friendLocationContent =
                    json.decodeFromString<FriendLocationContent>(socketEvent.content)
                update(listOf(friendLocationContent.toFriendData()))
            }

            else -> return
        }
    }


    suspend fun refreshFriendLocation() {
        // 只有在clear时设置true,用来触发刷新状态动画
        // 不然切换一个Page就触发动画
        isRefreshing = true
        friendLocationMap.clear()
        friendMap.clear()
        doRefreshFriendLocation()
        // 刷新后更新刷新状态, 防止页面重新加载时自动刷新

    }

    /**
     * 刷新好友位置
     * 未clear()的刷新会因为ws接口失效导致好友下线时未同步产生数据残留, 请让removeNotIncluded = true
     * @param removeNotIncluded 是否移除不在这一次刷新好友在线列表中的好友
     */
    suspend fun doRefreshFriendLocation(removeNotIncluded: Boolean = false) {
        // 防止再次更新时拉取到的与上次相同的instanceId导致item的key冲突
        val includedIdList: MutableList<String> = mutableListOf()
        screenModelScope.launch(Dispatchers.IO) {
            friendService.refreshFriendList(offline = false) { friends ->
                update(friends)
                if (removeNotIncluded) {
                    includedIdList.addAll(friends.map { it.id })
                }
            }
        }.join()
        if (removeNotIncluded) {
            friendMap.keys
                .filter { !includedIdList.contains(it) }
                .forEach { removeFriend(it) }
        }
        isRefreshing = false
    }

    private fun update(
        friends: List<FriendData>,
    ) = screenModelScope.launch(Dispatchers.Default) {
        runCatching {
            // 好友非正常退出时并挂黄灯时location会为private导致一直显示在private世界
            // 如果是WebSocketEvent更新的状态也无需担心,FriendActiveContent写死LocationType为Offline
            val currentUser = authService.currentUser(isRefresh = true)
            val currentFriendMap = friends.associateByTo(mutableMapOf()) { it.id }
            currentUser.activeFriends.forEach {
                currentFriendMap[it]?.let { activeFriend ->
                    if (activeFriend.location == LocationType.Private.value) {
                        currentFriendMap[it] = activeFriend.copy(location = LocationType.Offline.value)
                    }
                }
            }
            val friendCollection = currentFriendMap.values
            // 如果上次请求的数据中有这次的用户，则把该用户从上次的房间实例中列表中移除
            removePre(friendCollection)
            // 更新好友列表缓存
            friendMap += currentFriendMap

            val friendLocationInfoMap = friendCollection.associate { it.id to mutableStateOf(it) }
                .values.groupBy { LocationType.fromValue(it.value.location) }

            friendLocationInfoMap[LocationType.Offline]?.let { friends ->
                this@FriendLocationPagerModel.friendLocationMap.getOrPut(LocationType.Offline) {
                    mutableStateListOf(FriendLocation.Offline)
                }.first().friends.putAll(friends.associateBy { it.value.id })
            }
            friendLocationInfoMap[LocationType.Private]?.let { friends ->
                this@FriendLocationPagerModel.friendLocationMap.getOrPut(LocationType.Private) {
                    mutableStateListOf(FriendLocation.Private)
                }.first().friends.putAll(friends.associateBy { it.value.id })
            }
            friendLocationInfoMap[LocationType.Traveling]?.let { friends ->
                this@FriendLocationPagerModel.friendLocationMap.getOrPut(LocationType.Traveling) {
                    mutableStateListOf(FriendLocation.Traveling)
                }.first().friends.putAll(friends.associateBy { it.value.id })
            }

            val currentInstanceFriendLocations = this@FriendLocationPagerModel.friendLocationMap
                .getOrPut(LocationType.Instance, ::mutableStateListOf)
            val tempInstanceFriends = friendLocationInfoMap[LocationType.Instance] ?: emptyList()

            // 得到对应location的FriendLocation，将新的好友添加到friends
            tempInstanceFriends.groupBy { it.value.location }.forEach { locationFriendEntry ->
                // 通过location找到对应的FriendLocation，没有则创建一个并add到friendLocations
                // 找到相同的location的FriendLocation
                val location = locationFriendEntry.key
                val friendLocation = updateMutex.withLock(location) {
                    currentInstanceFriendLocations.find { location == it.location }
                        ?: FriendLocation(
                            location = location,
                            friends = mutableStateMapOf()
                        ).also { currentInstanceFriendLocations.add(it) }
                }
                // 通过location查询房间实例信息
                fetchInstants(location, friendLocation.instants.value) {
                    friendLocation.instants.value = it
                }
                friendLocation.friends.putAll(locationFriendEntry.value.associateBy { it.value.id })
            }
        }.onApiFailure("FriendLocation") {
            SharedFlowCentre.toastText.emit(ToastText.Error(it))
        }
    }


    private inline fun fetchInstants(
        location: String,
        oldInstants: HomeInstanceVo,
        crossinline updateInstants: (HomeInstanceVo) -> Unit
    ) {
        screenModelScope.launch(Dispatchers.IO) {
            authService.reTryAuthCatching {
                instancesApi.instanceByLocation(location)
            }.onFailure {
                SharedFlowCentre.toastText.emit(ToastText.Error(it.message.toString()))
            }.onSuccess { instance ->
                val instantsVo = HomeInstanceVo(instance)
                updateInstants(instantsVo)
                // owner不会变所以不用更新
                if (oldInstants.owner != null){
                    instantsVo.owner = oldInstants.owner
                    updateInstants(instantsVo)
                }else{
                    // 先刷新房间实例信息，提高用户体验，再更新房间实例的拥有者信息，不然会一直空白在加载
                    updateInstants(instantsVo)
                    fetchAndSetOwner(instance.ownerId, instantsVo)
                }
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

    /**
     * 如果上次请求的数据中有这次的用户并且在不同的location，则把该用户从上次的房间实例中列表中移除
     */
    private fun removePre(friends: Collection<FriendData>) {
        if (friendMap.isEmpty()) return
        friends.filter { friendMap.containsKey(it.id) && friendMap[it.id]!!.location != it.location }
            .map { it.id }
            .forEach(::removeFriend)
    }

    private fun removeFriend(friendId: String) {
        friendLocationMap.values.forEach { friendLocations ->
            friendLocations.filter { it.friends.containsKey(friendId) }
                .forEach { friendLocation ->
                    friendLocation.friends.remove(friendId)
                    friendMap.remove(friendId)
                    val locationType = LocationType.fromValue(friendLocation.location)
                    if (friendLocation.friends.isEmpty() && locationType == LocationType.Instance) {
                        friendLocations.remove(friendLocation)
                    }
                }
        }
    }

}