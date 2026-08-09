package io.github.vrcmteam.vrcm.network.api.inventory

import io.github.vrcmteam.vrcm.network.api.inventory.data.InventoryTemplateData
import io.github.vrcmteam.vrcm.network.extensions.checkSuccess
import io.ktor.client.HttpClient
import io.ktor.client.request.get

/** Retrieves VRChat inventory template definitions. */
class InventoryApi(private val client: HttpClient) {
    suspend fun getTemplate(templateId: String): InventoryTemplateData {
        require(ID_PATTERN.matches(templateId)) { "Invalid inventory template ID" }

        return client.get("inventory/template/$templateId").checkSuccess()
    }

    private companion object {
        val ID_PATTERN = Regex("[A-Za-z0-9_-]+")
    }
}
