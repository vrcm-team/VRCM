package io.github.vrcmteam.vrcm.presentation.screens.home.data

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateMapOf
import io.github.vrcmteam.vrcm.network.api.friends.date.FriendData

@Stable
data class HomeUIState(
    val friendIdMap: MutableMap<String, MutableState<FriendData>> = mutableMapOf(),
    val friendLocationMap: MutableMap<String, List<MutableState<FriendData>>> = mutableStateMapOf()
)