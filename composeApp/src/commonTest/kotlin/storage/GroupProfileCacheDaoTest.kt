package io.github.vrcmteam.vrcm.storage

import com.russhwolf.settings.MapSettings
import io.github.vrcmteam.vrcm.network.api.groups.data.GroupData
import io.github.vrcmteam.vrcm.storage.data.GroupProfileCache
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GroupProfileCacheDaoTest {
    @Test
    fun groupDetailsArePersisted() {
        val dao = GroupProfileCacheDao(MapSettings())
        val cache = GroupProfileCache(group(), cachedAtEpochMilliseconds = 123L)

        dao.save(cache)

        assertEquals(cache, dao.load("grp_example"))
    }

    @Test
    fun cacheExpiresAfterTwoHours() {
        val cache = GroupProfileCache(group(), cachedAtEpochMilliseconds = 1_000L)

        assertFalse(cache.isExpired(1_000L + GroupProfileCache.MAX_AGE_MILLISECONDS - 1L))
        assertTrue(cache.isExpired(1_000L + GroupProfileCache.MAX_AGE_MILLISECONDS))
    }

    @Test
    fun clearAllRemovesStoredGroups() {
        val dao = GroupProfileCacheDao(MapSettings())
        dao.save(GroupProfileCache(group(), cachedAtEpochMilliseconds = 123L))

        dao.clearAll()

        assertNull(dao.load("grp_example"))
    }

    private fun group() = GroupData(
        id = "grp_example",
        name = "Group",
        description = "Cached description",
        memberCount = 123,
        onlineMemberCount = 45,
        updatedAt = "2026-08-01T00:00:00Z",
    )
}
