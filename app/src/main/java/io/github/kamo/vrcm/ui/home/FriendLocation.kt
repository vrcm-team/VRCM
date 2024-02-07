package io.github.kamo.vrcm.ui.home

import androidx.compose.runtime.MutableState
import io.github.kamo.vrcm.data.api.auth.FriendInfo
import io.github.kamo.vrcm.data.api.instance.InstanceInfo

data class FriendLocation(
    val location: String,
    var instance: MutableState<InstanceInfo>?,
    val friends: List<MutableState<FriendInfo>>,
) {
    constructor(friends: List<MutableState<FriendInfo>>) : this(
        friends[0].value.location,
        null,
        friends
    )
}