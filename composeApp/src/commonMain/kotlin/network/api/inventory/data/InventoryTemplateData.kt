package io.github.vrcmteam.vrcm.network.api.inventory.data

import kotlinx.serialization.Serializable

/** An inventory template and the metadata needed to render it. */
@Serializable
data class InventoryTemplateData(
    val id: String = "",
    val metadata: InventoryTemplateMetadata = InventoryTemplateMetadata(),
)

/** Visual assets and optional gradient configuration for an inventory template. */
@Serializable
data class InventoryTemplateMetadata(
    val assets: List<InventoryTemplateAsset> = emptyList(),
    val gradientStart: String? = null,
    val gradientEnd: String? = null,
)

/** A typed asset reference used by an inventory template. */
@Serializable
data class InventoryTemplateAsset(
    val type: String = "",
    val url: String = "",
)
