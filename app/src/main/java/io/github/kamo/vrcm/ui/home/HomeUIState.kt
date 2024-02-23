package io.github.kamo.vrcm.ui.home

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateMapOf
import io.github.kamo.vrcm.data.api.auth.FriendData

data class HomeUIState(
    val friendIdMap: MutableMap<String, MutableState<FriendData>> = mutableMapOf(),
    val friendLocationMap: MutableMap<String, List<MutableState<FriendData>>> = mutableStateMapOf()
)