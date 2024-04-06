package network.websocket.data.content

import io.github.vrcmteam.vrcm.network.api.attributes.LocationType
import io.github.vrcmteam.vrcm.network.api.friends.date.FriendData
import io.github.vrcmteam.vrcm.network.websocket.data.content.FriendEventsContent
import kotlinx.serialization.Serializable

@Serializable
data class FriendActiveContent(
    override val user: UserContent,
    override val userId: String
): FriendEventsContent(){
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
            location = LocationType.Offline.value,
            profilePicOverride = user.profilePicOverride,
            status = user.status,
            statusDescription = user.statusDescription,
            tags = user.tags,
            userIcon = user.userIcon
        )
    }
}