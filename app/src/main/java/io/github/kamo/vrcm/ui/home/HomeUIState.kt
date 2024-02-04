package io.github.kamo.vrcm.ui.home

import androidx.compose.runtime.MutableState
import io.github.kamo.vrcm.data.api.auth.FriendInfo

data class HomeUIState(
    val friendIdMap:Map<String,MutableState< FriendInfo>> = mapOf(),
    val friendLocationMap:Map<String, List<MutableState<FriendInfo>>> = mapOf()
)