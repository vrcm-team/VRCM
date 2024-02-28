package io.github.vrcmteam.vrcm.data.api.auth

import kotlinx.serialization.Serializable

@Serializable
data class AuthData(
    val requiresTwoFactorAuth: List<String>? = null,
    val ok: Boolean? = null,
    val token: String? = null
)