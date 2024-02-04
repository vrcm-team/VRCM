package io.github.kamo.vrcm.ui.home

import io.github.kamo.vrcm.data.api.auth.FriendInfo
import io.github.vrchatapi.model.Instance

data class FriendLocation(
    val location: String,
    val instance: Instance,
    val friends: List<FriendInfo>,
) {
    constructor(friends: List<FriendInfo>) : this(friends[0].location, Instance(),friends)
}