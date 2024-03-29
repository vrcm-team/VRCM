package io.github.vrcmteam.vrcm.presentation.screens.home.tab

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import io.github.vrcmteam.vrcm.network.api.attributes.LocationType
import io.github.vrcmteam.vrcm.network.api.friends.FriendsApi
import io.github.vrcmteam.vrcm.network.api.friends.date.FriendData
import io.github.vrcmteam.vrcm.network.api.instances.InstancesApi
import io.github.vrcmteam.vrcm.network.supports.VRCApiException
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

class FriendLocationTabModel(
    private val onFailureCallback:  (String) -> Unit,
    private val friendsApi: FriendsApi,
    private val instancesApi: InstancesApi,
    private val authSupporter: AuthSupporter,
): ScreenModel {
    val friendLocationMap: MutableMap<LocationType, MutableList<FriendLocation>> =
        mutableStateMapOf()

    private val friendMap: MutableMap<String, FriendData> = mutableMapOf()

    private val updateMutex = Mutex()

    suspend fun refreshFriendLocation() {
        this.friendLocationMap.clear()
        this.friendMap.clear()
        // 多次更新时加把锁
        // 防止再次更新时拉取到的与上次相同的instanceId导致item的key冲突
        screenModelScope.launch(Dispatchers.IO) {
            friendsApi.friendsFlow()
                .retry(1) {
                    if (it is VRCApiException) authSupporter.doReTryAuth() else false
                }.catch {
                    onFailureCallback(it.message.toString())
                }.collect { friends ->
                    updateMutex.withLock(friendLocationMap) { update(friends) }
                }
        }.join()
    }

    private fun update(
        friends: List<FriendData>,
    ) = screenModelScope.launch(Dispatchers.Default) {
        runCatching {
            // 如果上次请求的数据中有这次的用户，则把该用户从上次的房间实例中列表中移除
            if (friendMap.isNotEmpty()) {
                friends.filter { friendMap.containsKey(it.id) }.forEach{ friend->
                    val locationType = LocationType.fromValue(friend.location)
                    friendLocationMap[locationType]
                        ?.find { it.location == friend.location }
                        ?.friends?.removeAll { it.value.id == friend.id }
                }
            }

            friendMap += friends.associateBy { it.id }

            val friendLocationInfoMap = friends.associate { it.id to mutableStateOf(it) }
                .values.groupBy { LocationType.fromValue(it.value.location) }

            friendLocationInfoMap[LocationType.Offline]?.let { friends ->
                this@FriendLocationTabModel.friendLocationMap.getOrPut(LocationType.Offline) {
                    mutableStateListOf(FriendLocation.Offline)
                }.first().friends.addAll(friends)
            }
            friendLocationInfoMap[LocationType.Private]?.let { friends ->
                this@FriendLocationTabModel.friendLocationMap.getOrPut(LocationType.Private) {
                    mutableStateListOf(FriendLocation.Private)
                }.first().friends.addAll(friends)
            }
            friendLocationInfoMap[LocationType.Traveling]?.let { friends ->
                this@FriendLocationTabModel.friendLocationMap.getOrPut(LocationType.Traveling) {
                    mutableStateListOf(FriendLocation.Traveling)
                }.first().friends.addAll(friends)
            }

            val currentInstanceFriendLocations = this@FriendLocationTabModel.friendLocationMap
                .getOrPut(LocationType.Instance, ::mutableStateListOf)
            val tempInstanceFriends = friendLocationInfoMap[LocationType.Instance] ?: emptyList()

            // 得到对应location的FriendLocation，将新的好友添加到friends
            tempInstanceFriends.groupBy { it.value.location }.forEach { locationFriendEntry ->
                // 通过location找到对应的FriendLocation，没有则创建一个并add到friendLocations
                // 找到相同的location的FriendLocation
                val friendLocation = currentInstanceFriendLocations.find { locationFriendEntry.key == it.location }
                    ?: createFriendLocation(locationFriendEntry.key)
                        .also { currentInstanceFriendLocations.add(it) }
                friendLocation.friends.addAll(locationFriendEntry.value)
            }
        }.onFailure {
            onFailureCallback(it.message.toString())
        }
    }

    private fun createFriendLocation(location: String): FriendLocation {
        val friendLocation = FriendLocation(
            location = location,
            friends = mutableStateListOf()
        )
        // 通过location查询房间实例信息
        screenModelScope.launch(Dispatchers.IO) {
            authSupporter.reTryAuth {
                instancesApi.instanceByLocation(location)
            }.onSuccess { instance ->
                friendLocation.instants.value = InstantsVO(instance)
            }.onFailure {
                onFailureCallback(it.message.toString())
            }
        }
        return friendLocation
    }
}