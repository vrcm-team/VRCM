package io.github.vrcmteam.vrcm.network.api.worlds.data

import kotlinx.serialization.Serializable

/** Fields accepted by VRChat when updating an existing world. */
@Serializable
data class WorldUpdateData(
    val name: String? = null,
    val description: String? = null,
    val capacity: Int? = null,
    val recommendedCapacity: Int? = null,
    val tags: List<String>? = null,
    val urlList: List<String>? = null,
)
