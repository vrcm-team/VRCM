package io.github.vrcmteam.vrcm.network.api.profile.data

import kotlinx.serialization.Serializable

/** Appearance assets applied to a VRChat user profile. */
@Serializable
data class ProfileAppearanceData(
    val id: String = "",
    val iconFrame: String? = null,
    val profileEffect: String? = null,
    val nameplateEffect: String? = null,
    /** Public profile fields returned by the same endpoint. */
    val bio: String? = null,
    val bioLinks: List<String>? = null,
    val displayName: String? = null,
    val iconUrl: String? = null,
    val pronouns: String? = null,
    val bannerUrl: String? = null,
)
