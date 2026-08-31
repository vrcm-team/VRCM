package io.github.vrcmteam.vrcm.network.api.users.data

import io.github.vrcmteam.vrcm.network.api.attributes.AgeVerificationStatus
import io.github.vrcmteam.vrcm.network.api.attributes.UserStatus
import kotlinx.serialization.Serializable

@Serializable
data class UpdateUserInfoData(
    val ageVerificationStatus: AgeVerificationStatus? = null,
    val bio: String? = null,
    val bioLinks: List<String>? = null,
    val isBoopingEnabled: Boolean? = null,
    val status: UserStatus? = null,
    val statusDescription: String? = null,
    val pronouns: String? = null,
    val userIcon: String? = null,
    val email: String? = null,
    val password: String? = null,
    val currentPassword: String? = null,
)
