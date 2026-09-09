package io.github.vrcmteam.vrcm.network.api.invite.data

import kotlinx.serialization.Serializable

@Serializable
internal data class InviteUserRequest(
    val instanceId: String,
    val messageSlot: Int,
)
