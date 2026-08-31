package io.github.vrcmteam.vrcm.service

import io.github.vrcmteam.vrcm.network.api.attributes.FavoriteType
import io.github.vrcmteam.vrcm.network.api.favorite.data.FavoriteData
import io.github.vrcmteam.vrcm.network.api.favorite.data.FavoriteGroupData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class FavoriteGroupCacheTest {
    @Test
    fun clearingSessionDataKeepsFlowIdentityForSubsequentLoads() {
        val cache = FavoriteGroupCache()
        val observedFlow = cache.flow(FavoriteType.Avatar)
        val firstSession = favorites("avtr_first")
        val secondSession = favorites("avtr_second")

        cache.replace(FavoriteType.Avatar, firstSession)
        cache.clear()

        assertSame(observedFlow, cache.flow(FavoriteType.Avatar))
        assertEquals(emptyMap(), observedFlow.value)

        cache.replace(FavoriteType.Avatar, secondSession)
        assertEquals(secondSession, observedFlow.value)
    }

    @Test
    fun clearingOneGroupKeepsItsKeyAndEveryOtherGroupUnchanged() {
        val cache = FavoriteGroupCache()
        val target = group("group_avatars1", "avatars1")
        val other = group("group_avatars2", "avatars2")
        val targetFavorites = listOf(favorite("avtr_target", target.name))
        val otherFavorites = listOf(favorite("avtr_other", other.name))
        cache.replace(
            FavoriteType.Avatar,
            linkedMapOf(target to targetFavorites, other to otherFavorites),
        )

        val cleared = cache.clearGroupMembers(
            type = FavoriteType.Avatar,
            ownerId = target.ownerId,
            groupName = target.name,
        )

        assertEquals(target to targetFavorites, cleared)
        assertEquals(
            linkedMapOf(target to emptyList(), other to otherFavorites),
            cache.flow(FavoriteType.Avatar).value,
        )
    }

    private fun favorites(avatarId: String): Map<FavoriteGroupData, List<FavoriteData>> {
        val group = group("group_avatars1", "avatars1")
        return mapOf(
            group to listOf(favorite(avatarId, group.name))
        )
    }

    private fun group(id: String, name: String) = FavoriteGroupData(
        id = id,
        ownerId = "usr_owner",
        type = FavoriteType.Avatar.value,
        visibility = "private",
        displayName = name,
        name = name,
        ownerDisplayName = "Owner",
        tags = emptyList(),
    )

    private fun favorite(avatarId: String, groupName: String) = FavoriteData(
        favoriteId = avatarId,
        id = "fvrt_$avatarId",
        tags = listOf(groupName),
        type = FavoriteType.Avatar.value,
    )
}
