package io.github.vrcmteam.vrcm.data.api.auth

import kotlinx.serialization.Serializable

@Serializable
data class AccountDeletionLog(
    val dateTime: String,
    val deletionScheduled: String,
    val message: String
)