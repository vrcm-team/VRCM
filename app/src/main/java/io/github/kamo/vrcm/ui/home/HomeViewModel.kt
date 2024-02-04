package io.github.kamo.vrcm.ui.home

import androidx.compose.runtime.mutableStateMapOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.kamo.vrcm.data.api.auth.AuthAPI
import io.github.kamo.vrcm.data.api.auth.FriendInfo
import io.github.kamo.vrcm.data.api.file.FileAPI
import kotlinx.coroutines.launch

class HomeViewModel(
    private val authAPI: AuthAPI,
    private val fileAPI: FileAPI

) : ViewModel() {

    private var friendIdMap = mapOf<String, FriendInfo>()
        set(value) {
            field = value
            updateFriendLocations(value)
        }

    val friendLocationMap = mutableStateMapOf<String, List<FriendInfo>>()

    fun init() {
        viewModelScope.launch {
            val newFriendIdMap = authAPI.friends().associateBy { it.id }
            newFriendIdMap.values.forEach {
                it.currentAvatarThumbnailImageUrl =  fileAPI.getFileLocal(it.currentAvatarThumbnailImageUrl)?:""
            }
            friendIdMap = newFriendIdMap
        }
    }

    private fun updateFriendLocations(friendInfoMap: Map<String, FriendInfo>) {
        friendLocationMap.clear()
        friendLocationMap.putAll(friendInfoMap.values.groupBy { it.location })
    }


}






