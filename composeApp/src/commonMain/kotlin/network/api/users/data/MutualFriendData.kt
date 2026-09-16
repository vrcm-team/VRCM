package io.github.vrcmteam.vrcm.network.api.users.data

import io.github.vrcmteam.vrcm.network.api.attributes.IUser
import io.github.vrcmteam.vrcm.network.api.attributes.UserStatus
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MutualFriendData(
    override val id: String,
    override val displayName: String = "",
    override val status: UserStatus = UserStatus.Offline,
    override val statusDescription: String = "",
    override val bio: String? = null,
    override val bioLinks: List<String> = emptyList(),
    override val tags: List<String> = emptyList(),
    override val currentAvatarImageUrl: String = "",
    override val currentAvatarThumbnailImageUrl: String? = null,
    override val currentAvatarTags: List<String> = emptyList(),
    val imageUrl: String = "",
    /** Canonical profile icon returned by the mutual-friends endpoint. */
    @SerialName("iconUrl")
    val profileIconUrl: String = "",
    override val profilePicOverride: String = "",
    override val userIcon: String = "",
    override val isFriend: Boolean = true,
    @SerialName("last_login")
    override val lastLogin: String? = null,
    @SerialName("last_platform")
    override val lastPlatform: String = "",
    override val developerType: String = "",
    override val pronouns: String? = null,
) : IUser {
    override val iconUrl: String
        get() = profileIconUrl.ifBlank {
            userIcon.ifBlank { imageUrl.ifBlank { profileImageUrl } }
        }
}
