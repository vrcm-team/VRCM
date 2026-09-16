package io.github.vrcmteam.vrcm.network.websocket.data.content

import io.github.vrcmteam.vrcm.network.api.attributes.IUser
import io.github.vrcmteam.vrcm.network.api.attributes.UserStatus
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UserContent(
    val allowAvatarCopying: Boolean,
    override val bio: String? = null,
    override val bioLinks: List<String> = emptyList(),
    override val currentAvatarImageUrl: String = "",
    override val currentAvatarTags: List<String> = emptyList(),
    override val currentAvatarThumbnailImageUrl: String? = null,
    @SerialName("date_joined")
    val dateJoined: String,
    override val developerType: String,
    override val displayName: String,
    val friendKey: String,
    override val id: String,
    override val isFriend: Boolean,
    @SerialName("last_activity")
    override val lastActivity: String,
    @SerialName("last_login")
    override val lastLogin: String,
    @SerialName("last_platform")
    override val lastPlatform: String,
    /** Canonical profile icon included in user-update events. */
    @SerialName("iconUrl")
    val profileIconUrl: String = "",
    override val profilePicOverride: String = "",
    val state: String,
    override val status: UserStatus,
    override val statusDescription: String,
    override val tags: List<String>,
    override val userIcon: String = "",
    override val pronouns: String? = null
): IUser {
    override val iconUrl: String
        get() = profileIconUrl.ifBlank { userIcon.ifBlank { profileImageUrl } }
}
