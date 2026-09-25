package io.github.vrcmteam.vrcm.service

import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

internal class FriendActivityWorldNameResolver(
    private val readCachedName: suspend (ownerUserId: String, worldId: String) -> String?,
    private val fetchWorldName: suspend (worldId: String) -> String,
    private val cacheWorldName: suspend (ownerUserId: String, worldId: String, worldName: String) -> Unit,
    private val nowMillis: () -> Long,
    maxConcurrentFetches: Int = DEFAULT_MAX_CONCURRENT_FETCHES,
) {
    private data class WorldKey(
        val ownerUserId: String,
        val worldId: String,
    )

    private data class ResolutionFlight(
        val startedAtMillis: Long,
        val completion: CompletableDeferred<Unit> = CompletableDeferred(),
    )

    private data class ResolutionClaim(
        val flight: ResolutionFlight,
        val ownsFlight: Boolean,
    )

    private val lock = SynchronizedObject()
    private val fetchSemaphore = Semaphore(maxConcurrentFetches)
    private val inFlightByWorld = mutableMapOf<WorldKey, ResolutionFlight>()
    private val retryAtMillisByWorld = mutableMapOf<WorldKey, Long>()

    init {
        require(maxConcurrentFetches > 0) { "maxConcurrentFetches must be positive" }
    }

    suspend fun resolve(ownerUserId: String, worldId: String) {
        val key = WorldKey(ownerUserId, worldId)
        val claim = claimResolution(key) ?: return
        if (!claim.ownsFlight) {
            claim.flight.completion.await()
            return
        }
        resolveClaimed(key, claim.flight)
    }

    fun request(
        scope: CoroutineScope,
        ownerUserId: String,
        worldId: String,
        onFailure: (Throwable) -> Unit,
    ) {
        val key = WorldKey(ownerUserId, worldId)
        val claim = claimResolution(key) ?: return
        if (!claim.ownsFlight) return

        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                resolveClaimed(key, claim.flight)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                onFailure(error)
            }
        }
        job.invokeOnCompletion { error ->
            if (error != null) completeResolution(key, claim.flight, error)
        }
        job.start()
    }

    private fun claimResolution(key: WorldKey): ResolutionClaim? = synchronized(lock) {
        inFlightByWorld[key]?.let { current ->
            return@synchronized ResolutionClaim(current, ownsFlight = false)
        }
        val now = nowMillis()
        if (now < (retryAtMillisByWorld[key] ?: Long.MIN_VALUE)) return@synchronized null

        val flight = ResolutionFlight(startedAtMillis = now)
        inFlightByWorld[key] = flight
        ResolutionClaim(flight, ownsFlight = true)
    }

    private suspend fun resolveClaimed(key: WorldKey, flight: ResolutionFlight) {
        var failure: Throwable? = null
        try {
            val worldName = readCachedName(key.ownerUserId, key.worldId)
                ?: fetchSemaphore.withPermit {
                    fetchWorldName(key.worldId).also { require(it.isNotBlank()) }
                }
            cacheWorldName(key.ownerUserId, key.worldId, worldName)
        } catch (error: CancellationException) {
            failure = error
            throw error
        } catch (error: Throwable) {
            failure = error
            throw error
        } finally {
            completeResolution(key, flight, failure)
        }
    }

    private fun completeResolution(
        key: WorldKey,
        flight: ResolutionFlight,
        failure: Throwable?,
    ) {
        val shouldComplete = synchronized(lock) {
            if (inFlightByWorld[key] !== flight) return@synchronized false

            when (failure) {
                null -> retryAtMillisByWorld.remove(key)
                is CancellationException -> Unit
                else -> retryAtMillisByWorld[key] =
                    flight.startedAtMillis + FAILURE_COOLDOWN_MILLIS
            }
            true
        }
        if (!shouldComplete) return

        if (failure == null) {
            flight.completion.complete(Unit)
        } else {
            flight.completion.completeExceptionally(failure)
        }
        synchronized(lock) {
            if (inFlightByWorld[key] === flight) inFlightByWorld.remove(key)
        }
    }

    private companion object {
        const val FAILURE_COOLDOWN_MILLIS = 60_000L
        const val DEFAULT_MAX_CONCURRENT_FETCHES = 4
    }
}
