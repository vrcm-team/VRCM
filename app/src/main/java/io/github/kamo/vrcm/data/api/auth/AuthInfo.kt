package io.github.kamo.vrcm.data.api.auth

data class AuthInfo(
    val requiresTwoFactorAuth: List<String>?,
    val ok: Boolean?,
    val token: String?
)