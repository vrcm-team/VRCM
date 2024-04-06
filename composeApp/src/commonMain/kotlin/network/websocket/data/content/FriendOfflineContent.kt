package io.github.vrcmteam.vrcm.network.websocket.data.content

import kotlinx.serialization.Serializable
import network.websocket.data.content.UserContent

@Serializable
data class FriendOfflineContent(
    override val user: UserContent? = null,
    override val userId: String
): FriendEventsContent()