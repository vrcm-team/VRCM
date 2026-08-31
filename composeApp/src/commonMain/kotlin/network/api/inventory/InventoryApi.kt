package io.github.vrcmteam.vrcm.network.api.inventory

import io.github.vrcmteam.vrcm.network.api.inventory.data.InventoryTemplateData
import io.github.vrcmteam.vrcm.network.api.inventory.data.RewardRedemptionRequestData
import io.github.vrcmteam.vrcm.network.api.inventory.data.RewardRedemptionResult
import io.github.vrcmteam.vrcm.network.extensions.checkSuccess
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

/** Accesses inventory template definitions and reward redemption. */
class InventoryApi(private val client: HttpClient) {
    suspend fun getTemplate(templateId: String): InventoryTemplateData {
        require(ID_PATTERN.matches(templateId)) { "Invalid inventory template ID" }

        return client.get("inventory/template/$templateId").checkSuccess()
    }

    suspend fun redeemReward(code: String): List<RewardRedemptionResult> =
        client.post("reward/redeem") {
            contentType(ContentType.Application.Json)
            setBody(RewardRedemptionRequestData(code))
        }.checkSuccess()

    private companion object {
        val ID_PATTERN = Regex("[A-Za-z0-9_-]+")
    }
}
