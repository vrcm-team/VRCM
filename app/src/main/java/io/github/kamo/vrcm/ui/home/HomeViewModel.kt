package io.github.kamo.vrcm.ui.home

import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.kamo.vrcm.data.api.CountryIcon
import io.github.kamo.vrcm.data.api.LocationType
import io.github.kamo.vrcm.data.api.auth.AuthApi
import io.github.kamo.vrcm.data.api.auth.FriendInfo
import io.github.kamo.vrcm.data.api.auth.UserInfo
import io.github.kamo.vrcm.data.api.instance.InstanceAPI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class HomeViewModel(
    private val authAPI: AuthApi,
    private val instanceAPI: InstanceAPI
) : ViewModel() {
    //    private val uiState = HomeUIState()
//    val _uiState: HomeUIState = uiState

    val friendLocationMap: MutableMap<LocationType, MutableList<FriendLocation>> =
        mutableStateMapOf()

    private val _currentUser = mutableStateOf<UserInfo?>(null)

    val currentUser by _currentUser

    fun ini() = viewModelScope.launch(Dispatchers.IO) {
        _currentUser.value = authAPI.currentUser()
    }


    suspend fun refresh() {
        this@HomeViewModel.friendLocationMap.clear()
        viewModelScope.launch(Dispatchers.IO) {
            authAPI.friendsFlow()
                .collect { friends ->
                    friends.associate { it.id to mutableStateOf(it) }.also { update(it) }
                }
        }.join()
    }

    private fun update(newValue: Map<String, MutableState<FriendInfo>>) =
        viewModelScope.launch(Dispatchers.Main) {
            val friendLocationInfoMap = newValue.values.groupBy {
                when (it.value.location) {
                    LocationType.Offline.typeName -> LocationType.Offline
                    LocationType.Private.typeName -> LocationType.Private
                    else -> LocationType.Instance
                }
            }
            friendLocationInfoMap[LocationType.Offline]?.let { friends ->
                this@HomeViewModel.friendLocationMap.getOrPut(LocationType.Offline) {
                    mutableStateListOf(FriendLocation.Offline)
                }.first().friends.addAll(friends)
            }
            friendLocationInfoMap[LocationType.Private]?.let { friends ->
                this@HomeViewModel.friendLocationMap.getOrPut(LocationType.Private) {
                    mutableStateListOf(FriendLocation.Private)
                }.first().friends.addAll(friends)
            }

            val inInstanceFriends = friendLocationInfoMap[LocationType.Instance] ?: emptyList()
            inInstanceFriends.groupBy { it.value.location }.forEach { locationFriendEntry ->
                this@HomeViewModel.friendLocationMap
                    .getOrPut(LocationType.Instance, ::mutableStateListOf)
                    // 得到对应location的FriendLocation，将新的好友添加到friends
                    .let { friendLocations ->
                        // 通过location找到对应的FriendLocation，没有则创建一个并add到friendLocations
                        friendLocations.find { locationFriendEntry.key == it.location }
                            ?: FriendLocation(
                                location = locationFriendEntry.key,
                                friends = mutableStateListOf()
                            ).also { newFriendLocation ->
                                viewModelScope.launch(Dispatchers.IO) {
                                    val instance = instanceAPI.instanceByLocation(newFriendLocation.location)
                                            ?: return@launch
                                    runCatching {
                                        newFriendLocation.instants.value = InstantsVO(
                                            worldName = instance.world.name?: "",
                                            worldImageUrl = instance.world.thumbnailImageUrl,
                                            instantsType = instance.detailedType,
                                            regionIconUrl = CountryIcon.fetchIconUrl(instance.location),
                                            userCount = "${instance.userCount}/${instance.world.capacity}"
                                        )
                                    }
                                    friendLocations.add(newFriendLocation)
                                }
                            }
                    }.friends.addAll(locationFriendEntry.value)
            }
        }
}






