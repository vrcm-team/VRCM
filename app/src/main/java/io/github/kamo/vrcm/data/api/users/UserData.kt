package io.github.kamo.vrcm.data.api.users

import com.google.gson.annotations.SerializedName
import io.github.kamo.vrcm.data.api.AccessType
import io.github.kamo.vrcm.data.api.UserState
import io.github.kamo.vrcm.data.api.UserStatus

data class UserData(
    val allowAvatarCopying: Boolean,
    val bio: String,
    val bioLinks: List<String>,
    val currentAvatarImageUrl: String,
    val currentAvatarTags: List<String>,
    val currentAvatarThumbnailImageUrl: String,
    @SerializedName("date_joined")
    val dateJoined: String,
    val developerType: String,
    val displayName: String,
    val friendKey: String,
    val friendRequestStatus: String,
    val id: String,
    val instanceId: String,
    val isFriend: Boolean,
    @SerializedName("last_activity")
    val lastActivity: String,
    @SerializedName("last_login")
    val lastLogin: String,
    @SerializedName("last_platform")
    val lastPlatform: String,
    val location: String,
    val note: String,
    val profilePicOverride: String,
    val state: UserState,
    val status: UserStatus,
    val statusDescription: String,
    val tags: List<String>,
    val travelingToInstance: String,
    val travelingToLocation: String,
    val travelingToWorld: String,
    val userIcon: String,
    val worldId: String
) {
    val accessType: AccessType
            get() =
            if (state == UserState.Offline) {
                error("user is offline!")
            } else {
                if (instanceId.contains(AccessType.Group.typeName)){
                    when (instanceId.substringAfter("groupAccessType(").substringBefore(")")) {
                        AccessType.GroupPublic.typeName -> AccessType.GroupPublic
                        AccessType.GroupPlus.typeName -> AccessType.GroupPlus
                        AccessType.GroupMembers.typeName -> AccessType.GroupMembers
                        else -> AccessType.Group
                    }
                }else if (instanceId.contains(AccessType.Private.typeName)) {
                    if (instanceId.contains( AccessType.InvitePlus.typeName)) AccessType.InvitePlus else AccessType.Invite
                } else if (instanceId.contains(AccessType.FriendPlus.typeName)) {
                    AccessType.FriendPlus
                }else if (instanceId.contains(AccessType.Friend.typeName)) {
                    AccessType.Friend
                } else AccessType.Public
            }
}


