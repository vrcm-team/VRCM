package io.github.vrcmteam.vrcm.presentation.screens.home.tab

import androidx.compose.runtime.mutableStateListOf
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import io.github.vrcmteam.vrcm.network.api.attributes.CountryIcon
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

class FriendListTabModel(
    private val friendsApi: FriendsApi,
    private val instancesApi: InstancesApi,
    private val authSupporter: AuthSupporter,
): ScreenModel {


    val friendList: MutableList<FriendData> = mutableStateListOf()

    private val updateMutex = Mutex()

    suspend fun refreshFriendList(onHomeFailure: Result<*>.() -> Unit) {
        friendList.clear()
        // 多次更新时加把锁
        // 防止再次更新时拉取到的与上次相同的instanceId导致item的key冲突
        screenModelScope.launch(Dispatchers.IO) {
            friendsApi.allFriendsFlow()
                .retry(1) {
                    if (it is VRCApiException) authSupporter.doReTryAuth() else false
                }.catch {
                    Result.failure<Throwable>(it).onHomeFailure()
                }.collect { friends ->
                    updateMutex.withLock(friendList) { update(friends, onHomeFailure) }
                }
        }.join()

    }

    private fun update(
        friends: List<FriendData>,
        onHomeFailure: Result<*>.() -> Unit
    ) = screenModelScope.launch(Dispatchers.Default) {
        friendList.removeAll { old -> friends.any { old.id == it.id } }
        friendList.addAll(friends)
    }

    private fun createFriendLocation(location: String, onHomeFailure: Result<*>.() -> Unit): FriendLocation {
        val friendLocation = FriendLocation(
            location = location,
            friends = mutableStateListOf()
        )
        // 通过location查询房间实例信息
        screenModelScope.launch(Dispatchers.IO) {
            authSupporter.reTryAuth {
                instancesApi.instanceByLocation(location)
            }.onSuccess { instance ->
                friendLocation.instants.value = InstantsVO(
                    worldName = instance.world.name ?: "",
                    worldImageUrl = instance.world.thumbnailImageUrl,
                    accessType = instance.accessType,
                    regionIconUrl = CountryIcon.fetchIconUrl(instance.region),
                    userCount = "${instance.userCount}/${instance.world.capacity}"
                )
            }.onHomeFailure()
        }
        return friendLocation
    }
}