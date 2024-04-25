package io.github.vrcmteam.vrcm.network.api.friends.date

import cafe.adriel.voyager.core.lifecycle.JavaSerializable
import io.github.vrcmteam.vrcm.network.api.attributes.IUser
import io.github.vrcmteam.vrcm.network.api.attributes.UserStatus
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class FriendData(
    override val bio: String?,
    override val bioLinks: List<String> = emptyList(),
    override val currentAvatarImageUrl: String,
    override val currentAvatarTags: List<String> = emptyList(),
    override val currentAvatarThumbnailImageUrl: String,
    override val developerType: String,
    override val displayName: String,
    val friendKey: String,
    override val id: String,
    val imageUrl: String,
    val isFriend: Boolean,
    @SerialName("last_login")
    override val lastLogin: String,
    @SerialName("last_platform")
    override val lastPlatform: String,
    val location: String,
    override val profilePicOverride: String,
    override val status: UserStatus,
    override val statusDescription: String,
    override val tags: List<String> = emptyList(),
    override val userIcon: String,
): IUser, JavaSerializable