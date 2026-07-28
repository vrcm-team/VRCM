package io.github.vrcmteam.vrcm.network.websocket.data.content

import io.github.vrcmteam.vrcm.network.api.attributes.LocationType
import kotlinx.serialization.Serializable

@Serializable
data class FriendActiveContent(
    override val user: UserContent,
    override val userId: String
): FriendEventsContent(){
    fun toFriendData() = user.toFriendData(
        userId = userId,
        location = LocationType.Offline.value,
    )
}
