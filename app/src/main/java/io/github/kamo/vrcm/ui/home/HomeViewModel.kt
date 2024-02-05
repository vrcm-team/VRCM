package io.github.kamo.vrcm.ui.home

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.kamo.vrcm.data.api.auth.AuthAPI
import io.github.kamo.vrcm.data.api.auth.FriendInfo
import io.github.kamo.vrcm.data.api.file.FileAPI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class HomeViewModel(
    private val authAPI: AuthAPI,
    private val fileAPI: FileAPI

) : ViewModel() {

    //    private val uiState = HomeUIState()
//    val _uiState: HomeUIState = uiState
    private var friendIdMap: Map<String, MutableState<FriendInfo>> = mutableMapOf()
        set(value) {
            update(value)
            field = value
        }
    val friendLocationMap: MutableMap<String, List<MutableState<FriendInfo>>> = mutableStateMapOf()
    fun flush() {
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
            val friendLocationInfoMap = newValue.values.groupBy({ it.value.location }) {
                it.apply {
                    launch(Dispatchers.IO) {
                        value = value.copy(
                            imageUrl = fileAPI.findImageFileLocal(
                                value.imageUrl,
                                fileSize = 64
                            )
                        )
                    }
                }
            }
            this@HomeViewModel.friendLocationMap.clear()
            this@HomeViewModel.friendLocationMap.putAll(friendLocationInfoMap)
        }
    }
}






