package io.github.kamo.vrcm.ui.home

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.kamo.vrcm.data.api.auth.AuthAPI
import io.github.kamo.vrcm.data.api.auth.FriendInfo
import io.github.kamo.vrcm.data.api.instance.InstanceAPI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class HomeViewModel(
    private val authAPI: AuthAPI,
    private val instanceAPI: InstanceAPI
) : ViewModel() {
    //    private val uiState = HomeUIState()
//    val _uiState: HomeUIState = uiState

    val friendLocationMap: MutableMap<LocationType, MutableList<FriendLocation>> = mutableStateMapOf()

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
                                    val instance =
                                        instanceAPI.instanceByLocation(newFriendLocation.location)
                                    val worldName = try {
                                        instance.world.name
                                    } catch (e: Exception) {
                                        println("instanceAPI.instanceByLocation error: $instance")
                                        ""
                                    }
                                    runCatching {
                                        newFriendLocation.instants.value = InstantsVO(
                                            worldName = worldName,
                                            worldImageUrl = instance.world.thumbnailImageUrl ?: "",
                                            instantsType = instance.type,
                                            userCount = "${instance.userCount}/${instance.world.capacity}"
                                        )
                                    }
                                }.invokeOnCompletion {
                                    it ?: friendLocations.add(newFriendLocation)
                                }
                            }
                    }.friends.addAll(locationFriendEntry.value)
            }
        }
}






