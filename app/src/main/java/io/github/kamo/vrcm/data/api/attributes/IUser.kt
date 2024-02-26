package io.github.kamo.vrcm.data.api.attributes

import com.google.gson.annotations.SerializedName
import io.github.kamo.vrcm.data.api.TrustRank
import io.github.kamo.vrcm.data.api.UserStatus

interface IUser {
    val userId: String
    val displayName: String
    val bio: String
    val bioLinks: List<String>
    val tags: List<String>
    val currentAvatarImageUrl: String
    val currentAvatarThumbnailImageUrl: String
    val currentAvatarTags: List<String>
    val userIcon:String
    val profilePicOverride: String
    @get:SerializedName("last_login")
    val lastLogin: String
    @get:SerializedName("last_platform")
    val lastPlatform: String
    val status: UserStatus
    val statusDescription: String
    val developerType: String

    val speakLanguages:List<String>
        get() = tags.filter { it.startsWith("language_") }.map { it.removePrefix("language_") }
    val imageUrl:String
        get() = profilePicOverride.ifBlank { currentAvatarImageUrl }

    val iconUrl:String
        get() = userIcon.ifBlank { currentAvatarThumbnailImageUrl }
    val isSupporter: Boolean
        get() = tags.contains("system_supporter")

    val trustRank: TrustRank
        get() = TrustRank.entries.firstOrNull { tags.contains(it.value) } ?:TrustRank.Visitor
}