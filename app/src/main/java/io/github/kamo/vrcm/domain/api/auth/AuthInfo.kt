package io.github.kamo.vrcm.domain.api.auth

data class AuthInfo(
    val requiresTwoFactorAuth: List<String>?,
    val ok: Boolean?,
    val token: String?
)