package io.github.vrcmteam.vrcm.presentation.screens.home

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import io.github.vrcmteam.vrcm.network.api.attributes.CountryIcon
import io.github.vrcmteam.vrcm.network.api.attributes.LocationType
import io.github.vrcmteam.vrcm.network.api.auth.data.CurrentUserData
import io.github.vrcmteam.vrcm.network.api.friends.FriendsApi
import io.github.vrcmteam.vrcm.network.api.friends.date.FriendData
import io.github.vrcmteam.vrcm.network.api.instances.InstancesApi
import io.github.vrcmteam.vrcm.network.supports.VRCApiException
import io.github.vrcmteam.vrcm.presentation.screens.home.data.FriendLocation
import io.github.vrcmteam.vrcm.presentation.screens.home.data.InstantsVO
import io.github.vrcmteam.vrcm.presentation.supports.AuthSupporter
import io.ktor.util.network.UnresolvedAddressException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.retry
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock


class HomeScreenModel(
    private val authSupporter: AuthSupporter,
    private val instancesApi: InstancesApi,
    private val friendsApi: FriendsApi
) : ScreenModel {
    //    private val uiState = HomeUIState()
//    val _uiState: HomeUIState = uiState

    val friendLocationMap: MutableMap<LocationType, MutableList<FriendLocation>> =
        mutableStateMapOf()

    private val updateMutex = Mutex()

    private val _currentUser = mutableStateOf<CurrentUserData?>(null)

    private val _errorMessage = mutableStateOf("")

    val errorMessage by _errorMessage

    val currentUser by _currentUser

    fun ini(onError: () -> Unit) = screenModelScope.launch(Dispatchers.IO) {
        authSupporter.reTryAuth { authSupporter.currentUser() }
            .onHomeFailure(onError)
            .onSuccess { _currentUser.value = it }
    }


    suspend fun refresh(onError: () -> Unit) {
        this@HomeScreenModel.friendLocationMap.clear()
        // 多次更新时加把锁
        // 防止再次更新时拉取到的与上次相同的instanceId导致item的key冲突
        screenModelScope.launch(Dispatchers.IO) {
            friendsApi.friendsFlow()
                .retry(1) {
                    authSupporter.doReTryAuth()
                }.catch {
                    Result.failure<Throwable>(it).onHomeFailure(onError)
                }.collect { friends ->
                    friends.associate { it.id to mutableStateOf(it) }
                        .also { updateMutex.withLock { update(it, onError) } }
                }
        }.join()
    }

    private fun update(
        newValue: Map<String, MutableState<FriendData>>,
        onError: () -> Unit
    ) = screenModelScope.launch(Dispatchers.Main) {
        val friendLocationInfoMap = newValue.values.groupBy {
            when (it.value.location) {
                LocationType.Offline.value -> LocationType.Offline
                LocationType.Private.value -> LocationType.Private
                LocationType.Traveling.value -> LocationType.Traveling
                else -> LocationType.Instance
            }
        }
        friendLocationInfoMap[LocationType.Offline]?.let { friends ->
            this@HomeScreenModel.friendLocationMap.getOrPut(LocationType.Offline) {
                mutableStateListOf(FriendLocation.Offline)
            }.first().friends.addAll(friends)
        }
        friendLocationInfoMap[LocationType.Private]?.let { friends ->
            this@HomeScreenModel.friendLocationMap.getOrPut(LocationType.Private) {
                mutableStateListOf(FriendLocation.Private)
            }.first().friends.addAll(friends)
        }
        friendLocationInfoMap[LocationType.Traveling]?.let { friends ->
            this@HomeScreenModel.friendLocationMap.getOrPut(LocationType.Traveling) {
                mutableStateListOf(FriendLocation.Traveling)
            }.first().friends.addAll(friends)
        }

        val currentInstanceFriendLocations = this@HomeScreenModel.friendLocationMap
            .getOrPut(LocationType.Instance, ::mutableStateListOf)
        val tempInstanceFriends = friendLocationInfoMap[LocationType.Instance] ?: emptyList()

        // 得到对应location的FriendLocation，将新的好友添加到friends
        tempInstanceFriends.groupBy { it.value.location }.forEach { locationFriendEntry ->
            // 通过location找到对应的FriendLocation，没有则创建一个并add到friendLocations
            // 找到相同的location的FriendLocation
            val friendLocation =
                (currentInstanceFriendLocations.find { locationFriendEntry.key == it.location }
                    ?: FriendLocation(
                        location = locationFriendEntry.key,
                        friends = mutableStateListOf()
                    ))

            screenModelScope.launch(Dispatchers.IO) {
                authSupporter.reTryAuth {
                    instancesApi.instanceByLocation(friendLocation.location)
                }.onSuccess { instance ->
                    friendLocation.instants.value = InstantsVO(
                        worldName = instance.world.name ?: "",
                        worldImageUrl = instance.world.thumbnailImageUrl,
                        accessType = instance.accessType,
                        regionIconUrl = CountryIcon.fetchIconUrl(instance.region),
                        userCount = "${instance.userCount}/${instance.world.capacity}"
                    )
                    friendLocation.friends.addAll(locationFriendEntry.value)
                    currentInstanceFriendLocations.add(friendLocation)
                }.onHomeFailure(onError)
            }

        }
    }

    fun onErrorMessageChange(errorMessage: String) {
        _errorMessage.value = errorMessage
    }

    private fun <T> Result<T>.onHomeFailure(onError: () -> Unit) =
        onFailure {
            val message = when (it) {
                is UnresolvedAddressException -> {
                    "Failed to Auth: ${it.message}"
                }

                is VRCApiException -> {
                    "Failed to Auth: ${it.message}"
                }

                else -> "${it.message}"
            }
            onErrorMessageChange(message)
            screenModelScope.launch(Dispatchers.Main) {
                delay(2000L)
                onError()
            }
        }
}






