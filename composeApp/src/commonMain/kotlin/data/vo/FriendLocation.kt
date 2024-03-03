package io.github.vrcmteam.vrcm.data.vo

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import io.github.vrcmteam.vrcm.data.api.AccessType
import io.github.vrcmteam.vrcm.data.api.LocationType
import io.github.vrcmteam.vrcm.data.api.auth.FriendData

@Immutable
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

data class InstantsVO(
    val worldName: String = "",
    val worldImageUrl: String? = null,
    val accessType: AccessType? = null,
    val regionIconUrl: String? = null,
    val userCount: String = "",
)

