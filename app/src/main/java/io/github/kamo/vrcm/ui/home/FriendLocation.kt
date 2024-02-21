package io.github.kamo.vrcm.ui.home

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import io.github.kamo.vrcm.data.api.InstantsType
import io.github.kamo.vrcm.data.api.LocationType
import io.github.kamo.vrcm.data.api.auth.FriendInfo

data class FriendLocation(
    val location: String,
    val instants: MutableState<InstantsVO> = mutableStateOf(InstantsVO()),
    val friends: MutableList<MutableState<FriendInfo>>,
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
    }
}

data class InstantsVO(
    val worldName: String = "",
    val worldImageUrl: String? = null,
    val instantsType: InstantsType = InstantsType.Public,
    val regionIconUrl: String? = null,
    val userCount: String = "",
)

