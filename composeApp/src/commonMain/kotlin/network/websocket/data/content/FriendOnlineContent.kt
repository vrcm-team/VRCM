package io.github.vrcmteam.vrcm.network.websocket.data.content

import io.github.vrcmteam.vrcm.network.api.friends.date.FriendData
import kotlinx.serialization.Serializable

/**
 * 好友上线事件内容
 * 当好友在游戏中上线时，VRChat WebSocket 会推送此事件
 */
@Serializable
data class FriendOnlineContent(
    val location: String,
    val platform: String,
    val travelingToLocation: String,
    override val user: UserContent? = null,
    override val userId: String,
    val worldId: String
) : FriendEventsContent() {
    fun mergeWith(existing: FriendData?) =
        mergeFriendPresence(
            existing = existing,
            user = user,
            userId = userId,
            location = location,
            travelingToLocation = travelingToLocation,
            platform = platform,
        )
}
