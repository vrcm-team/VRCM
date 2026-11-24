package io.github.vrcmteam.vrcm.network.api.avatars.data

import kotlinx.serialization.Serializable

@Serializable
data class AvatarSelectionData(
    val currentAvatar: String,
    val currentAvatarImageUrl: String? = null,
    val currentAvatarThumbnailImageUrl: String? = null,
)
