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
import io.github.vrcmteam.vrcm.presentation.compoments.ToastText
import io.github.vrcmteam.vrcm.presentation.extensions.onApiFailure
import io.github.vrcmteam.vrcm.presentation.screens.home.data.FriendLocation
import io.github.vrcmteam.vrcm.presentation.screens.home.data.HomeInstanceVo
import io.github.vrcmteam.vrcm.service.AuthService
import io.github.vrcmteam.vrcm.service.FriendService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class FriendLocationPagerModel(
    private val friendService: FriendService,
    private val usersApi: UsersApi,
    private val groupsApi: GroupsApi,
    private val instancesApi: InstancesApi,
    private val authService: AuthService,
) : ScreenModel {
    val friendLocationMap: MutableMap<LocationType, MutableList<FriendLocation>> =
        mutableStateMapOf()

    private val presenceStore = FriendLocationPresenceStore()
    private val updateMutex = Mutex()
    private val refreshMutex = Mutex()

    /**
     * 刷新状态,一次登录成功后只会自动刷新一次
     */
    var isRefreshing by mutableStateOf(true)
        private set

    init {
        screenModelScope.launch {
            friendService.friendUpdateFlow.collect { event ->
                val refreshRequired = updateMutex.withLock { presenceStore.apply(event) }
                if (refreshRequired) {
                    doRefreshFriendLocation(removeNotIncluded = true)
                } else {
                    syncFriendLocations()
                }
            }
        }
        // 监听登录状态,用于重新登录后更新刷新状态
        screenModelScope.launch {
            SharedFlowCentre.authed.collect {
                updateMutex.withLock {
                    friendLocationMap.clear()
                    presenceStore.clear()
                }
                isRefreshing = true
            }
        }
    }


    suspend fun refreshFriendLocation() {
        // 只有在clear时设置true,用来触发刷新状态动画
        // 不然切换一个Page就触发动画
        isRefreshing = true
        updateMutex.withLock {
            friendLocationMap.clear()
            presenceStore.clear()
        }
        doRefreshFriendLocation()
        // 刷新后更新刷新状态, 防止页面重新加载时自动刷新
    }

    /**
     * 刷新好友位置
     * 未clear()的刷新会因为ws接口失效导致好友下线时未同步产生数据残留, 请让removeNotIncluded = true
     * @param removeNotIncluded 是否移除不在这一次刷新好友在线列表中的好友
     */
    suspend fun doRefreshFriendLocation(removeNotIncluded: Boolean = false) = refreshMutex.withLock {
        val includedIds = mutableSetOf<String>()
        updateMutex.withLock { presenceStore.beginRefresh() }
        try {
            val currentUser = authService.currentUser(isRefresh = true)
            updateMutex.withLock { presenceStore.setActiveFriends(currentUser.activeFriends) }
        } catch (e: CancellationException) {
            updateMutex.withLock { presenceStore.cancelRefresh() }
            throw e
        } catch (_: Exception) {
            // Presence events keep this cache current if the account refresh is temporarily unavailable.
        }
        var completed = false
        try {
            completed = withContext(Dispatchers.IO) {
                friendService.refreshFriendList(offline = false) { friends ->
                    updateMutex.withLock {
                        presenceStore.addPage(friends)
                        includedIds.addAll(friends.map(FriendData::id))
                    }
                    syncFriendLocations()
                }
            }
            updateMutex.withLock {
                presenceStore.finishRefresh(includedIds, reconcile = removeNotIncluded && completed)
            }
        } finally {
            if (!completed) updateMutex.withLock { presenceStore.cancelRefresh() }
            syncFriendLocations()
            isRefreshing = false
        }
    }

    private suspend fun syncFriendLocations() = updateMutex.withLock {
        runCatching {
            val snapshot = presenceStore.snapshot()
            syncSimpleLocation(LocationType.Offline, snapshot.offline)
            syncSimpleLocation(LocationType.Private, snapshot.private)
            syncInstanceLocations(snapshot.instances)
        }.onApiFailure("FriendLocation") {
            SharedFlowCentre.toastText.emit(ToastText.Error(it))
        }
    }

    private fun syncSimpleLocation(type: LocationType, friends: List<FriendData>) {
        if (friends.isEmpty()) {
            friendLocationMap.remove(type)
            return
        }
        val location = friendLocationMap.getOrPut(type) {
            mutableStateListOf(
                if (type == LocationType.Offline) FriendLocation.Offline else FriendLocation.Private
            )
        }.first()
        syncFriends(location, FriendLocationGroup(friends))
    }

    private fun syncInstanceLocations(groups: Map<String, FriendLocationGroup>) {
        if (groups.isEmpty()) {
            friendLocationMap.remove(LocationType.Instance)
            return
        }
        val locations = friendLocationMap.getOrPut(LocationType.Instance, ::mutableStateListOf)
        locations.removeAll { it.location !in groups }
        groups.forEach { (locationId, group) ->
            val location = locations.find { it.location == locationId } ?: FriendLocation(
                location = locationId,
                friends = mutableStateMapOf(),
            ).also(locations::add)
            syncFriends(location, group)
            fetchInstants(locationId, location.instants.value) {
                location.instants.value = it
            }
        }
    }

    private fun syncFriends(location: FriendLocation, group: FriendLocationGroup) {
        val incoming = group.friends.associateBy(FriendData::id)
        location.friends.keys.filter { it !in incoming }.forEach(location.friends::remove)
        incoming.forEach { (friendId, friend) ->
            val state = location.friends[friendId]
            if (state == null) location.friends[friendId] = mutableStateOf(friend)
            else state.value = friend
        }
        location.travelingIds.value = group.travelingIds
    }


    private inline fun fetchInstants(
        location: String,
        oldInstants: HomeInstanceVo,
        crossinline updateInstants: (HomeInstanceVo) -> Unit
    ) {
        // 已加载过实例信息则跳过网络请求
        if (oldInstants.worldId.isNotEmpty()) return
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

}
