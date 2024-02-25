package io.github.kamo.vrcm.data.vo

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import io.github.kamo.vrcm.data.api.AccessType
import io.github.kamo.vrcm.data.api.LocationType
import io.github.kamo.vrcm.data.api.auth.FriendData

data class FriendLocation(
    val location: String,
    val instants: MutableState<InstantsVO> = mutableStateOf(InstantsVO()),
    val friends: MutableList<MutableState<FriendData>>,
) {
    companion object {
        val Offline
            get() = FriendLocation(
                location = LocationType.Offline.typeName,
                friends = mutableStateListOf()
            )
        val Private
            get() = FriendLocation(
                location = LocationType.Private.typeName,
                friends = mutableStateListOf()
            )

        val Traveling
            get() = FriendLocation(
                location = LocationType.Traveling.typeName,
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

