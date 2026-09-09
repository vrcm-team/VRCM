package io.github.vrcmteam.vrcm.service

import io.github.vrcmteam.vrcm.network.api.attributes.FavoriteType
import io.github.vrcmteam.vrcm.network.api.favorite.data.FavoriteData
import io.github.vrcmteam.vrcm.network.api.favorite.data.FavoriteGroupData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
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
    fun updatingGroupMetadataReplacesMapKeyAndPreservesMembership() {
        val cache = FavoriteGroupCache()
        val original = favorites("avtr_member")
        val originalGroup = original.keys.single()
        val originalMembership = original.values.single()
        cache.replace(FavoriteType.Avatar, original)

        val updatedGroup = cache.updateGroup(
            type = FavoriteType.Avatar,
            ownerId = originalGroup.ownerId,
            groupName = originalGroup.name,
        ) { group ->
            group.copy(displayName = "Updated", visibility = "friends")
        }

        val updated = cache.flow(FavoriteType.Avatar).value
        val publishedGroup = assertNotNull(updatedGroup)
        assertEquals("Updated", publishedGroup.displayName)
        assertEquals("friends", publishedGroup.visibility)
        assertFalse(originalGroup in updated)
        assertSame(originalMembership, updated.getValue(publishedGroup))
        assertEquals(listOf(originalGroup.name), updated.getValue(publishedGroup).single().tags)
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
