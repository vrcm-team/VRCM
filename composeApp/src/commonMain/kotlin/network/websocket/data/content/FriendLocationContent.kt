package io.github.vrcmteam.vrcm.network.websocket.data.content

import io.github.vrcmteam.vrcm.network.api.friends.date.FriendData
import kotlinx.serialization.Serializable

@Serializable
data class FriendLocationContent(
    val canRequestInvite: Boolean,
    val location: String,
    val travelingToLocation: String,
    override val user: UserContent,
    override val userId: String,
    val worldId: String
): FriendEventsContent() {
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

