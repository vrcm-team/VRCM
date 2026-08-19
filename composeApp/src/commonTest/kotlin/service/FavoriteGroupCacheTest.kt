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

    private fun favorites(avatarId: String): Map<FavoriteGroupData, List<FavoriteData>> {
        val group = FavoriteGroupData(
            id = "group_avatars1",
            ownerId = "usr_owner",
            type = FavoriteType.Avatar.value,
            visibility = "private",
            displayName = "Avatars 1",
            name = "avatars1",
            ownerDisplayName = "Owner",
            tags = emptyList(),
        )
        return mapOf(
            group to listOf(
                FavoriteData(
                    favoriteId = avatarId,
                    id = "fvrt_$avatarId",
                    tags = listOf(group.name),
                    type = FavoriteType.Avatar.value,
                )
            )
        )
    }
}
