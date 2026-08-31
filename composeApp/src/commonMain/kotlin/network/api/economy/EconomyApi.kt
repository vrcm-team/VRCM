package io.github.vrcmteam.vrcm.network.api.economy

import io.github.vrcmteam.vrcm.network.api.economy.data.CreditsBalanceData
import io.github.vrcmteam.vrcm.network.extensions.checkSuccess
import io.ktor.client.HttpClient
import io.ktor.client.request.get

/** Retrieves the authenticated user's VRChat economy data. */
class EconomyApi(private val client: HttpClient) {
    suspend fun getCreditsBalance(userId: String): CreditsBalanceData {
        require(ID_PATTERN.matches(userId)) { "Invalid user ID" }

        return client.get("user/$userId/economy/balance").checkSuccess()
    }

    private companion object {
        val ID_PATTERN = Regex("[A-Za-z0-9_-]+")
    }
}
