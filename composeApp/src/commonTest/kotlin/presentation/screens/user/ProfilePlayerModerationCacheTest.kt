package io.github.vrcmteam.vrcm.presentation.screens.user

import io.github.vrcmteam.vrcm.core.shared.AccountSessionToken
import io.github.vrcmteam.vrcm.network.api.playermoderation.PlayerModerationData
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ProfilePlayerModerationCacheTest {
    @Test
    fun profileControlsShareAReadUntilItsSessionOrRemoteStateChanges() = runTest {
        var reads = 0
        val moderation = PlayerModerationData(
            targetUserId = TARGET_USER_ID,
            type = "block",
        )
        val cache = ProfilePlayerModerationCache {
            reads++
            listOf(moderation)
        }
        val firstSession = AccountSessionToken("usr_owner", generation = 1)

        repeat(4) {
            assertEquals(listOf(moderation), cache.get(firstSession, TARGET_USER_ID))
        }
        assertEquals(1, reads)

        cache.invalidate(firstSession, TARGET_USER_ID)
        cache.get(firstSession, TARGET_USER_ID)
        assertEquals(2, reads)

        cache.get(AccountSessionToken("usr_owner", generation = 2), TARGET_USER_ID)
        assertEquals(3, reads)
    }

    private companion object {
        const val TARGET_USER_ID = "usr_target"
    }
}
