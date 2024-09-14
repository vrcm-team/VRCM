package io.github.vrcmteam.vrcm.presentation.screens.profile.data

import cafe.adriel.voyager.core.lifecycle.JavaSerializable
import io.github.vrcmteam.vrcm.network.api.attributes.*
import io.github.vrcmteam.vrcm.network.api.users.data.UserData

data class UserProfileVo (
    val id: String,
    val displayName: String = "",
    val status: UserStatus = UserStatus.Offline,
    val statusDescription: String = "",
    val bio: String = "",
    val bioLinks: List<String> = listOf(),
    val tags: List<String> = listOf(),
    val speakLanguages:List<String> = listOf(),
    val friendRequestStatus: FriendRequestStatus? = null,
    val profileImageUrl:String = "",
    val iconUrl:String = "",
    val isSupporter: Boolean = false,
    val isSelf:Boolean = false,
    val isFriend:Boolean = false,
    val trustRank: TrustRank = TrustRank.Visitor
): JavaSerializable{
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
        trustRank = user.trustRank
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
        trustRank = user.trustRank
    )
}
