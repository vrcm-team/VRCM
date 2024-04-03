package network.websocket.data.content

import cafe.adriel.voyager.core.lifecycle.JavaSerializable
import io.github.vrcmteam.vrcm.network.api.attributes.IUser
import io.github.vrcmteam.vrcm.network.api.attributes.UserStatus
import io.github.vrcmteam.vrcm.network.api.friends.date.FriendData
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class FriendLocationContent(
    val canRequestInvite: Boolean,
    val location: String,
    val travelingToLocation: String,
    val user: FriendLocationUser,
    val userId: String,
    val worldId: String
): JavaSerializable{
    fun toFriendData(): FriendData {
        return FriendData(
            bio = user.bio,
            bioLinks = user.bioLinks,
            currentAvatarImageUrl = user.currentAvatarImageUrl,
            currentAvatarTags = user.currentAvatarTags,
            currentAvatarThumbnailImageUrl = user.currentAvatarThumbnailImageUrl,
            developerType = user.developerType,
            displayName = user.displayName,
            friendKey = user.friendKey,
            id = userId,
            imageUrl = user.profileImageUrl,
            isFriend = user.isFriend,
            lastLogin = user.lastLogin,
            lastPlatform = user.lastPlatform,
            location = location,
            profilePicOverride = user.profilePicOverride,
            status = user.status,
            statusDescription = user.statusDescription,
            tags = user.tags,
            userIcon = user.userIcon
        )
    }
}

@Serializable
data class FriendLocationUser(
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
    override val id: String,
    val isFriend: Boolean,
    @SerialName("last_activity")
    val lastActivity: String,
    @SerialName("last_login")
    override val lastLogin: String,
    @SerialName("last_platform")
    override val lastPlatform: String,
    override val profilePicOverride: String,
    val state: String,
    override val status: UserStatus,
    override val statusDescription: String,
    override val tags: List<String>,
    override val userIcon: String
): IUser, JavaSerializable