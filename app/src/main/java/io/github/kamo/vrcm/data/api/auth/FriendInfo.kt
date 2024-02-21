package io.github.kamo.vrcm.data.api.auth

import com.google.gson.annotations.SerializedName
import io.github.kamo.vrcm.common.UserStatus

data class FriendInfo(
    val bio: String,
    val bioLinks: List<String> = emptyList(),
    val currentAvatarImageUrl: String,
    val currentAvatarTags: List<String> = emptyList(),
    val currentAvatarThumbnailImageUrl: String,
    val developerType: String,
    val displayName: String,
    val friendKey: String,
    val id: String,
    val imageUrl: String,
    val isFriend: Boolean,
    @SerializedName("last_login")
    val lastLogin: String,
    @SerializedName("last_platform")
    val lastPlatform: String,
    val location: String,
    val profilePicOverride: String,
    val status: UserStatus,
    val statusDescription: String,
    val tags: List<String> = emptyList(),
    val userIcon: String
)