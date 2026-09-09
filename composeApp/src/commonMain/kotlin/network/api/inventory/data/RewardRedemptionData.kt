package io.github.vrcmteam.vrcm.network.api.inventory.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
internal data class RewardRedemptionRequestData(
    val code: String,
)

/** One response entry returned when a reward code is redeemed. */
@Serializable
data class RewardRedemptionResult(
    val redeemedRewards: List<RewardRedemption> = emptyList(),
    val redemptionCode: String? = null,
)

/** A typed reward. Unknown future types remain available through [type]. */
@Serializable
data class RewardRedemption(
    val data: RewardRedemptionPayload = RewardRedemptionPayload(),
    val type: String = "",
)

/** The type-specific payload nested inside a redeemed reward. */
@Serializable
data class RewardRedemptionPayload(
    val badge: RewardBadge? = null,
    val item: RewardInventoryItem? = null,
)

/** Badge fields currently returned by the reward endpoint. */
@Serializable
data class RewardBadge(
    val id: String = "",
    val name: String = "",
    val description: String = "",
    val imageUrl: String = "",
    val type: String = "",
    val createdAt: String? = null,
    val createdBy: String? = null,
    val fileName: String? = null,
    val hidden: Boolean? = null,
    val isLocalizationEnabled: Boolean? = null,
    val machineName: String? = null,
    val updatedAt: String? = null,
)

/** Inventory template fields currently returned for item rewards. */
@Serializable
data class RewardInventoryItem(
    val id: String = "",
    val name: String = "",
    val description: String = "",
    val imageUrl: String = "",
    val itemType: String = "",
    val itemTypeLabel: String = "",
    val attribution: JsonElement? = null,
    val authorId: String? = null,
    val collections: List<String> = emptyList(),
    @SerialName("created_at")
    val createdAt: String? = null,
    val defaultAttributes: JsonElement? = null,
    val dropStatus: String? = null,
    val equipSlots: List<String> = emptyList(),
    val flags: List<String> = emptyList(),
    val metadata: JsonElement? = null,
    val notificationDetails: JsonElement? = null,
    val status: String? = null,
    val tags: List<String> = emptyList(),
    @SerialName("updated_at")
    val updatedAt: String? = null,
    val validateUserAttributes: Boolean? = null,
)
