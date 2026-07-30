package io.github.vrcmteam.vrcm.presentation.screens.home.data

import androidx.compose.runtime.*
import io.github.vrcmteam.vrcm.network.api.attributes.LocationType
import io.github.vrcmteam.vrcm.network.api.friends.date.FriendData

@Stable
data class FriendLocation(
    val location: String,
    val instants: MutableState<HomeInstanceVo> = mutableStateOf(HomeInstanceVo()),
    val friends: MutableMap<String, MutableState<FriendData>>,
    // 正在跃迁到此位置的好友 ID 集合，UI 上显示加载动画
    val travelingIds: MutableState<Set<String>> = mutableStateOf(emptySet()),
) {
    companion object {
        val Offline
            get() = FriendLocation(
                location = LocationType.Offline.value,
                friends = mutableStateMapOf()
            )
        val Web
            get() = FriendLocation(
                location = LocationType.Web.value,
                friends = mutableStateMapOf()
            )
        val Private
            get() = FriendLocation(
                location = LocationType.Private.value,
                friends = mutableStateMapOf()
            )

        val Traveling
            get() = FriendLocation(
                location = LocationType.Traveling.value,
                friends = mutableStateMapOf()
            )
    }

    @Stable
    val friendList: List<State<FriendData>>
        get() = friends.values.sortedBy { it.value.displayName }
}


