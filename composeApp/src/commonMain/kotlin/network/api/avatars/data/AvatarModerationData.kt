package io.github.vrcmteam.vrcm.network.api.avatars.data

import kotlinx.serialization.Serializable

internal const val AVATAR_BLOCK_MODERATION_TYPE = "block"

@Serializable
internal data class AvatarModerationData(
    val avatarModerationType: String,
    val targetAvatarId: String,
)

@Serializable
internal data class CreateAvatarModerationRequest(
    val avatarModerationType: String,
    val targetAvatarId: String,
)
