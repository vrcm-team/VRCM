package io.github.vrcmteam.vrcm.network.api.groups.data

import kotlinx.serialization.Serializable

@Serializable
data class UpdateGroupRepresentationRequest(
    val isRepresenting: Boolean,
)
