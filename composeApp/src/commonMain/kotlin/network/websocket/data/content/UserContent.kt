package network.websocket.data.content

import cafe.adriel.voyager.core.lifecycle.JavaSerializable
import io.github.vrcmteam.vrcm.network.api.attributes.IUser
import io.github.vrcmteam.vrcm.network.api.attributes.UserStatus
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UserContent(
    val allowAvatarCopying: Boolean,
    override val bio: String,
    override val bioLinks: List<String>,
    override val currentAvatarImageUrl: String,
    override val currentAvatarTags: List<String>,
    override val currentAvatarThumbnailImageUrl: String,
    @SerialName("date_joined")
    val dateJoined: String,
    override val developerType: String,
    override val displayName: String,
    val friendKey: String,
    override val id: String,
    val isFriend: Boolean,
    @SerialName("last_activity")
    val lastActivity: String,
    @SerialName("last_login")
    override val lastLogin: String,
    @SerialName("last_platform")
    override val lastPlatform: String,
    override val profilePicOverride: String,
    val state: String,
    override val status: UserStatus,
    override val statusDescription: String,
    override val tags: List<String>,
    override val userIcon: String
): IUser, JavaSerializable