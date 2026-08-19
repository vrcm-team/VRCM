package io.github.vrcmteam.vrcm.storage

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import io.github.vrcmteam.vrcm.network.api.avatars.data.AvatarData
import io.github.vrcmteam.vrcm.network.api.worlds.data.FavoritedWorld
import io.github.vrcmteam.vrcm.storage.data.FavoritedWorldGroup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class RoomFavoriteListCacheStoreTest {
    @Test
    fun worldAndAvatarRefreshesDoNotOverwriteEachOther() = withStore { store ->
        val worlds = listOf(
            FavoritedWorldGroup(
                name = "Worlds",
                groupKey = "worlds1",
                worlds = listOf(
                    FavoritedWorld(
                        id = "wrld_cached",
                        name = "Cached world",
                        favoriteId = "fvrt_cached",
                        favoriteGroup = "worlds1",
                    ),
                ),
            ),
        )
        val avatars = listOf(AvatarData(id = "avtr_cached", name = "Cached avatar"))

        store.saveWorlds("usr_owner", worlds)
        store.saveAvatars("usr_owner", avatars)

        val cached = assertNotNull(store.load("usr_owner"))
        assertEquals(worlds, cached.favoritedWorlds)
        assertEquals(avatars, cached.favoritedAvatars)

        store.saveWorlds("usr_owner", emptyList())

        val afterEmptyRefresh = assertNotNull(store.load("usr_owner"))
        assertEquals(emptyList(), afterEmptyRefresh.favoritedWorlds)
        assertEquals(avatars, afterEmptyRefresh.favoritedAvatars)
    }

    @Test
    fun clearingOneAccountKeepsAnotherAccountsFavorites() = withStore { store ->
        store.saveAvatars("usr_a", listOf(AvatarData(id = "avtr_a", name = "A")))
        store.saveAvatars("usr_b", listOf(AvatarData(id = "avtr_b", name = "B")))

        store.clear("usr_a")

        assertNull(store.load("usr_a"))
        assertEquals("avtr_b", assertNotNull(store.load("usr_b")).favoritedAvatars.single().id)
    }

    private fun withStore(block: suspend (FavoriteListCacheStore) -> Unit) = runTest {
        val database = Room.inMemoryDatabaseBuilder<VrcmDatabase>()
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()
        try {
            var clock = 0L
            block(
                RoomFavoriteListCacheStore(
                    dao = database.cachedBlobDao(),
                    nowMillis = { ++clock },
                ),
            )
        } finally {
            database.close()
        }
    }
}
