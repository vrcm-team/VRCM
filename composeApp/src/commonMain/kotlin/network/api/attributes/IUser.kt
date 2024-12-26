package io.github.vrcmteam.vrcm.network.api.attributes


import io.github.vrcmteam.vrcm.network.api.files.FileApi
import kotlinx.serialization.SerialName

/**
 * 用户持有属性的抽象
 */
interface IUser {
    val id: String
    val displayName: String
    val bio: String?
    val bioLinks: List<String>
    val tags: List<String>
    val currentAvatarImageUrl: String
    val currentAvatarThumbnailImageUrl: String?
    val currentAvatarTags: List<String>
    val userIcon:String
    val isFriend: Boolean
    val profilePicOverride: String
    @SerialName("last_login")
    val lastLogin: String?
    @SerialName("last_platform")
    val lastPlatform: String
    val status: UserStatus
    val statusDescription: String
    val developerType: String

    val speakLanguages:List<String>
        get() = tags.filter { it.startsWith("language_") }.map { it.removePrefix("language_") }

    val profileImageUrl:String
        get() = profilePicOverride.ifBlank { currentAvatarImageUrl }.let { FileApi.convertFileUrl(it) }

    val iconUrl:String
        get() = userIcon.ifBlank { profileImageUrl }

    val isSupporter: Boolean
        get() = tags.contains("system_supporter")

    val trustRank: TrustRank
        get() = TrustRank.fromValue(tags)
}