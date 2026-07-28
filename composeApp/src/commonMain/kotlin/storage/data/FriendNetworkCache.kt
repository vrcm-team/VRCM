package io.github.vrcmteam.vrcm.storage.data

import io.github.vrcmteam.vrcm.network.api.users.data.MutualFriendData
import kotlinx.serialization.Serializable

@Serializable
data class FriendNetworkCache(
    val userId: String,
    val updatedAt: Long,
    val nodes: List<MutualFriendData>,
    val edges: Map<String, List<String>>,
    // 上次展示的社区划分（节点 ID → 社区编号，「其他」为 -1）；
    // 刷新时按成员重叠匹配继承编号，保证颜色跨刷新稳定
    val communityAssignments: Map<String, Int> = emptyMap(),
)
