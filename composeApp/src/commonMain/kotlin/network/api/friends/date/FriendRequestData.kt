package io.github.vrcmteam.vrcm.network.api.friends.date

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class FriendRequestData(
    @SerialName("created_at")
    val createdAt: String,
    val details: String,
    val id: String,
    val message: String,
    val seen: Boolean,
    val senderUserId: String,
    val type: String
)