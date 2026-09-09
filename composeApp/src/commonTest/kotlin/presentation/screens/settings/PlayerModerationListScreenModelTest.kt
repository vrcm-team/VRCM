package io.github.vrcmteam.vrcm.presentation.screens.settings

import io.github.vrcmteam.vrcm.core.shared.AccountSessionToken
import io.github.vrcmteam.vrcm.core.shared.AuthenticatedAccount
import io.github.vrcmteam.vrcm.network.api.playermoderation.PlayerModerationData
import io.github.vrcmteam.vrcm.service.data.AccountDto
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerModerationListScreenModelTest {
    @Test
    fun staleAccountResponseCannotReplaceTheCurrentAccountRecords() = runTest {
        val first = accountSession("usr_first", generation = 1)
        val second = accountSession("usr_second", generation = 2)
        val sessions = MutableStateFlow<AuthenticatedAccount?>(first)
        val firstResponse = CompletableDeferred<PlayerModerationListResponse>()
        val secondResponse = CompletableDeferred<PlayerModerationListResponse>()
        val controller = PlayerModerationListController(
            scope = backgroundScope,
            sessions = sessions,
            isCurrentSession = { sessions.value?.token == it },
            load = { token ->
                withContext(NonCancellable) {
                    if (token == first.token) firstResponse.await() else secondResponse.await()
                }
            },
        )
        runCurrent()

        sessions.value = second
        runCurrent()
        secondResponse.complete(
            successfulResponse(second.token, moderation(id = "pmod_second", target = "usr_second_target")),
        )
        runCurrent()

        val current = assertIs<PlayerModerationListState.Ready>(controller.state.value)
        assertEquals("pmod_second", current.records.single().id)

        firstResponse.complete(
            successfulResponse(first.token, moderation(id = "pmod_first", target = "usr_first_target")),
        )
        advanceUntilIdle()

        val afterStaleResponse = assertIs<PlayerModerationListState.Ready>(controller.state.value)
        assertEquals("pmod_second", afterStaleResponse.records.single().id)
    }

    @Test
    fun failedLoadCanRetryAndFilterTheCompleteResult() = runTest {
        val session = accountSession("usr_current", generation = 1)
        val sessions = MutableStateFlow<AuthenticatedAccount?>(session)
        var attempts = 0
        val records = listOf(
            moderation(id = "pmod_mute", type = "mute"),
            moderation(id = "pmod_block", type = "block"),
            moderation(id = "pmod_future", type = "futureType"),
        )
        val controller = PlayerModerationListController(
            scope = backgroundScope,
            sessions = sessions,
            isCurrentSession = { sessions.value?.token == it },
            load = { token ->
                attempts++
                if (attempts == 1) {
                    PlayerModerationListResponse(Result.failure(IllegalStateException("offline")), token)
                } else {
                    PlayerModerationListResponse(Result.success(records), token)
                }
            },
        )
        runCurrent()
        assertIs<PlayerModerationListState.Failed>(controller.state.value)

        controller.retry()
        runCurrent()
        controller.selectType("futureType")

        val ready = assertIs<PlayerModerationListState.Ready>(controller.state.value)
        assertEquals(2, attempts)
        assertEquals(listOf("mute", "block", "futureType"), ready.availableTypes)
        assertEquals(listOf("pmod_future"), ready.visibleRecords.map { it.record.id })
    }

    @Test
    fun invalidTimestampKeepsTheCompleteServerOrder() {
        val first = moderation(id = "pmod_first", created = "2026-01-01T00:00:00Z")
        val invalid = moderation(id = "pmod_invalid", created = "not-a-time")
        val newest = moderation(id = "pmod_newest", created = "2026-08-31T00:00:00Z")
        val records = listOf(first, invalid, newest)

        val ordered = records.stableNewestFirst()

        assertSame(records, ordered)
    }

    @Test
    fun validTimestampsSortNewestFirstAndKeepServerOrderForTies() {
        val oldest = moderation(id = "pmod_oldest", created = "2026-01-01T00:00:00Z")
        val firstTie = moderation(id = "pmod_tie_first", created = "2026-08-31T00:00:00Z")
        val secondTie = moderation(id = "pmod_tie_second", created = "2026-08-31T00:00:00Z")

        val ordered = listOf(oldest, firstTie, secondTie).stableNewestFirst()

        assertEquals(
            listOf("pmod_tie_first", "pmod_tie_second", "pmod_oldest"),
            ordered.map(PlayerModerationData::id),
        )
    }

    @Test
    fun listKeysUseUniqueIdsAndRemainStableAcrossFiltering() {
        val records = listOf(
            moderation(id = "pmod_unique", type = "mute"),
            moderation(id = "pmod_duplicate", type = "mute", target = "usr_a"),
            moderation(id = "pmod_duplicate", type = "block", target = "usr_b"),
            moderation(id = "", type = "block", target = "usr_c"),
        )
        val all = PlayerModerationListState.Ready(records)
        val muted = PlayerModerationListState.Ready(records, selectedType = "mute")

        assertEquals("pmod_unique", all.visibleRecords.first().key)
        assertTrue(all.visibleRecords.drop(1).all { it.key.startsWith("fallback:") })
        assertEquals(
            all.visibleRecords.filter { it.record.type == "mute" }.map { it.key },
            muted.visibleRecords.map { it.key },
        )
    }
}

private fun accountSession(userId: String, generation: Long): AuthenticatedAccount = AuthenticatedAccount(
    account = AccountDto(userId = userId),
    token = AccountSessionToken(userId = userId, generation = generation),
)

private fun successfulResponse(
    token: AccountSessionToken,
    vararg records: PlayerModerationData,
): PlayerModerationListResponse = PlayerModerationListResponse(
    result = Result.success(records.toList()),
    sessionToken = token,
)

private fun moderation(
    id: String,
    type: String = "mute",
    target: String = "usr_target",
    created: String = "2026-08-31T00:00:00Z",
): PlayerModerationData = PlayerModerationData(
    created = created,
    id = id,
    sourceDisplayName = "Current User",
    sourceUserId = "usr_current",
    targetDisplayName = target,
    targetUserId = target,
    type = type,
)
