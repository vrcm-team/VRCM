package io.github.kamo.vrcm.data.api.auth

import com.google.gson.annotations.SerializedName
import io.github.kamo.vrcm.data.api.UserStatus

data class UserInfo(
    val acceptedPrivacyVersion: Int,
    val acceptedTOSVersion: Int,
    val accountDeletionDate: String,
    val accountDeletionLog: List<AccountDeletionLog>,
    val activeFriends: List<String>,
    val allowAvatarCopying: Boolean,
    val bio: String,
    val bioLinks: List<String>,
    val currentAvatar: String,
    val currentAvatarAssetUrl: String,
    val currentAvatarImageUrl: String,
    val currentAvatarTags: List<String>,
    val currentAvatarThumbnailImageUrl: String,
    @SerializedName("date_joined")
    val dateJoined: String,
    val developerType: String,
    val displayName: String,
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
    val id: String,
    val isFriend: Boolean,
    @SerializedName("last_activity")
    val lastActivity: String,
    @SerializedName("last_login")
    val lastLogin: String,
    @SerializedName("last_platform")
    val lastPlatform: String,
    val obfuscatedEmail: String,
    val obfuscatedPendingEmail: String,
    val oculusId: String,
    val offlineFriends: List<String>,
    val onlineFriends: List<String>,
    val pastDisplayNames: List<PastDisplayName>,
    val picoId: String,
    val presence: Presence,
    val profilePicOverride: String,
    val state: String,
    val status: UserStatus,
    val statusDescription: String,
    val statusFirstTime: Boolean,
    val statusHistory: List<String>,
    val steamDetails: SteamDetails,
    val steamId: String,
    val tags: List<String>,
    val twoFactorAuthEnabled: Boolean,
    val twoFactorAuthEnabledDate: String,
    val unsubscribe: Boolean,
    @SerializedName("updated_at")
    val updatedAt: String,
    val userIcon: String,
    val userLanguage: String,
    val userLanguageCode: String,
    val username: String,
    val viveId: String
)



