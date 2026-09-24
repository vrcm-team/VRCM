package io.github.vrcmteam.vrcm.storage

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import io.github.vrcmteam.vrcm.network.api.avatars.data.AvatarData
import io.github.vrcmteam.vrcm.network.api.files.data.PlatformType
import io.github.vrcmteam.vrcm.network.api.worlds.data.FavoritedWorld
import io.github.vrcmteam.vrcm.network.api.worlds.data.UnityPackage
import io.github.vrcmteam.vrcm.storage.data.FavoritedWorldGroup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

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
        assertEquals(true, cached.worldsLoaded)
        assertEquals(true, cached.avatarsLoaded)

        store.saveWorlds("usr_owner", emptyList())

        val afterEmptyRefresh = assertNotNull(store.load("usr_owner"))
        assertEquals(emptyList(), afterEmptyRefresh.favoritedWorlds)
        assertEquals(avatars, afterEmptyRefresh.favoritedAvatars)
        assertEquals(true, afterEmptyRefresh.worldsLoaded)
        assertEquals(true, afterEmptyRefresh.avatarsLoaded)
    }

    @Test
    fun refreshingOneTypeDoesNotMarkTheOtherTypeComplete() = withStore { store ->
        store.saveWorlds("usr_owner", emptyList())

        val worldsOnly = assertNotNull(store.load("usr_owner"))
        assertEquals(true, worldsOnly.worldsLoaded)
        assertEquals(false, worldsOnly.avatarsLoaded)

        store.saveAvatars("usr_owner", emptyList())

        val bothTypes = assertNotNull(store.load("usr_owner"))
        assertEquals(true, bothTypes.worldsLoaded)
        assertEquals(true, bothTypes.avatarsLoaded)
    }

    @Test
    fun cachedFavoriteWorldsKeepPlatformsForTheListIconsButDropCdnLinks() = withStore { store ->
        fun pkg(platform: PlatformType, version: String) = UnityPackage(
            assetUrl = "https://api.vrchat.cloud/api/1/file/file_$version/1/file",
            createdAt = "2026-01-0${version.last()}T00:00:00Z",
            platform = platform,
            pluginUrl = "https://api.vrchat.cloud/api/1/file/file_plugin_$version/1/file",
            unityVersion = "2022.3.22f1",
        )
        val world = FavoritedWorld(
            id = "wrld_cached",
            name = "Cached world",
            favoriteId = "fvrt_cached",
            favoriteGroup = "worlds1",
            unityPackages = listOf(
                pkg(PlatformType.Windows, "w1"),
                pkg(PlatformType.Windows, "w2"),
                pkg(PlatformType.Android, "a1"),
            ),
        )

        store.saveWorlds("usr_owner", listOf(FavoritedWorldGroup(name = "Worlds", groupKey = "worlds1", worlds = listOf(world))))

        // 收藏列表从缓存恢复时就要画出"支持平台"图标，不能等网络刷新
        val cachedPackages = assertNotNull(store.load("usr_owner")).favoritedWorlds.single().worlds.single().unityPackages
        assertEquals(setOf(PlatformType.Windows, PlatformType.Android), cachedPackages.map { it.platform }.toSet())
        assertEquals(2, cachedPackages.size)
        assertTrue(cachedPackages.all { it.assetUrl == null && it.pluginUrl == null })
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
