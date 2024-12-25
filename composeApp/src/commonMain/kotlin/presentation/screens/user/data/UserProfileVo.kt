package io.github.vrcmteam.vrcm.presentation.screens.user.data

import cafe.adriel.voyager.core.lifecycle.JavaSerializable
import io.github.vrcmteam.vrcm.network.api.attributes.*
import io.github.vrcmteam.vrcm.network.api.users.data.UserData

data class UserProfileVo(
    override val id: String,
    override val displayName: String = "",
    override val status: UserStatus = UserStatus.Offline,
    override val statusDescription: String = "",
    override val bio: String = "",
    override val bioLinks: List<String> = listOf(),
    override val tags: List<String> = listOf(),
    override val speakLanguages: List<String> = listOf(),
    val friendRequestStatus: FriendRequestStatus? = null,
    override val profileImageUrl: String = "",
    override val iconUrl: String = "",
    override val isSupporter: Boolean = false,
    val isSelf: Boolean = false,
    override val isFriend: Boolean = false,
    override val trustRank: TrustRank = TrustRank.Visitor,
    override val currentAvatarImageUrl: String = "",
    override val currentAvatarThumbnailImageUrl: String? = "",
    override val currentAvatarTags: List<String> = listOf(),
    override val userIcon: String = "",
    override val profilePicOverride: String = "",
    override val lastLogin: String? = "",
    override val lastPlatform: String = "",
    override val developerType: String = "",
) : IUser, JavaSerializable {
    constructor(user: IUser): this(
        id = user.id,
        displayName = user.displayName,
        status = user.status,
        statusDescription = user.statusDescription,
        bio = user.bio.orEmpty(),
        bioLinks = user.bioLinks,
        tags = user.tags,
        speakLanguages = user.speakLanguages,
        profileImageUrl = user.profileImageUrl,
        iconUrl = user.iconUrl,
        isSupporter = user.isSupporter,
        isFriend = user.isFriend,
        trustRank = user.trustRank,
        currentAvatarImageUrl = user.currentAvatarImageUrl,
        currentAvatarThumbnailImageUrl = user.currentAvatarThumbnailImageUrl,
        currentAvatarTags = user.currentAvatarTags,
        userIcon = user.userIcon,
        profilePicOverride = user.profilePicOverride,
        lastLogin = user.lastLogin,
        lastPlatform = user.lastPlatform,
        developerType = user.developerType
    )

    constructor(user: UserData): this(
        id = user.id,
        displayName = user.displayName,
        status = if (user.state == UserState.Offline) UserStatus.Offline else user.status,
        statusDescription = user.statusDescription,
        bio = user.bio.orEmpty(),
        bioLinks = user.bioLinks,
        tags = user.tags,
        speakLanguages = user.speakLanguages,
        friendRequestStatus = user.friendRequestStatus,
        profileImageUrl = user.profileImageUrl,
        iconUrl = user.iconUrl,
        isSupporter = user.isSupporter,
        isSelf = !user.isFriend && user.friendKey.isNotEmpty(),
        isFriend = user.isFriend,
        trustRank = user.trustRank,
        currentAvatarImageUrl = user.currentAvatarImageUrl,
        currentAvatarThumbnailImageUrl = user.currentAvatarThumbnailImageUrl,
        currentAvatarTags = user.currentAvatarTags,
        userIcon = user.userIcon,
        profilePicOverride = user.profilePicOverride,
        lastLogin = user.lastLogin,
        lastPlatform = user.lastPlatform,
        developerType = user.developerType
    )

}
