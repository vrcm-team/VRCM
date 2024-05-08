package io.github.vrcmteam.vrcm.presentation.screens.profile.data

import cafe.adriel.voyager.core.lifecycle.JavaSerializable
import io.github.vrcmteam.vrcm.network.api.attributes.IUser
import io.github.vrcmteam.vrcm.network.api.attributes.TrustRank
import io.github.vrcmteam.vrcm.network.api.attributes.UserState
import io.github.vrcmteam.vrcm.network.api.attributes.UserStatus
import io.github.vrcmteam.vrcm.network.api.users.data.UserData

data class UserProfileVo (
    val id: String,
    val displayName: String,
    val status: UserStatus,
    val statusDescription: String,
    val bio: String,
    val bioLinks: List<String>,
    val tags: List<String>,
    val speakLanguages:List<String>,
    val profileImageUrl:String,
    val iconUrl:String,
    val isSupporter: Boolean,
    val trustRank: TrustRank
): JavaSerializable{
    constructor(user: IUser): this(
        id = user.id,
        displayName = user.displayName,
        status = if (user is UserData && user.state == UserState.Offline) UserStatus.Offline else user.status,
        statusDescription = user.statusDescription,
        bio = user.bio.orEmpty(),
        bioLinks = user.bioLinks,
        tags = user.tags,
        speakLanguages = user.speakLanguages,
        profileImageUrl = user.profileImageUrl,
        iconUrl = user.iconUrl,
        isSupporter = user.isSupporter,
        trustRank = user.trustRank
    )
}
