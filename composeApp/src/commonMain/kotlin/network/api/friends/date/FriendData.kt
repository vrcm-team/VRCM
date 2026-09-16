package io.github.vrcmteam.vrcm.network.api.friends.date

import io.github.vrcmteam.vrcm.network.api.attributes.IUser
import io.github.vrcmteam.vrcm.network.api.attributes.UserStatus
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class FriendData(
    override val bio: String? = null,
    override val bioLinks: List<String> = emptyList(),
    override val currentAvatarImageUrl: String = "",
    override val currentAvatarTags: List<String> = emptyList(),
    override val currentAvatarThumbnailImageUrl: String? = null,
    override val developerType: String,
    override val displayName: String,
    val friendKey: String,
    override val id: String,
    val imageUrl: String = "",
    /** Newer friend responses expose the profile icon under this field. */
    @SerialName("iconUrl")
    val profileIconUrl: String = "",
    override val isFriend: Boolean,
    @SerialName("last_login")
    override val lastLogin: String? = null,
    @SerialName("last_activity")
    override val lastActivity: String? = null,
    @SerialName("last_platform")
    override val lastPlatform: String = "",
    override val location: String,
    val travelingToLocation: String = "",
    override val profilePicOverride: String = "",
    override val status: UserStatus,
    override val statusDescription: String,
    override val tags: List<String> = emptyList(),
    override val userIcon: String = "",
    override val pronouns: String? = null,
) : IUser {
    /**
     * Friend-list responses can omit the legacy `userIcon` field while still
     * providing `iconUrl` or the required `imageUrl` field.
     */
    override val iconUrl: String
        get() = profileIconUrl.ifBlank {
            userIcon.ifBlank { imageUrl.ifBlank { profileImageUrl } }
        }
}
