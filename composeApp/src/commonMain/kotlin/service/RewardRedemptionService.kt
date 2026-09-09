package io.github.vrcmteam.vrcm.service

import io.github.vrcmteam.vrcm.core.shared.AccountSessionToken
import io.github.vrcmteam.vrcm.network.api.inventory.InventoryApi
import io.github.vrcmteam.vrcm.network.api.inventory.data.RewardRedemptionResult

internal data class AuthenticatedRewardRedemptionResponse(
    val result: Result<List<RewardRedemptionResult>>,
    val sessionToken: AccountSessionToken,
)

internal fun interface RewardCodeRedeemer {
    suspend fun redeem(
        sessionToken: AccountSessionToken,
        code: String,
    ): AuthenticatedRewardRedemptionResponse?
}

internal class NetworkRewardCodeRedeemer(
    private val authService: AuthService,
    private val inventoryApi: InventoryApi,
) : RewardCodeRedeemer {
    override suspend fun redeem(
        sessionToken: AccountSessionToken,
        code: String,
    ): AuthenticatedRewardRedemptionResponse? =
        authService.runSessionBoundCatching(sessionToken) {
            inventoryApi.redeemReward(code)
        }?.let { response ->
            AuthenticatedRewardRedemptionResponse(
                result = response.result,
                sessionToken = response.sessionToken,
            )
        }
}
