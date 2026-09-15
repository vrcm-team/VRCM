package io.github.vrcmteam.vrcm.service

import io.github.vrcmteam.vrcm.core.shared.AccountSessionToken
import io.github.vrcmteam.vrcm.network.api.attributes.FavoriteType
import io.github.vrcmteam.vrcm.network.api.favorite.data.FavoriteData
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FavoriteBulkRemovalServiceTest {
    @Test
    fun renewedSessionContinuesBatchAndRetainsPerItemFailures() = runBlocking {
        val firstToken = AccountSessionToken("usr_owner", 1L)
        val renewedToken = AccountSessionToken("usr_owner", 2L)
        val observedTokens = mutableListOf<AccountSessionToken>()
        val callbacks = mutableListOf<String>()
        val service = FavoriteBulkRemovalService(
            call = object : FavoriteRemovalCall {
                override suspend fun remove(
                    sessionToken: AccountSessionToken,
                    selection: FavoriteRemovalSelection,
                ): SessionBoundResponse<Unit> {
                    observedTokens += sessionToken
                    return if (selection.selectionId == "avtr_ok") {
                        SessionBoundResponse(Result.success(Unit), renewedToken)
                    } else {
                        SessionBoundResponse(Result.failure(IllegalStateException("denied")), renewedToken)
                    }
                }
            },
            isCurrentSession = { it == renewedToken },
        )

        val response = assertNotNull(
            service.remove(
                sessionToken = firstToken,
                selections = listOf(selection("avtr_ok"), selection("avtr_failed")),
                onResult = { callbacks += it.selection.selectionId },
            )
        )

        assertEquals(listOf(firstToken, renewedToken), observedTokens)
        assertEquals(listOf("avtr_ok", "avtr_failed"), callbacks)
        assertEquals(renewedToken, response.sessionToken)
        assertTrue(response.results.first().succeeded)
        assertFalse(response.results.last().succeeded)
    }

    private fun selection(avatarId: String) = FavoriteRemovalSelection(
        selectionId = avatarId,
        favoriteType = FavoriteType.Avatar,
        favorite = FavoriteData(
            favoriteId = avatarId,
            id = "fvrt_$avatarId",
            tags = listOf("avatars1"),
            type = FavoriteType.Avatar.value,
        ),
    )
}
