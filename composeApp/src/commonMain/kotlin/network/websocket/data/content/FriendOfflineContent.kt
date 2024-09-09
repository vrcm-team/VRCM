package io.github.vrcmteam.vrcm.network.websocket.data.content

import kotlinx.serialization.Serializable

@Serializable
data class FriendOfflineContent(
    override val user: UserContent? = null,
    override val userId: String
): FriendEventsContent()