package io.github.vrcmteam.vrcm.service

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex

internal class FriendActivityWorldNameResolver(
    private val readCachedName: suspend (ownerUserId: String, worldId: String) -> String?,
    private val fetchWorldName: suspend (worldId: String) -> String,
    private val cacheWorldName: suspend (ownerUserId: String, worldId: String, worldName: String) -> Unit,
    private val nowMillis: () -> Long,
) {
    private data class WorldKey(
        val ownerUserId: String,
        val worldId: String,
    )

    private val mutex = Mutex()
    private val retryAtMillisByWorld = mutableMapOf<WorldKey, Long>()

    suspend fun resolve(ownerUserId: String, worldId: String) {
        val key = WorldKey(ownerUserId, worldId)
        mutex.lock()
        try {
            val now = nowMillis()
            if (now < (retryAtMillisByWorld[key] ?: Long.MIN_VALUE)) return

            try {
                val worldName = readCachedName(ownerUserId, worldId)
                    ?: fetchWorldName(worldId).also { require(it.isNotBlank()) }
                cacheWorldName(ownerUserId, worldId, worldName)
                retryAtMillisByWorld.remove(key)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                retryAtMillisByWorld[key] = now + FAILURE_COOLDOWN_MILLIS
                throw error
            }
        } finally {
            mutex.unlock()
        }
    }

    private companion object {
        const val FAILURE_COOLDOWN_MILLIS = 60_000L
    }
}
