package io.github.vrcmteam.vrcm.presentation.screens.home.pager

import io.github.vrcmteam.vrcm.network.api.worlds.data.FavoritedWorld
import kotlin.test.Test
import kotlin.test.assertEquals

class FavoritedWorldCacheTest {
    @Test
    fun movedFavoriteReplacesTheOldFavoriteRecord() {
        val cache = mutableMapOf<String, FavoritedWorld>()
        replaceFavoritedWorldCache(
            cache,
            listOf(world(id = "wrld_shared", favoriteId = "fvrt_old", group = "group_1")),
        )

        replaceFavoritedWorldCache(
            cache,
            listOf(world(id = "wrld_shared", favoriteId = "fvrt_new", group = "group_2")),
        )

        assertEquals(setOf("wrld_shared"), cache.keys)
        assertEquals("fvrt_new", cache.getValue("wrld_shared").favoriteId)
        assertEquals("group_2", cache.getValue("wrld_shared").favoriteGroup)
    }

    @Test
    fun remoteAndLocalCopiesOfAWorldMergeByWorldId() {
        val remote = world(id = "wrld_shared", favoriteId = "fvrt_remote", group = "worlds1")
        val local = world(
            id = "wrld_shared",
            favoriteId = "local|world|wrld_shared",
            group = "local",
        )

        val merged = mergeFavoritedWorlds(listOf(remote), listOf(local))

        assertEquals(listOf(remote), merged)
    }

    @Test
    fun hiddenPlaceholdersRemainDistinctByFavoriteRecordId() {
        val first = world(id = "???", favoriteId = "fvrt_hidden_1", group = "worlds1")
        val second = world(id = "???", favoriteId = "fvrt_hidden_2", group = "worlds2")

        val merged = mergeFavoritedWorlds(listOf(first, second), emptyList())

        assertEquals(listOf(first, second), merged)
        assertEquals(
            setOf("fvrt_hidden_1", "fvrt_hidden_2"),
            merged.map(::favoritedWorldCacheKey).toSet(),
        )
    }

    private fun world(id: String, favoriteId: String, group: String) = FavoritedWorld(
        id = id,
        name = id,
        favoriteId = favoriteId,
        favoriteGroup = group,
    )
}
