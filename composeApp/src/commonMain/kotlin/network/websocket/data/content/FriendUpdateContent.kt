package io.github.vrcmteam.vrcm.network.websocket.data.content

import kotlinx.serialization.Serializable

@Serializable
data class FriendUpdateContent(
    val user: UserContent,
)
