package io.github.vrcmteam.vrcm.network.api.users.data

import io.github.vrcmteam.vrcm.network.api.attributes.IUser
import io.github.vrcmteam.vrcm.network.api.attributes.UserStatus
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SearchUserData(
    override val bio: String = "",
    override val bioLinks: List<String> = emptyList(),
    override val currentAvatarImageUrl: String = "",
    override val currentAvatarTags: List<String> = emptyList(),
    override val currentAvatarThumbnailImageUrl: String = "",
    override val developerType: String = "",
    override val displayName: String = "",
    override val id: String = "",
    override val isFriend: Boolean = false,
    @SerialName("last_platform")
    override val lastPlatform: String = "",
    /** Canonical profile icon returned by the user search endpoint. */
    @SerialName("iconUrl")
    val profileIconUrl: String = "",
    override val profilePicOverride: String = "",
    override val pronouns: String? = null,
    override val status: UserStatus = UserStatus.Offline,
    override val statusDescription: String = "",
    override val tags: List<String> = emptyList(),
    override val userIcon: String = "",
    @SerialName("last_login")
    override val lastLogin: String? = null,
) : IUser {
    override val iconUrl: String
        get() = profileIconUrl.ifBlank { userIcon.ifBlank { profileImageUrl } }
}
