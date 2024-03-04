package io.github.vrcmteam.vrcm.presentation.screens.home.data

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import io.github.vrcmteam.vrcm.network.api.attributes.LocationType
import io.github.vrcmteam.vrcm.network.api.auth.data.FriendData
import presentation.screens.home.data.InstantsVO

@Stable
data class FriendLocation(
    val location: String,
    val instants: MutableState<InstantsVO> = mutableStateOf(InstantsVO()),
    val friends: MutableList<MutableState<FriendData>>,
) {
    companion object {
        val Offline
            get() = FriendLocation(
                location = LocationType.Offline.value,
                friends = mutableStateListOf()
            )
        val Private
            get() = FriendLocation(
                location = LocationType.Private.value,
                friends = mutableStateListOf()
            )

        val Traveling
            get() = FriendLocation(
                location = LocationType.Traveling.value,
                friends = mutableStateListOf()
            )
    }
}



