package io.github.kamo.vrcm.data.api.auth

data class AccountDeletionLog(
    val dateTime: String,
    val deletionScheduled: String,
    val message: String
)