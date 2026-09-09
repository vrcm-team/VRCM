package io.github.vrcmteam.vrcm.network.api.users.data

import kotlinx.serialization.Serializable
import kotlin.time.Instant

internal enum class PlayerInteractionOverride(val apiValue: String?) {
    Default(null),
    InteractOff("interactOff"),
    InteractOn("interactOn");

    val opposite: PlayerInteractionOverride
        get() = when (this) {
            Default -> error("The default interaction setting has no opposite override")
            InteractOff -> InteractOn
            InteractOn -> InteractOff
        }

    companion object {
        fun fromApiValue(value: String): PlayerInteractionOverride? =
            entries.firstOrNull { it.apiValue == value }
    }
}

internal data class PlayerInteractionSnapshot(
    val effectiveOverride: PlayerInteractionOverride,
    val explicitOverrides: Set<PlayerInteractionOverride>,
) {
    fun isSettledAt(override: PlayerInteractionOverride): Boolean =
        override != PlayerInteractionOverride.Default &&
            effectiveOverride == override &&
            override.opposite !in explicitOverrides
}

@Serializable
internal data class PlayerInteractionModerationData(
    val created: String? = null,
    val id: String? = null,
    val targetUserId: String,
    val type: String,
)

@OptIn(kotlin.time.ExperimentalTime::class)
internal fun resolvePlayerInteractionSnapshot(
    targetUserId: String,
    moderations: List<PlayerInteractionModerationData>,
): PlayerInteractionSnapshot {
    val candidates = moderations.withIndex().mapNotNull { indexed ->
        if (indexed.value.targetUserId != targetUserId) return@mapNotNull null
        val override = PlayerInteractionOverride.fromApiValue(indexed.value.type)
            ?.takeUnless { it == PlayerInteractionOverride.Default }
            ?: return@mapNotNull null
        Candidate(indexed.index, indexed.value.created, override)
    }
    if (candidates.isEmpty()) {
        return PlayerInteractionSnapshot(
            effectiveOverride = PlayerInteractionOverride.Default,
            explicitOverrides = emptySet(),
        )
    }

    val candidatesWithTimes = candidates.map { candidate ->
        candidate to candidate.created?.let { runCatching { Instant.parse(it) }.getOrNull() }
    }
    val selected = if (candidatesWithTimes.all { it.second != null }) {
        candidatesWithTimes.maxWithOrNull(
            compareBy<Pair<Candidate, Instant?>>({ it.second }, { it.first.index }),
        )!!.first
    } else {
        candidates.last()
    }
    return PlayerInteractionSnapshot(
        effectiveOverride = selected.override,
        explicitOverrides = candidates.mapTo(linkedSetOf()) { it.override },
    )
}

private data class Candidate(
    val index: Int,
    val created: String?,
    val override: PlayerInteractionOverride,
)
