package io.github.vrcmteam.vrcm.network.api.users.data

import io.github.vrcmteam.vrcm.network.api.attributes.*
import io.github.vrcmteam.vrcm.network.api.auth.data.CurrentUserData
import io.github.vrcmteam.vrcm.network.api.profile.data.ProfileAppearanceData
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UserData(
    val ageVerificationStatus : AgeVerificationStatus,
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
    val friendRequestStatus: FriendRequestStatus? = null,
    override val id: String,
    override val instanceId: String = "",
    override val isFriend: Boolean,
    @SerialName("last_activity")
    override val lastActivity: String,
    @SerialName("last_login")
    override val lastLogin: String,
    @SerialName("last_platform")
    override val lastPlatform: String,
    override val location: String = "",
    val note: String = "",
    /** Canonical profile icon returned by the post-PR user schema. */
    @SerialName("iconUrl")
    val profileIconUrl: String = "",
    override val profilePicOverride: String = "",
    val state: UserState,
    override val status: UserStatus,
    override val statusDescription: String,
    override val tags: List<String>,
    val travelingToInstance: String? = null,
    val travelingToLocation: String? = null,
    val travelingToWorld: String? = null,
    override val userIcon: String = "",
    val worldId: String = "",
    override val pronouns: String? = null,
) : IUser, IAccessType {
    override val iconUrl: String
        get() = profileIconUrl.ifBlank { userIcon.ifBlank { profileImageUrl } }

    override val accessType: AccessType
        get() =
            when {
                instanceId.contains(AccessType.Group.value) -> when (instanceId.substringAfter("groupAccessType(")
                    .substringBefore(")")) {
                    AccessType.GroupPublic.value -> AccessType.GroupPublic
                    AccessType.GroupPlus.value -> AccessType.GroupPlus
                    AccessType.GroupMembers.value -> AccessType.GroupMembers
                    else -> AccessType.Group
                }
                instanceId.contains(AccessType.FriendPlus.value) -> AccessType.FriendPlus
                instanceId.contains(AccessType.Friend.value) -> AccessType.Friend
                else -> AccessType.Public
            }
}

/** Keeps the fields moved to the public profile endpoint in the profile cache. */
internal fun UserData.mergeCurrentUserProfile(currentUser: CurrentUserData?): UserData {
    if (currentUser == null || currentUser.id != id) return this
    return copy(
        bio = currentUser.bio ?: bio,
        bioLinks = currentUser.bioLinks.ifEmpty { bioLinks },
        currentAvatarImageUrl = currentUser.currentAvatarImageUrl
            .ifBlank { currentAvatarImageUrl },
        currentAvatarTags = currentUser.currentAvatarTags.ifEmpty { currentAvatarTags },
        currentAvatarThumbnailImageUrl = currentUser.currentAvatarThumbnailImageUrl
            ?: currentAvatarThumbnailImageUrl,
        displayName = currentUser.displayName.ifBlank { displayName },
        profilePicOverride = currentUser.profilePicOverride
            .ifBlank { profilePicOverride },
        profileIconUrl = currentUser.profileIconUrl
            .ifBlank { profileIconUrl },
        userIcon = currentUser.userIcon.ifBlank { userIcon },
        pronouns = currentUser.pronouns ?: pronouns,
    )
}

/** Restores fields moved from `/users/{userId}` to `/profile/{userId}` by PR #600. */
internal fun UserData.mergePublicProfile(profile: ProfileAppearanceData?): UserData {
    if (profile == null || profile.id.isNotBlank() && profile.id != id) return this
    val iconUrl = profile.iconUrl.orEmpty()
    return copy(
        bio = profile.bio ?: bio,
        bioLinks = profile.bioLinks ?: bioLinks,
        currentAvatarImageUrl = currentAvatarImageUrl.ifBlank { iconUrl },
        currentAvatarThumbnailImageUrl = currentAvatarThumbnailImageUrl
            ?: iconUrl.takeIf(String::isNotBlank),
        displayName = profile.displayName?.takeIf(String::isNotBlank) ?: displayName,
        profileIconUrl = iconUrl.takeIf(String::isNotBlank) ?: profileIconUrl,
        userIcon = iconUrl.takeIf(String::isNotBlank) ?: userIcon,
        pronouns = profile.pronouns ?: pronouns,
    )
}
