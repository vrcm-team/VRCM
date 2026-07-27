package io.github.vrcmteam.vrcm.network.websocket.data.content

import io.github.vrcmteam.vrcm.network.api.friends.date.FriendData
import kotlinx.serialization.Serializable

@Serializable
data class FriendLocationContent(
    val canRequestInvite: Boolean,
    val location: String,
    val travelingToLocation: String,
    override val user: UserContent? = null,
    override val userId: String,
    val worldId: String
): FriendEventsContent() {
    fun mergeWith(existing: FriendData?) =
        mergeFriendPresence(
            existing = existing,
            user = user,
            userId = userId,
            location = location,
            travelingToLocation = travelingToLocation,
        )
}
