package io.github.vrcmteam.vrcm.network.api.attributes

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class FriendRequestStatus {
    @SerialName("null")
    Null,
    @SerialName("outgoing")
    Outgoing,
    @SerialName("incoming")
    Incoming,
    @SerialName("completed")
    Completed;


    companion object {
        fun fromValue(value: String): FriendRequestStatus =
            when (value) {
                "null" -> Null
                "outgoing" -> Outgoing
                "completed" -> Completed
                else -> error("Unknown value $value")
            }
    }
}