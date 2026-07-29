package io.github.vrcmteam.vrcm.presentation.screens.home.pager

import io.github.vrcmteam.vrcm.network.api.worlds.data.FavoritedWorld
import kotlin.test.Test
import kotlin.test.assertEquals

class FavoritedWorldSearchMappingTest {
    @Test
    fun thumbnailOnlyFavoriteKeepsAUsableWorldImage() {
        val world = FavoritedWorld(
            id = "wrld_thumbnail_only",
            name = "Thumbnail only",
            imageUrl = null,
            thumbnailImageUrl =
                "https://api.vrchat.cloud/api/1/image/file_thumbnail-only/7/256",
            favoriteId = "fvrt_thumbnail_only",
            favoriteGroup = "worlds1",
        )

        val mapped = world.toSearchWorldData()

        assertEquals(
            "https://api.vrchat.cloud/api/1/file/file_thumbnail-only/7/file",
            mapped.imageUrl,
        )
    }
}
