package io.github.vrcmteam.vrcm.network.api.invite.data

import kotlinx.serialization.Serializable

@Serializable
internal data class RequestInviteRequest(
    val requestSlot: Int,
)
