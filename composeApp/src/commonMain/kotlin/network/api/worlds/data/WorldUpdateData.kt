package io.github.vrcmteam.vrcm.network.api.worlds.data

import kotlinx.serialization.Serializable

@Serializable
data class WorldUpdateData(
    val imageUrl: String? = null,
)
