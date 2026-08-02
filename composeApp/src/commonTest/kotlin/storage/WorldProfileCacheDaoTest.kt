package io.github.vrcmteam.vrcm.storage

import com.russhwolf.settings.MapSettings
import io.github.vrcmteam.vrcm.network.api.worlds.data.WorldData
import io.github.vrcmteam.vrcm.storage.data.WorldProfileCache
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WorldProfileCacheDaoTest {
    @Test
    fun worldDetailsArePersisted() {
        val dao = WorldProfileCacheDao(MapSettings())
        val cache = WorldProfileCache(world(), cachedAtEpochMilliseconds = 123L)

        dao.save(cache)

        assertEquals(cache, dao.load("wrld_example"))
    }

    @Test
    fun cacheExpiresAfterTwentyFourHours() {
        val cache = WorldProfileCache(world(), cachedAtEpochMilliseconds = 1_000L)

        assertFalse(cache.isExpired(1_000L + WorldProfileCache.MAX_AGE_MILLISECONDS - 1L))
        assertTrue(cache.isExpired(1_000L + WorldProfileCache.MAX_AGE_MILLISECONDS))
    }

    @Test
    fun clearAllRemovesStoredWorlds() {
        val dao = WorldProfileCacheDao(MapSettings())
        dao.save(WorldProfileCache(world(), cachedAtEpochMilliseconds = 123L))

        dao.clearAll()

        assertNull(dao.load("wrld_example"))
    }

    private fun world() = WorldData(
        authorId = "usr_author",
        authorName = "Author",
        capacity = 32,
        createdAt = "2026-01-01T00:00:00Z",
        description = "Cached description",
        favorites = 456,
        featured = false,
        heat = 3,
        id = "wrld_example",
        imageUrl = "https://example.com/world.png",
        labsPublicationDate = "2026-01-01T00:00:00Z",
        name = "World",
        namespace = null,
        organization = "vrchat",
        popularity = 4,
        publicationDate = "2026-01-02T00:00:00Z",
        recommendedCapacity = 16,
        releaseStatus = "public",
        tags = emptyList(),
        thumbnailImageUrl = null,
        udonProducts = emptyList(),
        unityPackages = emptyList(),
        updatedAt = "2026-07-31T00:00:00Z",
        version = 7,
        visits = 12_345,
    )
}
