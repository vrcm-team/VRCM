package io.github.vrcmteam.vrcm.network.api.groups.data

import io.github.vrcmteam.vrcm.network.api.attributes.IUser
import io.github.vrcmteam.vrcm.network.api.attributes.UserStatus
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GroupMember(
    val id: String = "",
    val userId: String = "",
    val groupId: String = "",
    val membershipStatus: String = "",
    val isRepresenting: Boolean = false,
    val isSubscribedToAnnouncements: Boolean = false,
    val roleIds: List<String> = emptyList(),
    val joinedAt: String? = null,
    val user: GroupMemberLimitedUser? = null,
)

@Serializable
data class GroupMemberLimitedUser(
    override val id: String = "",
    override val displayName: String = "",
    @SerialName("iconUrl")
    val _iconUrl: String = "",
    val thumbnailUrl: String? = null,
    override val profilePicOverride: String = "",
    override val pronouns: String? = null,
    override val bio: String? = null,
    override val bioLinks: List<String> = emptyList(),
    override val tags: List<String> = emptyList(),
    override val currentAvatarImageUrl: String = "",
    override val currentAvatarThumbnailImageUrl: String? = null,
    override val currentAvatarTags: List<String> = emptyList(),
    override val userIcon: String = "",
    override val isFriend: Boolean = false,
    @SerialName("last_login")
    override val lastLogin: String? = null,
    @SerialName("last_platform")
    override val lastPlatform: String = "",
    override val status: UserStatus = UserStatus.Offline,
    override val statusDescription: String = "",
    override val developerType: String = "",
) : IUser {
    override val iconUrl: String
        get() = _iconUrl.ifBlank { thumbnailUrl ?: profilePicOverride.ifBlank { currentAvatarImageUrl } }
}
