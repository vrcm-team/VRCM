package io.github.vrcmteam.vrcm.network.api.auth.data

import io.github.vrcmteam.vrcm.network.api.attributes.AgeVerificationStatus
import io.github.vrcmteam.vrcm.network.api.attributes.IUser
import io.github.vrcmteam.vrcm.network.api.attributes.UserStatus
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CurrentUserData(
    val ageVerificationStatus: AgeVerificationStatus,
    val ageVerified: Boolean,
    val acceptedPrivacyVersion: Int,
    val acceptedTOSVersion: Int,
    val accountDeletionDate: String?,
    val accountDeletionLog: List<AccountDeletionLog>?,
    val activeFriends: List<String>,
    val allowAvatarCopying: Boolean,
    override val bio: String? = null,
    /** Optional on the auth response; profile links are not guaranteed by the public user schema. */
    override val bioLinks: List<String> = emptyList(),
    val currentAvatar: String,
    val currentAvatarAssetUrl: String?,
    override val currentAvatarImageUrl: String = "",
    override val currentAvatarTags: List<String> = emptyList(),
    override val currentAvatarThumbnailImageUrl: String? = null,
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
    override val isFriend: Boolean,
    val isBoopingEnabled: Boolean? = null,
    @SerialName("last_activity")
    override val lastActivity: String,
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
    /** Optional on the auth response; [IUser.profileImageUrl] falls back to the current avatar. */
    override val profilePicOverride: String = "",
    /** Canonical profile icon returned by the post-PR user schema. */
    @SerialName("iconUrl")
    val profileIconUrl: String = "",
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
    /** Optional on the auth response; [IUser.iconUrl] falls back to the profile image. */
    override val userIcon: String = "",
    val userLanguage: String?,
    val userLanguageCode: String?,
    val username: String,
    val viveId: String,
    override val pronouns: String?,
    val bannerType: String? = null,
    val bannerUrl: String? = null,
): IUser {
    override val iconUrl: String
        get() = profileIconUrl.ifBlank { userIcon.ifBlank { profileImageUrl } }

    override val location: String
        get() = presenceLocation(presence.world, presence.instance)
}

internal fun presenceLocation(world: String, instance: String): String = when {
    instance.startsWith("wrld_") -> instance
    world.startsWith("wrld_") && instance.isNotBlank() && instance != "offline" -> "$world:$instance"
    else -> instance
}
