package io.github.vrcmteam.vrcm.storage.data

import kotlinx.serialization.Serializable

@Serializable
data class CookiesData(
    val auth: String,
    val twoFactorAuth: String
)
