package io.github.vrcmteam.vrcm.network.api.inventory.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** A server-paged inventory result for the authenticated account. */
@Serializable
data class InventoryData(
    val data: List<InventoryItemData> = emptyList(),
    val totalCount: Int? = null,
)

/** Fields used to render an inventory entry; optional values tolerate older item variants. */
@Serializable
data class InventoryItemData(
    val id: String = "",
    val name: String? = null,
    val description: String? = null,
    val imageUrl: String? = null,
    val itemType: String? = null,
    val itemTypeLabel: String? = null,
    val isArchived: Boolean? = null,
    val expiryDate: String? = null,
    val quantifiable: Boolean? = null,
    val metadata: InventoryItemMetadataData? = null,
    @SerialName("created_at")
    val createdAt: String? = null,
    @SerialName("updated_at")
    val updatedAt: String? = null,
) {
    val displayImageUrl: String?
        get() = imageUrl?.takeIf(String::isNotBlank)
            ?: metadata?.imageUrl?.takeIf(String::isNotBlank)
}

@Serializable
data class InventoryItemMetadataData(
    val imageUrl: String? = null,
    val animated: Boolean? = null,
)
