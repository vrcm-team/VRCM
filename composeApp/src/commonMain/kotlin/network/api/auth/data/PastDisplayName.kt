package io.github.vrcmteam.vrcm.network.api.auth.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PastDisplayName(
    val displayName: String,
    @SerialName("updated_at")
    val updatedAt: String
)