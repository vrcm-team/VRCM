package io.github.vrcmteam.vrcm.network.websocket.data.content

import cafe.adriel.voyager.core.lifecycle.JavaSerializable
import kotlinx.serialization.Serializable

@Serializable
abstract class FriendEventsContent: JavaSerializable{
    abstract val user: UserContent?
    abstract val userId: String
}