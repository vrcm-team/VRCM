package io.github.vrcmteam.vrcm.presentation.screens.home

import androidx.compose.runtime.*
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import io.github.vrcmteam.vrcm.network.api.attributes.CountryIcon
import io.github.vrcmteam.vrcm.network.api.attributes.LocationType
import io.github.vrcmteam.vrcm.network.api.auth.AuthApi
import io.github.vrcmteam.vrcm.network.api.auth.data.CurrentUserData
import io.github.vrcmteam.vrcm.network.api.friends.FriendsApi
import io.github.vrcmteam.vrcm.network.api.friends.date.FriendData
import io.github.vrcmteam.vrcm.network.api.instances.InstancesApi
import io.github.vrcmteam.vrcm.network.supports.VRCApiException
import io.github.vrcmteam.vrcm.presentation.screens.home.data.FriendLocation
import io.github.vrcmteam.vrcm.presentation.screens.home.data.InstantsVO
import io.ktor.util.network.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


class HomeScreenModel(
    private val authApi: AuthApi,
    private val instancesApi: InstancesApi,
    private val friendsApi: FriendsApi
) : ScreenModel {
    //    private val uiState = HomeUIState()
//    val _uiState: HomeUIState = uiState

    val friendLocationMap: MutableMap<LocationType, MutableList<FriendLocation>> =
        mutableStateMapOf()

    private val _currentUser = mutableStateOf<CurrentUserData?>(null)

    private val _errorMessage = mutableStateOf("")

    val errorMessage by _errorMessage

    val currentUser by _currentUser

    fun ini(onError: () -> Unit) = screenModelScope.launch(Dispatchers.IO) {
        runCatching { _currentUser.value = authApi.currentUser() }.onHomeFailure(onError)
    }


    suspend fun refresh(onError: () -> Unit) {
        this@HomeScreenModel.friendLocationMap.clear()
        screenModelScope.launch(Dispatchers.IO) {
            runCatching {
                friendsApi.friendsFlow()
                    .collect { friends ->
                        friends.associate { it.id to mutableStateOf(it) }
                            .also { update(it,onError) }
                    }
            }.onHomeFailure(onError)
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
        val inInstanceFriends = friendLocationInfoMap[LocationType.Instance] ?: emptyList()
        inInstanceFriends.groupBy { it.value.location }.forEach { locationFriendEntry ->
            this@HomeScreenModel.friendLocationMap
                .getOrPut(LocationType.Instance, ::mutableStateListOf)
                // 得到对应location的FriendLocation，将新的好友添加到friends
                .let { friendLocationList ->
                    // 通过location找到对应的FriendLocation，没有则创建一个并add到friendLocations
                    val friendLocation =
                        (friendLocationList.find { locationFriendEntry.key == it.location }
                            ?: FriendLocation(
                                location = locationFriendEntry.key,
                                friends = mutableStateListOf()
                            ))
                    screenModelScope.launch(Dispatchers.IO) {
                        instancesApi.instanceByLocation(friendLocation.location)
                            .onSuccess { instance ->
                                friendLocation.instants.value = InstantsVO(
                                    worldName = instance.world.name ?: "",
                                    worldImageUrl = instance.world.thumbnailImageUrl,
                                    accessType = instance.accessType,
                                    regionIconUrl = CountryIcon.fetchIconUrl(instance.region),
                                    userCount = "${instance.userCount}/${instance.world.capacity}"
                                )
                                friendLocation.friends.addAll(locationFriendEntry.value)
                                friendLocationList.add(friendLocation)
                            }.onHomeFailure(onError)
                    }
                }
        }
    }

    fun onErrorMessageChange(errorMessage: String) {
        _errorMessage.value = errorMessage
    }

    private fun Result<*>.onHomeFailure(onError: () -> Unit) =
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






