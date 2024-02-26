package io.github.kamo.vrcm.ui.home

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.kamo.vrcm.data.api.CountryIcon
import io.github.kamo.vrcm.data.api.LocationType
import io.github.kamo.vrcm.data.api.auth.AuthApi
import io.github.kamo.vrcm.data.api.auth.CurrentUserData
import io.github.kamo.vrcm.data.api.auth.FriendData
import io.github.kamo.vrcm.data.api.instance.InstanceAPI
import io.github.kamo.vrcm.data.vo.FriendLocation
import io.github.kamo.vrcm.data.vo.InstantsVO
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

    private val _currentUser = mutableStateOf<CurrentUserData?>(null)

    private val _errorMessage = mutableStateOf("")

    val errorMessage by _errorMessage

    val currentUser by _currentUser

    fun ini() = viewModelScope.launch(Dispatchers.IO) {
        runCatching {
            _currentUser.value = authAPI.currentUser()
        }.onFailure {
            _errorMessage.value = "error: ${it.message}"
        }
    }


    suspend fun refresh(onError: () -> Unit) {
        this@HomeViewModel.friendLocationMap.clear()
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                authAPI.friendsFlow()
                    .collect { friends ->
                        friends.associate { it.id to mutableStateOf(it) }
                            .also { update(it) }
                    }
            }.onFailure {
                _errorMessage.value = "error: ${it.message}"
                viewModelScope.launch(Dispatchers.Main) {
                    onError()
                }
            }
        }.join()
    }

    private fun update(
        newValue: Map<String, MutableState<FriendData>>
    ) = viewModelScope.launch(Dispatchers.Main) {
        val friendLocationInfoMap = newValue.values.groupBy {
            when (it.value.location) {
                LocationType.Offline.value -> LocationType.Offline
                LocationType.Private.value -> LocationType.Private
                LocationType.Traveling.value -> LocationType.Traveling
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
        friendLocationInfoMap[LocationType.Traveling]?.let { friends ->
            this@HomeViewModel.friendLocationMap.getOrPut(LocationType.Traveling) {
                mutableStateListOf(FriendLocation.Traveling)
            }.first().friends.addAll(friends)
        }
        val inInstanceFriends = friendLocationInfoMap[LocationType.Instance] ?: emptyList()
        inInstanceFriends.groupBy { it.value.location }.forEach { locationFriendEntry ->
            this@HomeViewModel.friendLocationMap
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

                    viewModelScope.launch(Dispatchers.IO) instanceLaunch@{
                        runCatching {
                            val instance = instanceAPI.instanceByLocation(friendLocation.location)
                            friendLocation.instants.value = InstantsVO(
                                worldName = instance.world.name ?: "",
                                worldImageUrl = instance.world.thumbnailImageUrl,
                                accessType = instance.accessType,
                                regionIconUrl = CountryIcon.fetchIconUrl(instance.region),
                                userCount = "${instance.userCount}/${instance.world.capacity}"
                            )
                        }.onFailure {
                            println("location: ${friendLocation.location}")
                            _errorMessage.value =
                                "location: ${friendLocation.location} error: ${it.message} "
                        }
                    }
                    friendLocation.also(friendLocationList::add)
                }.friends.addAll(locationFriendEntry.value)
        }
    }

    fun onErrorMessageChange(errorMessage: String) {
        _errorMessage.value = errorMessage
    }
}






