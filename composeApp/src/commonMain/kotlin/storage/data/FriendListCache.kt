package io.github.vrcmteam.vrcm.storage.data

import io.github.vrcmteam.vrcm.network.api.friends.date.FriendData
import kotlinx.serialization.Serializable

@Serializable
data class FriendListCache(
    val friends: List<FriendData>,
)
