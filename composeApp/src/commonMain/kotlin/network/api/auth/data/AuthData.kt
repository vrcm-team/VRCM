package io.github.vrcmteam.vrcm.network.api.auth.data

import kotlinx.serialization.Serializable

@Serializable
data class AuthData(
    val requiresTwoFactorAuth: List<String>? = null,
    val ok: Boolean? = null,
    val token: String? = null
)