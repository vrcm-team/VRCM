package io.github.kamo.vrcm.data.api.auth

import com.google.gson.annotations.SerializedName
import io.github.kamo.vrcm.data.api.UserStatus
import io.github.kamo.vrcm.data.api.attributes.IUser

data class FriendData(
    override val bio: String,
    override val bioLinks: List<String> = emptyList(),
    override val currentAvatarImageUrl: String,
    override val currentAvatarTags: List<String> = emptyList(),
    override val currentAvatarThumbnailImageUrl: String,
    override val developerType: String,
    override val displayName: String,
    val friendKey: String,
    val id: String,
    override val imageUrl: String,
    val isFriend: Boolean,
    @SerializedName("last_login")
    override val lastLogin: String,
    @SerializedName("last_platform")
    override val lastPlatform: String,
    val location: String,
    override val profilePicOverride: String,
    override val status: UserStatus,
    override val statusDescription: String,
    override val tags: List<String> = emptyList(),
    override val userIcon: String,
    override val userId: String = id
):IUser