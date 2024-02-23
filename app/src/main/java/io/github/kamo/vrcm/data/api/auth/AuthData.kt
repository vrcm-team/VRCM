package io.github.kamo.vrcm.data.api.auth

data class AuthData(
    val requiresTwoFactorAuth: List<String>?,
    val ok: Boolean?,
    val token: String?
)