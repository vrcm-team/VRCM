package io.github.vrcmteam.vrcm.network.websocket.data.content

import cafe.adriel.voyager.core.lifecycle.JavaSerializable
import io.github.vrcmteam.vrcm.network.api.attributes.LocationType
import io.github.vrcmteam.vrcm.network.api.attributes.UserStatus
import io.github.vrcmteam.vrcm.network.api.friends.date.FriendData
import kotlinx.serialization.Serializable

@Serializable
abstract class FriendEventsContent: JavaSerializable{
    abstract val user: UserContent?
    abstract val userId: String
}

internal fun mergeFriendPresence(
    existing: FriendData?,
    user: UserContent?,
    userId: String,
    location: String,
    travelingToLocation: String = "",
    platform: String? = null,
): FriendData? {
    val friend = user?.toFriendData(userId, location, travelingToLocation) ?: existing?.copy(
        location = location,
        travelingToLocation = travelingToLocation,
        status = if (existing.status == UserStatus.Offline && location != LocationType.Offline.value) {
            UserStatus.Active
        } else {
            existing.status
        },
    )
    return platform?.let { friend?.copy(lastPlatform = it) } ?: friend
}

internal fun UserContent.toFriendData(
    userId: String = id,
    location: String,
    travelingToLocation: String = "",
) = FriendData(
    bio = bio,
    bioLinks = bioLinks,
    currentAvatarImageUrl = currentAvatarImageUrl,
    currentAvatarTags = currentAvatarTags,
    currentAvatarThumbnailImageUrl = currentAvatarThumbnailImageUrl,
    developerType = developerType,
    displayName = displayName,
    friendKey = friendKey,
    id = userId,
    imageUrl = profileImageUrl,
    isFriend = isFriend,
    lastLogin = lastLogin,
    lastPlatform = lastPlatform,
    location = location,
    travelingToLocation = travelingToLocation,
    profilePicOverride = profilePicOverride,
    status = status,
    statusDescription = statusDescription,
    tags = tags,
    userIcon = userIcon,
    pronouns = pronouns,
)
