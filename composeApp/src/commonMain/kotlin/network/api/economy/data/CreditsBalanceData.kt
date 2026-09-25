package io.github.vrcmteam.vrcm.network.api.economy.data

import kotlinx.serialization.Serializable

/** Current VRChat Credits balance for an authenticated account. */
@Serializable
data class CreditsBalanceData(
    val balance: Long,
)
