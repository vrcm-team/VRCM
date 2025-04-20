package io.github.vrcmteam.vrcm.network.api.users.data

import io.github.vrcmteam.vrcm.network.api.attributes.IUser
import io.github.vrcmteam.vrcm.network.api.attributes.UserStatus
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SearchUserData(
    override val bio: String = "",
    override val bioLinks: List<String> = emptyList(),
    override val currentAvatarImageUrl: String,
    override val currentAvatarTags: List<String> = emptyList(),
    override val currentAvatarThumbnailImageUrl: String,
    override val developerType: String,
    override val displayName: String,
    override val id: String,
    override val isFriend: Boolean,
    @SerialName("last_platform")
    override val lastPlatform: String,
    override val profilePicOverride: String,
    override val pronouns: String?,
    override val status: UserStatus,
    override val statusDescription: String,
    override val tags: List<String>,
    override val userIcon: String,
    @SerialName("last_login")
    override val lastLogin: String?,
) : IUser