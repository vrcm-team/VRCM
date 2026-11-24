package io.github.vrcmteam.vrcm.network.websocket.data.content

import io.github.vrcmteam.vrcm.network.api.attributes.UserStatus
import io.github.vrcmteam.vrcm.network.api.friends.date.FriendData
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class FriendUpdateContent(
    val user: SocketUserUpdate,
)

/** Pipeline profile updates can omit fields unrelated to the change. */
@Serializable
internal data class SocketUserUpdate(
    val id: String,
    val allowAvatarCopying: Boolean? = null,
    val bio: String? = null,
    val bioLinks: List<String>? = null,
    val currentAvatar: String? = null,
    val currentAvatarImageUrl: String? = null,
    val currentAvatarTags: List<String>? = null,
    val currentAvatarThumbnailImageUrl: String? = null,
    val displayName: String? = null,
    @SerialName("iconUrl")
    val profileIconUrl: String? = null,
    val profilePicOverride: String? = null,
    @SerialName("last_activity")
    val lastActivity: String? = null,
    @SerialName("last_login")
    val lastLogin: String? = null,
    @SerialName("last_platform")
    val lastPlatform: String? = null,
    val state: String? = null,
    val status: UserStatus? = null,
    val statusDescription: String? = null,
    val tags: List<String>? = null,
    val userIcon: String? = null,
    val pronouns: String? = null,
    val bannerType: String? = null,
    val bannerUrl: String? = null,
) {
    fun mergeWith(friend: FriendData): FriendData = friend.copy(
        bio = bio ?: friend.bio,
        bioLinks = bioLinks ?: friend.bioLinks,
        currentAvatarImageUrl = currentAvatarImageUrl ?: friend.currentAvatarImageUrl,
        currentAvatarTags = currentAvatarTags ?: friend.currentAvatarTags,
        currentAvatarThumbnailImageUrl = currentAvatarThumbnailImageUrl ?: friend.currentAvatarThumbnailImageUrl,
        displayName = displayName ?: friend.displayName,
        profilePicOverride = profilePicOverride ?: friend.profilePicOverride,
        profileIconUrl = profileIconUrl ?: friend.profileIconUrl,
        status = status ?: friend.status,
        statusDescription = statusDescription ?: friend.statusDescription,
        tags = tags ?: friend.tags,
        userIcon = userIcon ?: friend.userIcon,
        pronouns = pronouns ?: friend.pronouns,
        bannerType = bannerType ?: friend.bannerType,
        bannerUrl = bannerUrl ?: friend.bannerUrl,
    )
}
