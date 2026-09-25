package io.github.vrcmteam.vrcm.network.api.inventory

import io.github.vrcmteam.vrcm.network.api.inventory.data.InventoryData
import io.github.vrcmteam.vrcm.network.api.inventory.data.InventoryTemplateData
import io.github.vrcmteam.vrcm.network.api.inventory.data.RewardRedemptionRequestData
import io.github.vrcmteam.vrcm.network.api.inventory.data.RewardRedemptionResult
import io.github.vrcmteam.vrcm.network.extensions.checkSuccess
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

enum class InventoryItemType(val value: String) {
    Bundle("bundle"),
    DroneSkin("droneskin"),
    Emoji("emoji"),
    PortalSkin("portalskin"),
    Prop("prop"),
    Sticker("sticker"),
    WarpEffect("warpeffect"),
}

enum class InventorySortOrder(val value: String) {
    NewestUpdated("newest"),
    NewestCreated("newest_created"),
    OldestUpdated("oldest"),
    OldestCreated("oldest_created"),
}

/** Retrieves inventory entries and template definitions, and redeems reward codes. */
class InventoryApi(private val client: HttpClient) {
    suspend fun getInventory(
        n: Int = DEFAULT_PAGE_SIZE,
        offset: Int = 0,
        type: InventoryItemType? = null,
        archived: Boolean? = null,
        order: InventorySortOrder = InventorySortOrder.NewestUpdated,
    ): InventoryData {
        require(n in 1..MAX_PAGE_SIZE) { "Inventory page size must be between 1 and 100" }
        require(offset >= 0) { "Inventory offset must not be negative" }

        return client.get("inventory") {
            parameter("n", n)
            parameter("offset", offset)
            parameter("order", order.value)
            type?.let { parameter("types", it.value) }
            archived?.let { parameter("archived", it) }
        }.checkSuccess()
    }

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
        const val DEFAULT_PAGE_SIZE = 60
        const val MAX_PAGE_SIZE = 100
        val ID_PATTERN = Regex("[A-Za-z0-9_-]+")
    }
}
