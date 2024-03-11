package io.github.vrcmteam.vrcm.network.api.auth.data

import kotlinx.serialization.Serializable

@Serializable
data class AccountDeletionLog(
    val dateTime: String,
    val deletionScheduled: String?,
    val message: String
)