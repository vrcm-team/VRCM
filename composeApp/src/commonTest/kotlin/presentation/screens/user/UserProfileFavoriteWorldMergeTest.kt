package io.github.vrcmteam.vrcm.presentation.screens.user

import io.github.vrcmteam.vrcm.network.api.worlds.data.FavoritedWorld
import io.github.vrcmteam.vrcm.storage.data.FavoritedWorldGroup
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UserProfileFavoriteWorldMergeTest {
    @Test
    fun oldCachedGroupWithoutStableKeyFallsBackToItsName() {
        val cached = Json.decodeFromString<FavoritedWorldGroup>(
            """{"name":"Group one","worlds":[]}"""
        )

        assertEquals("Group one", cached.groupKey)
    }

    @Test
    fun successfulGroupUpdatesWhileFailedGroupKeepsCachedWorlds() {
        val cached = listOf(
            group("worlds1", "Group one", world("wrld_old_1")),
            group("worlds2", "Group two", world("wrld_old_2")),
        )

        val merged = mergeFavoritedWorldGroups(
            cachedGroups = cached,
            loads = listOf(
                load("worlds1", "Group one", Result.success(listOf(world("wrld_new_1")))),
                load("worlds2", "Group two", Result.failure(IllegalStateException("timeout"))),
            ),
        )

        assertEquals(listOf("wrld_new_1"), merged[0].worlds.map { it.id })
        assertEquals(listOf("wrld_old_2"), merged[1].worlds.map { it.id })
    }

    @Test
    fun successfulEmptyGroupClearsItsCachedWorlds() {
        val merged = mergeFavoritedWorldGroups(
            cachedGroups = listOf(group("worlds1", "Group one", world("wrld_old"))),
            loads = listOf(load("worlds1", "Group one", Result.success(emptyList()))),
        )

        assertEquals(1, merged.size)
        assertEquals("worlds1", merged.single().groupKey)
        assertTrue(merged.single().worlds.isEmpty())
    }

    @Test
    fun selfProfileRefreshKeepsTheLocalWorldGroupFromTheSharedCache() {
        val local = group(
            "__local_world__",
            "Local",
            world("wrld_local", favoriteId = "local|world|wrld_local"),
        )

        val merged = mergeFavoritedWorldGroups(
            cachedGroups = listOf(
                group("worlds1", "Group one", world("wrld_old")),
                local,
            ),
            loads = listOf(
                load("worlds1", "Group one", Result.success(listOf(world("wrld_new")))),
            ),
        )

        assertEquals(listOf("worlds1", "__local_world__"), merged.map { it.groupKey })
        assertEquals("wrld_local", merged.last().worlds.single().id)
    }

    @Test
    fun failedSelfMigrationKeepsLegacyFavoriteWorldsForTheNextSave() {
        val legacy = listOf(group("worlds1", "Group one", world("wrld_old")))

        assertEquals(
            legacy,
            profileFavoritedWorldsForCache(
                userId = "usr_owner",
                cacheOwnerUserId = "usr_owner",
                migrationSucceeded = false,
                favoritedWorldGroups = legacy,
            ),
        )
        assertTrue(
            profileFavoritedWorldsForCache(
                userId = "usr_owner",
                cacheOwnerUserId = "usr_owner",
                migrationSucceeded = true,
                favoritedWorldGroups = legacy,
            ).isEmpty(),
        )
    }

    private fun load(
        groupKey: String,
        displayName: String,
        result: Result<List<FavoritedWorld>>,
    ) = FavoritedWorldGroupLoad(groupKey, displayName, result)

    private fun group(groupKey: String, name: String, vararg worlds: FavoritedWorld) =
        FavoritedWorldGroup(name = name, worlds = worlds.toList(), groupKey = groupKey)

    private fun world(id: String, favoriteId: String = "fvrt_$id") = FavoritedWorld(
        id = id,
        name = id,
        favoriteId = favoriteId,
        favoriteGroup = "worlds1",
    )
}
