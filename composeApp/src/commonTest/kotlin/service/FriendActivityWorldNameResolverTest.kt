package io.github.vrcmteam.vrcm.service

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class FriendActivityWorldNameResolverTest {
    @Test
    fun concurrentResolutionForTheSameKeySharesOneRequest() = runTest {
        var fetchCount = 0
        val fetchStarted = CompletableDeferred<Unit>()
        val releaseFetch = CompletableDeferred<Unit>()
        val cacheWrites = mutableListOf<Triple<String, String, String>>()
        val resolver = FriendActivityWorldNameResolver(
            readCachedName = { _, _ -> null },
            fetchWorldName = {
                fetchCount += 1
                fetchStarted.complete(Unit)
                releaseFetch.await()
                "The Black Cat"
            },
            cacheWorldName = { ownerUserId, worldId, worldName ->
                cacheWrites += Triple(ownerUserId, worldId, worldName)
            },
            nowMillis = { 1_000L },
        )

        val resolutions = List(20) {
            async { resolver.resolve("usr_owner", "wrld_world") }
        }
        fetchStarted.await()
        runCurrent()
        assertEquals(1, fetchCount)

        releaseFetch.complete(Unit)
        resolutions.awaitAll()

        assertEquals(1, fetchCount)
        assertEquals(
            listOf(Triple("usr_owner", "wrld_world", "The Black Cat")),
            cacheWrites,
        )
    }

    @Test
    fun differentOwnerWorldKeysFetchConcurrently() = runTest {
        val releaseFetches = CompletableDeferred<Unit>()
        val startedWorlds = mutableListOf<String>()
        val cacheWrites = mutableListOf<Pair<String, String>>()
        val resolver = FriendActivityWorldNameResolver(
            readCachedName = { _, _ -> null },
            fetchWorldName = { worldId ->
                startedWorlds += worldId
                releaseFetches.await()
                "World name"
            },
            cacheWorldName = { ownerUserId, worldId, _ ->
                cacheWrites += ownerUserId to worldId
            },
            nowMillis = { 1_000L },
        )
        val resolutions = listOf(
            async { resolver.resolve("usr_owner_a", "wrld_shared") },
            async { resolver.resolve("usr_owner_b", "wrld_shared") },
        )

        runCurrent()
        try {
            assertEquals(listOf("wrld_shared", "wrld_shared"), startedWorlds)
        } finally {
            releaseFetches.complete(Unit)
        }
        resolutions.awaitAll()

        assertEquals(
            setOf("usr_owner_a" to "wrld_shared", "usr_owner_b" to "wrld_shared"),
            cacheWrites.toSet(),
        )
    }

    @Test
    fun differentWorldFetchesUseSmallBoundedConcurrency() = runTest {
        val releaseFetches = CompletableDeferred<Unit>()
        val startedWorlds = mutableListOf<String>()
        val resolver = FriendActivityWorldNameResolver(
            readCachedName = { _, _ -> null },
            fetchWorldName = { worldId ->
                startedWorlds += worldId
                releaseFetches.await()
                "World $worldId"
            },
            cacheWorldName = { _, _, _ -> },
            nowMillis = { 1_000L },
        )
        val resolutions = (1..5).map { index ->
            async { resolver.resolve("usr_owner", "wrld_$index") }
        }

        runCurrent()
        try {
            assertEquals(4, startedWorlds.size)
        } finally {
            releaseFetches.complete(Unit)
        }
        resolutions.awaitAll()

        assertEquals(5, startedWorlds.size)
    }

    @Test
    fun failedResolutionWaitsForCooldownBeforeRetrying() = runTest {
        var nowMillis = 1_000L
        var fetchCount = 0
        var cachedName: String? = null
        val resolver = FriendActivityWorldNameResolver(
            readCachedName = { _, _ -> cachedName },
            fetchWorldName = {
                fetchCount += 1
                if (fetchCount == 1) error("Network unavailable")
                "Retry succeeded"
            },
            cacheWorldName = { _, _, worldName -> cachedName = worldName },
            nowMillis = { nowMillis },
        )

        assertFailsWith<IllegalStateException> {
            resolver.resolve("usr_owner", "wrld_world")
        }
        resolver.resolve("usr_owner", "wrld_world")
        assertEquals(1, fetchCount)

        nowMillis += 60_000L
        resolver.resolve("usr_owner", "wrld_world")
        assertEquals(2, fetchCount)
        assertEquals("Retry succeeded", cachedName)
    }

    @Test
    fun cancellationPropagatesWithoutStartingFailureCooldown() = runTest {
        var fetchCount = 0
        val firstFetchStarted = CompletableDeferred<Unit>()
        val blockedFetch = CompletableDeferred<String>()
        val resolver = FriendActivityWorldNameResolver(
            readCachedName = { _, _ -> null },
            fetchWorldName = {
                fetchCount += 1
                if (fetchCount == 1) {
                    firstFetchStarted.complete(Unit)
                    blockedFetch.await()
                } else {
                    "Retry after cancellation"
                }
            },
            cacheWorldName = { _, _, _ -> },
            nowMillis = { 1_000L },
        )

        val cancelledResolution = async {
            resolver.resolve("usr_owner", "wrld_world")
        }
        firstFetchStarted.await()
        cancelledResolution.cancelAndJoin()
        assertTrue(cancelledResolution.isCancelled)

        resolver.resolve("usr_owner", "wrld_world")

        assertEquals(2, fetchCount)
    }
}
