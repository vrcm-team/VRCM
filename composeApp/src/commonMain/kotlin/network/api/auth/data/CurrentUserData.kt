package io.github.vrcmteam.vrcm.network.api.auth.data

import cafe.adriel.voyager.core.lifecycle.JavaSerializable
import io.github.vrcmteam.vrcm.network.api.attributes.IUser
import io.github.vrcmteam.vrcm.network.api.attributes.UserStatus
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CurrentUserData(
    val acceptedPrivacyVersion: Int,
    val acceptedTOSVersion: Int,
    val accountDeletionDate: String?,
    val accountDeletionLog: List<AccountDeletionLog>?,
    val activeFriends: List<String>,
    val allowAvatarCopying: Boolean,
    override val bio: String?,
    override val bioLinks: List<String>,
    val currentAvatar: String,
    val currentAvatarAssetUrl: String,
    override val currentAvatarImageUrl: String,
    override val currentAvatarTags: List<String>,
    override val currentAvatarThumbnailImageUrl: String,
    @SerialName("date_joined")
    val dateJoined: String,
    override val developerType: String,
    override val displayName: String,
    val emailVerified: Boolean,
    val fallbackAvatar: String,
    val friendGroupNames: List<String>,
    val friendKey: String,
    val friends: List<String>,
    val googleId: String,
    val hasBirthday: Boolean,
    val hasEmail: Boolean,
    val hasLoggedInFromClient: Boolean,
    val hasPendingEmail: Boolean,
    val hideContentFilterSettings: Boolean,
    val homeLocation: String,
    override val id: String,
    val isFriend: Boolean,
    @SerialName("last_activity")
    val lastActivity: String,
    @SerialName("last_login")
    override val lastLogin: String,
    @SerialName("last_platform")
    override val lastPlatform: String,
    val obfuscatedEmail: String,
    val obfuscatedPendingEmail: String,
    val oculusId: String,
    val offlineFriends: List<String>,
    val onlineFriends: List<String>,
    val pastDisplayNames: List<PastDisplayName>,
    val picoId: String,
    val presence: Presence,
    override val profilePicOverride: String,
    val state: String,
    override val status: UserStatus,
    override val statusDescription: String,
    val statusFirstTime: Boolean,
    val statusHistory: List<String>,
    val steamDetails: SteamDetails,
    val steamId: String,
    override val tags: List<String>,
    val twoFactorAuthEnabled: Boolean,
    val twoFactorAuthEnabledDate: String?,
    val unsubscribe: Boolean,
    @SerialName("updated_at")
    val updatedAt: String,
    override val userIcon: String,
    val userLanguage: String?,
    val userLanguageCode: String?,
    val username: String,
    val viveId: String
):IUser, JavaSerializable