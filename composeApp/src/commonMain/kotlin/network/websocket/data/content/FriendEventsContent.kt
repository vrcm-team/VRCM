package io.github.vrcmteam.vrcm.network.websocket.data.content

import io.github.vrcmteam.vrcm.network.api.attributes.LocationType
import io.github.vrcmteam.vrcm.network.api.attributes.UserStatus
import io.github.vrcmteam.vrcm.network.api.friends.date.FriendData
import kotlinx.serialization.Serializable

@Serializable
abstract class FriendEventsContent{
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
    val friend = user?.toFriendData(userId, location, travelingToLocation, existing) ?: existing?.copy(
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
    existing: FriendData? = null,
) = FriendData(
    bio = bio,
    bioLinks = bioLinks,
    currentAvatarImageUrl = currentAvatarImageUrl.ifBlank { existing?.currentAvatarImageUrl.orEmpty() },
    currentAvatarTags = currentAvatarTags,
    currentAvatarThumbnailImageUrl = currentAvatarThumbnailImageUrl ?: existing?.currentAvatarThumbnailImageUrl,
    developerType = developerType,
    displayName = displayName,
    friendKey = friendKey,
    id = userId,
    imageUrl = profileImageUrl,
    profileIconUrl = profileIconUrl.ifBlank { existing?.profileIconUrl.orEmpty() },
    isFriend = isFriend,
    lastLogin = lastLogin,
    lastActivity = lastActivity,
    lastPlatform = lastPlatform,
    location = location,
    travelingToLocation = travelingToLocation,
    profilePicOverride = profilePicOverride,
    status = status,
    statusDescription = statusDescription,
    tags = tags,
    userIcon = userIcon,
    pronouns = pronouns,
    bannerType = bannerType ?: existing?.bannerType,
    bannerUrl = bannerUrl ?: existing?.bannerUrl,
)
