package io.github.vrcmteam.vrcm.network.api.attributes

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class NotificationType(val value: String) {

    @SerialName("friendRequest")
    FriendRequest("friendRequest"),

    @SerialName("all")
    All("all"),

}