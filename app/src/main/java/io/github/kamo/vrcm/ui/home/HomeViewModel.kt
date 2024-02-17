package io.github.kamo.vrcm.ui.home

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.kamo.vrcm.data.api.auth.AuthAPI
import io.github.kamo.vrcm.data.api.auth.FriendInfo
import io.github.kamo.vrcm.data.api.file.FileAPI
import io.github.kamo.vrcm.data.api.instance.InstanceAPI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class HomeViewModel(
    private val authAPI: AuthAPI,
    private val fileAPI: FileAPI,
    private val instanceAPI: InstanceAPI
) : ViewModel() {
    //    private val uiState = HomeUIState()
//    val _uiState: HomeUIState = uiState
    private var friendIdMap: Map<String, MutableState<FriendInfo>> = mutableMapOf()
        set(value) {
            update(value)
            field = value
        }
    val friendLocationMap: MutableMap<LocationType, MutableList<FriendLocation>> =
        mutableStateMapOf()

    var refreshing = mutableStateOf(false)
    fun refresh() {
        refreshing.value = true
        this@HomeViewModel.friendLocationMap.clear()
        viewModelScope.launch(Dispatchers.IO) {
            authAPI.friendsFlow()
                .collect { friends ->
                    friends.associate { it.id to mutableStateOf(it) }
                        .also { friendIdMap = it }
                }
            refreshing.value = false
        }
    }

    private fun update(newValue: Map<String, MutableState<FriendInfo>>) {
        viewModelScope.launch(Dispatchers.Main) {
            val friendLocationInfoMap = newValue.values.groupBy({
                when (it.value.location) {
                    LocationType.Offline.typeName -> LocationType.Offline
                    LocationType.Private.typeName -> LocationType.Private
                    else -> LocationType.Instance
                }
            }) {
                it.apply {
                    launch(Dispatchers.IO) {
                        value = value.copy(
                            imageUrl = fileAPI.findImageFileLocal(value.imageUrl)
                        )
                    }
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
            inInstanceFriends.groupBy { it.value.location }
                .forEach { locationFriendEntry ->
                    this@HomeViewModel.friendLocationMap.getOrPut(
                        LocationType.Instance,
                        ::mutableStateListOf
                    )
                        .let { friendLocations ->
                            friendLocations.find { locationFriendEntry.key == it.location }
                                ?: FriendLocation(
                                    location = locationFriendEntry.key,
                                    friends = mutableStateListOf()
                                ).apply {
                                    launch(Dispatchers.IO) {
                                        val instance = instanceAPI.instanceByLocation(location)
                                        val worldImageUrl =
                                            fileAPI.findImageFileLocal(instance.world.imageUrl)
                                        if (instance.world.name == null) {
                                            println("i = $instance")
                                        }
                                        instants.value = InstantsVO(
                                            worldName = instance.world.name ?: "",
                                            worldImageUrl = worldImageUrl,
                                            instantsType = instance.type,
                                            userCount = "${instance.userCount}/${instance.world.capacity}"
                                        )
                                    }
                                    friendLocations.add(this@apply)
                                }
                        }.friends.addAll(locationFriendEntry.value)
                }
        }
    }
}






