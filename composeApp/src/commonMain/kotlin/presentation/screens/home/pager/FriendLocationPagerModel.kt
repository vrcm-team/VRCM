package io.github.vrcmteam.vrcm.presentation.screens.home.pager

import androidx.compose.runtime.*
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.network.api.attributes.LocationType
import io.github.vrcmteam.vrcm.network.api.friends.FriendsApi
import io.github.vrcmteam.vrcm.network.api.friends.date.FriendData
import io.github.vrcmteam.vrcm.network.api.instances.InstancesApi
import io.github.vrcmteam.vrcm.network.supports.VRCApiException
import io.github.vrcmteam.vrcm.network.websocket.data.content.FriendOfflineContent
import io.github.vrcmteam.vrcm.network.websocket.data.type.FriendEvents
import io.github.vrcmteam.vrcm.presentation.screens.home.data.FriendLocation
import io.github.vrcmteam.vrcm.presentation.screens.home.data.InstantsVO
import io.github.vrcmteam.vrcm.presentation.supports.AuthSupporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.retry
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import network.websocket.data.WebSocketEvent
import network.websocket.data.content.FriendActiveContent
import network.websocket.data.content.FriendLocationContent

class FriendLocationPagerModel(
    private val friendsApi: FriendsApi,
    private val instancesApi: InstancesApi,
    private val authSupporter: AuthSupporter,
    private val json: Json
): ScreenModel {
    val friendLocationMap: MutableMap<LocationType, MutableList<FriendLocation>> =
        mutableStateMapOf()

    private val friendMap: MutableMap<String, FriendData> = mutableMapOf()

    private val updateMutex = Mutex()

    var isRefreshing by mutableStateOf(true)
        private set


    init {
        // 监听websocket事件
        screenModelScope.launch {
            SharedFlowCentre.webSocket.collect { socketEvent ->
                onWebSocketEvent(socketEvent)
            }
        }
        // 监听登出事件, 清除刷新标记
        screenModelScope.launch {
            SharedFlowCentre.logout.collect {
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
                println("location:${friendLocationContent.location}")
                println("displayName:${friendLocationContent.user.displayName}")
                update(listOf(friendLocationContent.toFriendData()))
            }
            else -> return
        }
        println("type:${socketEvent.type}")
        println("content:${socketEvent.content}")
    }


    suspend fun refreshFriendLocation() {
        friendLocationMap.clear()
        friendMap.clear()
        doRefreshFriendLocation()
        isRefreshing = false
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
            friendsApi.friendsFlow()
                .retry(1) {
                    if (it is VRCApiException) authSupporter.doReTryAuth() else false
                }.catch {
                    SharedFlowCentre.error.emit(it.message.toString())
                }.collect { friends ->
                    update(friends)
                    if (removeNotIncluded){
                        includedIdList.addAll(friends.map { it.id })
                    }
                }
        }.join()
        if (removeNotIncluded){
            friendMap.keys.filter { !includedIdList.contains(it) }
                .forEach { removeFriend(it) }
        }
    }

    private fun update(
        friends: List<FriendData>,
    ) = screenModelScope.launch(Dispatchers.Default) {
        runCatching {
            // 好友非正常退出时并挂黄灯时location会为private导致一直显示在private世界
            // 如果是WebSocketEvent更新的状态也无需担心,FriendActiveContent些死LocationType为Offline
            val currentUser = authSupporter.currentUser(isRefresh = true).getOrThrow()
            val currentFriendMap = friends.associateByTo(mutableMapOf()) { it.id }
            currentUser.activeFriends.forEach {
                currentFriendMap[it]?.let { activeFriend ->
                    if (activeFriend.location == LocationType.Private.value){
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
                val friendLocation =  updateMutex.withLock(location) {
                    currentInstanceFriendLocations.find { location == it.location }
                        ?: FriendLocation(
                            location = location,
                            friends = mutableStateMapOf()
                        ).also { currentInstanceFriendLocations.add(it) }
                }
                // 通过location查询房间实例信息
                screenModelScope.launch(Dispatchers.IO) {
                    authSupporter.reTryAuth {
                        instancesApi.instanceByLocation(location)
                    }.onSuccess { instance ->
                        friendLocation.instants.value = InstantsVO(instance)
                    }.onFailure {
                        SharedFlowCentre.error.emit(it.message.toString())
                    }
                }
                friendLocation.friends.putAll(locationFriendEntry.value.associateBy { it.value.id })
            }
        }.onFailure {
            SharedFlowCentre.error.emit(it.message.toString())
        }
    }

    /**
     * 如果上次请求的数据中有这次的用户并且在不同的location，则把该用户从上次的房间实例中列表中移除
     */
    private fun removePre(friends: Collection<FriendData>) {
        if (friendMap.isEmpty()) return
        friends.filter {
            friendMap.containsKey(it.id) && friendMap[it.id]!!.location != it.location
        }.map { it.id }.forEach(::removeFriend)
    }

    private fun removeFriend(friendId: String) {
        friendLocationMap.values.forEach { friendLocations ->
            friendLocations.filter{ it.friends.containsKey(friendId) }
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