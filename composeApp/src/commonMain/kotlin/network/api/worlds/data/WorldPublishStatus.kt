package io.github.vrcmteam.vrcm.network.api.worlds.data

import kotlinx.serialization.Serializable

@Serializable
data class WorldPublishStatus(
    val canPublish: Boolean,
)
