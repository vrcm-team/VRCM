package io.github.vrcmteam.vrcm.data.api.users

import io.github.vrcmteam.vrcm.data.api.AccessType
import io.github.vrcmteam.vrcm.data.api.UserState
import io.github.vrcmteam.vrcm.data.api.UserStatus
import io.github.vrcmteam.vrcm.data.api.attributes.IAccessType
import io.github.vrcmteam.vrcm.data.api.attributes.IUser
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UserData(
    val allowAvatarCopying: Boolean,
    override val bio: String,
    override val bioLinks: List<String>,
    override val currentAvatarImageUrl: String,
    override val currentAvatarTags: List<String>,
    override val currentAvatarThumbnailImageUrl: String,
    @SerialName("date_joined")
    val dateJoined: String,
    override val developerType: String,
    override val displayName: String,
    val friendKey: String,
    val friendRequestStatus: String,
    override val id: String,
    override val instanceId: String,
    val isFriend: Boolean,
    @SerialName("last_activity")
    val lastActivity: String,
    @SerialName("last_login")
    override val lastLogin: String,
    @SerialName("last_platform")
    override val lastPlatform: String,
    val location: String,
    val note: String,
    override val profilePicOverride: String,
    val state: UserState,
    override val status: UserStatus,
    override val statusDescription: String,
    override val tags: List<String>,
    val travelingToInstance: String,
    val travelingToLocation: String,
    val travelingToWorld: String,
    override val userIcon: String,
    val worldId: String,
) : IUser, IAccessType {
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

            instanceId.contains(AccessType.Private.value) -> AccessType.Private
            instanceId.contains(AccessType.FriendPlus.value) -> AccessType.FriendPlus
            instanceId.contains(AccessType.Friend.value) -> AccessType.Friend
            else -> AccessType.Public
        }
}




