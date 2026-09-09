package io.github.vrcmteam.vrcm.network.api.invite.data

import kotlinx.serialization.Serializable

@Serializable
data class InviteResponseRequest(
    val responseSlot: Int,
)
