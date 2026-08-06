package io.github.vrcmteam.vrcm.network.api.avatars.data

import kotlinx.serialization.Serializable

@Serializable
data class AvatarUpdateData(
    val name: String? = null,
    val description: String? = null,
    val imageUrl: String? = null,
)
