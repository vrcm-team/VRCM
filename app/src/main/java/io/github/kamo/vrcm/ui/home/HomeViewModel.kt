package io.github.kamo.vrcm.ui.home

import androidx.compose.runtime.MutableState
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
    val friendLocationMap: MutableMap<LocationType, List<FriendLocation>?> = mutableStateMapOf()

    val refreshing = mutableStateOf(false)
    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            authAPI.friendsFlow()
                .collect { friends ->
                    friends.associate { it.id to mutableStateOf(it) }
                        .also { friendIdMap = it }
                }
        }
    }

    private fun update(newValue: Map<String, MutableState<FriendInfo>>) {
        viewModelScope.launch {
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
            val typeMapping = mapOf(
                LocationType.Offline to friendLocationInfoMap[LocationType.Offline]?.let {
                    listOf(
                        FriendLocation(it)
                    )
                },
                LocationType.Private to friendLocationInfoMap[LocationType.Private]?.let {
                    listOf(
                        FriendLocation(it)
                    )
                },
                LocationType.Instance to friendLocationInfoMap[LocationType.Instance]?.let { friends ->
                    friends.groupBy { it.value.location }.map {
                        FriendLocation(location = it.key, friends = it.value).apply {
                            launch(Dispatchers.IO) {
                                val instance = instanceAPI.instanceByLocation(it.key)

                                instants.value = InstantsVO(
                                    worldName = instance.world.name,
                                    worldImageUrl = fileAPI.findImageFileLocal(instance.world.imageUrl),
                                    instantsType = instance.type,
                                    userCount = "${instance.userCount}/${instance.world.capacity}"
                                )
                            }
                        }
                    }
                },
            )
            this@HomeViewModel.friendLocationMap.clear()
            this@HomeViewModel.friendLocationMap.putAll(typeMapping)
        }
    }
}

enum class LocationType(val typeName: String) {
    /**
     * Friends Active on the Website
     */
    Offline("offline"),

    /**
     * Friends in Private Worlds
     */
    Private("private"),

    /**
     * by Location Instance
     */
    Instance("wrld_")
}




