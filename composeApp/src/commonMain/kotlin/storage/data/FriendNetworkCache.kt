package io.github.vrcmteam.vrcm.storage.data

import io.github.vrcmteam.vrcm.network.api.users.data.MutualFriendData
import kotlinx.serialization.Serializable

@Serializable
data class FriendNetworkCache(
    val userId: String,
    val updatedAt: Long,
    val nodes: List<MutualFriendData>,
    val edges: Map<String, List<String>>,
)
