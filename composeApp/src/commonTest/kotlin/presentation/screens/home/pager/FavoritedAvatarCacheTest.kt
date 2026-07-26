package io.github.vrcmteam.vrcm.presentation.screens.home.pager

import io.github.vrcmteam.vrcm.network.api.avatars.data.AvatarData
import io.github.vrcmteam.vrcm.network.api.favorite.data.FavoriteData
import io.github.vrcmteam.vrcm.network.api.favorite.data.FavoriteGroupData
import kotlin.test.Test
import kotlin.test.assertEquals

class FavoritedAvatarCacheTest {
    @Test
    fun remoteAndLocalAvatarsMergeByIdWithoutDuplicates() {
        val remote = listOf(avatar("remote"), avatar("shared", "remote version"))
        val local = listOf(avatar("local"), avatar("shared", "local version"))

        val merged = mergeFavoritedAvatars(remote, local)

        assertEquals(listOf("remote", "shared", "local"), merged.map { it.id })
        assertEquals("remote version", merged.first { it.id == "shared" }.name)
    }

    @Test
    fun localAvatarIdsOnlyComeFromTheLocalAvatarGroup() {
        val groups = mapOf(
            group(id = "local-avatar", ownerId = "local", type = "avatar") to listOf(favorite("avtr_local")),
            group(id = "remote-avatar", ownerId = "usr_owner", type = "avatar") to listOf(favorite("avtr_remote")),
            group(id = "local-world", ownerId = "local", type = "world") to listOf(favorite("wrld_local")),
        )

        assertEquals(listOf("avtr_local"), localFavoritedAvatarIds(groups))
    }

    private fun avatar(id: String, name: String = id) = AvatarData(id = id, name = name)

    private fun group(id: String, ownerId: String, type: String) = FavoriteGroupData(
        id = id,
        ownerId = ownerId,
        type = type,
        visibility = "private",
        displayName = id,
        name = id,
        ownerDisplayName = ownerId,
        tags = emptyList(),
    )

    private fun favorite(id: String) = FavoriteData(
        favoriteId = id,
        id = "favorite-$id",
        tags = emptyList(),
        type = "avatar",
    )
}
