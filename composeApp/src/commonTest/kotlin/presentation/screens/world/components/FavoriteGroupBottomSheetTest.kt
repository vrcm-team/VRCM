package io.github.vrcmteam.vrcm.presentation.screens.world.components

import io.github.vrcmteam.vrcm.network.api.favorite.data.FavoriteData
import io.github.vrcmteam.vrcm.network.api.favorite.data.FavoriteGroupData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FavoriteGroupBottomSheetTest {
    @Test
    fun hiddenWorldCanResolveItsFavoriteByRecordId() {
        val firstGroup = group("worlds1")
        val secondGroup = group("worlds2")
        val firstFavorite = favorite("fvrt_hidden_1", firstGroup.name)
        val secondFavorite = favorite("fvrt_hidden_2", secondGroup.name)

        val result = findFavoriteForManagement(
            favoritesByGroup = mapOf(
                firstGroup to listOf(firstFavorite),
                secondGroup to listOf(secondFavorite),
            ),
            favoriteId = "???",
            favoriteRecordId = secondFavorite.id,
        )

        assertEquals(secondGroup, result?.first)
        assertEquals(secondFavorite, result?.second)
    }

    @Test
    fun missingExplicitRecordIdDoesNotFallBackToHiddenWorldPlaceholder() {
        val group = group("worlds1")
        val favorite = favorite("fvrt_hidden_1", group.name)

        val result = findFavoriteForManagement(
            favoritesByGroup = mapOf(group to listOf(favorite)),
            favoriteId = "???",
            favoriteRecordId = "fvrt_missing",
        )

        assertNull(result)
    }

    private fun group(name: String) = FavoriteGroupData(
        id = "group_$name",
        ownerId = "usr_owner",
        type = "world",
        visibility = "private",
        displayName = name,
        name = name,
        ownerDisplayName = "Owner",
        tags = emptyList(),
    )

    private fun favorite(id: String, groupName: String) = FavoriteData(
        favoriteId = "???",
        id = id,
        tags = listOf(groupName),
        type = "world",
    )
}
