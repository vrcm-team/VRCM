package io.github.vrcmteam.vrcm.service

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class FriendActivityWorldNameResolverTest {
    @Test
    fun concurrentResolutionFetchesWorldOnlyOnce() = runTest {
        var cachedName: String? = null
        var fetchCount = 0
        val resolver = FriendActivityWorldNameResolver(
            readCachedName = { _, _ -> cachedName },
            fetchWorldName = {
                fetchCount += 1
                "The Black Cat"
            },
            cacheWorldName = { _, _, worldName -> cachedName = worldName },
            nowMillis = { 1_000L },
        )

        listOf(
            async { resolver.resolve("usr_owner", "wrld_world") },
            async { resolver.resolve("usr_owner", "wrld_world") },
        ).awaitAll()

        assertEquals(1, fetchCount)
        assertEquals("The Black Cat", cachedName)
    }

    @Test
    fun failedResolutionWaitsForCooldownBeforeRetrying() = runTest {
        var nowMillis = 1_000L
        var fetchCount = 0
        val resolver = FriendActivityWorldNameResolver(
            readCachedName = { _, _ -> null },
            fetchWorldName = {
                fetchCount += 1
                error("Network unavailable")
            },
            cacheWorldName = { _, _, _ -> error("Must not cache failures") },
            nowMillis = { nowMillis },
        )

        assertFailsWith<IllegalStateException> {
            resolver.resolve("usr_owner", "wrld_world")
        }
        resolver.resolve("usr_owner", "wrld_world")
        assertEquals(1, fetchCount)

        nowMillis += 60_000L
        assertFailsWith<IllegalStateException> {
            resolver.resolve("usr_owner", "wrld_world")
        }
        assertEquals(2, fetchCount)
    }
}
