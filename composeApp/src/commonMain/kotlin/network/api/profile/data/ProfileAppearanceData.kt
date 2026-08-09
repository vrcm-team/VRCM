package io.github.vrcmteam.vrcm.network.api.profile.data

import kotlinx.serialization.Serializable

/** Appearance assets applied to a VRChat user profile. */
@Serializable
data class ProfileAppearanceData(
    val id: String = "",
    val iconFrame: String? = null,
    val profileEffect: String? = null,
    val nameplateEffect: String? = null,
)
