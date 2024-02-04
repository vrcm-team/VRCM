package io.github.kamo.vrcm.data.api.auth

data class FriendInfo(
    val bio: String,
    val bioLinks: List<String>,
    var currentAvatarImageUrl: String,
    val currentAvatarTags: List<String>,
    var currentAvatarThumbnailImageUrl: String,
    val developerType: String,
    val displayName: String,
    val friendKey: String,
    val id: String,
    var imageUrl: String,
    val isFriend: Boolean,
    val last_login: String,
    val last_platform: String,
    val location: String,
    val profilePicOverride: String,
    val status: String,
    val statusDescription: String,
    val tags: List<String>,
    val userIcon: String
)