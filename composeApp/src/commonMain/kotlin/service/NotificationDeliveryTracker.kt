package io.github.vrcmteam.vrcm.service

/**
 * Tracks the notification event represented by legacy and V2 Pipeline payloads.
 *
 * V2 inbox items can be updated in place. A changed version alone may only mean "seen", while a
 * changed related notification ID represents a new underlying event such as another Boop. VRChat
 * can also emit the legacy counterpart for that related ID, so both forms share one delivery key.
 */
internal class NotificationDeliveryTracker {
    private data class V2State(
        val version: Int,
        val relatedId: String?,
    )

    private val knownV2 = mutableMapOf<String, V2State>()
    private val deliveredEventKeys = mutableSetOf<String>()

    fun reset() {
        knownV2.clear()
        deliveredEventKeys.clear()
    }

    fun shouldDeliverLegacy(id: String): Boolean = deliveredEventKeys.add(id)

    fun shouldDeliverV2(
        id: String,
        version: Int,
        relatedId: String?,
        seedOnly: Boolean = false,
        isPipelineEvent: Boolean = false,
    ): Boolean {
        val previous = knownV2[id]
        if (previous != null && version < previous.version) return false

        val normalizedRelatedId = relatedId?.takeIf(String::isNotBlank)
        val next = V2State(
            version = version,
            relatedId = normalizedRelatedId ?: previous?.relatedId,
        )
        knownV2[id] = next

        if (seedOnly) return false
        val representsIncomingEvent = isPipelineEvent || previous == null ||
            (normalizedRelatedId != null && normalizedRelatedId != previous.relatedId)
        if (!representsIncomingEvent) return false

        return deliveredEventKeys.add(normalizedRelatedId ?: "$id@$version")
    }
}
