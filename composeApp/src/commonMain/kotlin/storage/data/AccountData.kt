package io.github.vrcmteam.vrcm.storage.data

import kotlinx.serialization.Serializable

@Serializable
data class AccountData(
    val username: String,
    val password: String,
)
