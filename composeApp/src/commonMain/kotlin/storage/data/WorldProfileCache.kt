package io.github.vrcmteam.vrcm.storage.data

import io.github.vrcmteam.vrcm.network.api.worlds.data.WorldData
import kotlinx.serialization.Serializable

@Serializable
data class WorldProfileCache(
    val world: WorldData,
    val cachedAtEpochMilliseconds: Long,
) {
    fun isExpired(
        nowEpochMilliseconds: Long,
        maxAgeMilliseconds: Long = MAX_AGE_MILLISECONDS,
    ): Boolean = nowEpochMilliseconds - cachedAtEpochMilliseconds >= maxAgeMilliseconds

    companion object {
        const val MAX_AGE_MILLISECONDS: Long = 24L * 60L * 60L * 1_000L
    }
}
