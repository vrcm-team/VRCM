package io.github.vrcmteam.vrcm.presentation.screens.settings

import androidx.lifecycle.ViewModelStore
import io.github.vrcmteam.vrcm.core.shared.AccountSessionToken
import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.network.api.playermoderation.data.PlayerModerationData
import io.github.vrcmteam.vrcm.network.api.playermoderation.data.PlayerModerationType
import io.github.vrcmteam.vrcm.service.PlayerModerationCleanupResponse
import io.github.vrcmteam.vrcm.service.PlayerModerationCleanupSource
import io.github.vrcmteam.vrcm.service.data.AccountDto
import io.github.vrcmteam.vrcm.testing.MainDispatcherTest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class PlayerModerationListScreenModelTest : MainDispatcherTest() {
    @Test
    fun staleAccountResponseCannotReplaceTheCurrentAccountRecords() = runBlocking {
        val firstAccount = AccountDto(userId = "usr_first", username = "first")
        val secondAccount = AccountDto(userId = "usr_second", username = "second")
        val firstRequestStarted = CompletableDeferred<Unit>()
        val firstResponse =
            CompletableDeferred<PlayerModerationCleanupResponse<List<PlayerModerationData>>>()
        val secondResponse =
            CompletableDeferred<PlayerModerationCleanupResponse<List<PlayerModerationData>>>()
        SharedFlowCentre.emitAuthenticated(firstAccount)
        val firstToken = requireNotNull(SharedFlowCentre.currentSession.value?.token)
        val source = ListTestPlayerModerationSource { token ->
            if (token == firstToken) {
                firstRequestStarted.complete(Unit)
                withContext(NonCancellable) { firstResponse.await() }
            } else {
                secondResponse.await()
            }
        }
        val model = PlayerModerationListScreenModel(source)
        try {
            model.loadIfNeeded()
            firstRequestStarted.await()

            SharedFlowCentre.emitAuthenticated(secondAccount)
            val secondToken = requireNotNull(SharedFlowCentre.currentSession.value?.token)
            secondResponse.complete(
                successfulResponse(
                    secondToken,
                    moderation(id = "pmod_second", target = "usr_second_target"),
                ),
            )
            awaitUntil {
                model.state.value.records.singleOrNull()?.id == "pmod_second"
            }

            firstResponse.complete(
                successfulResponse(
                    firstToken,
                    moderation(id = "pmod_first", target = "usr_first_target"),
                ),
            )
            yield()

            assertEquals("pmod_second", model.state.value.records.single().id)
        } finally {
            firstResponse.complete(successfulResponse(firstToken))
            secondResponse.complete(successfulResponse(firstToken))
            close(model)
            SharedFlowCentre.emitLogout()
        }
    }

    @Test
    fun failedLoadCanRetryAndFilterTheCompleteResult() = runBlocking {
        val records = listOf(
            moderation(id = "pmod_mute", type = "mute"),
            moderation(id = "pmod_block", type = "block"),
            moderation(id = "pmod_future", type = "futureType"),
        )
        var attempts = 0
        SharedFlowCentre.emitAuthenticated(AccountDto(userId = "usr_current", username = "current"))
        val source = ListTestPlayerModerationSource { token ->
            attempts++
            if (attempts == 1) {
                PlayerModerationCleanupResponse(
                    Result.failure(IllegalStateException("offline")),
                    token,
                )
            } else {
                PlayerModerationCleanupResponse(Result.success(records), token)
            }
        }
        val model = PlayerModerationListScreenModel(source)
        try {
            model.loadIfNeeded()
            awaitUntil { model.state.value.loadFailed }

            model.refresh()
            awaitUntil { model.state.value.records.size == records.size }
            model.selectFilter("mute")

            assertEquals(PlayerModerationType.Mute, model.state.value.selectedCleanupType)

            model.selectFilter("futureType")

            val state = model.state.value
            assertEquals(2, attempts)
            assertEquals(listOf("mute", "block", "futureType"), state.availableFilterTypes)
            assertEquals(listOf("pmod_future"), state.visibleRecords.map { it.record.id })
            assertFalse(state.loadFailed)
        } finally {
            close(model)
            SharedFlowCentre.emitLogout()
        }
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
        val all = PlayerModerationState(records = records)
        val muted = PlayerModerationState(records = records, selectedFilter = "mute")

        assertEquals("pmod_unique", all.visibleRecords.first().key)
        assertTrue(all.visibleRecords.drop(1).all { it.key.startsWith("fallback:") })
        assertEquals(
            all.visibleRecords.filter { it.record.type == "mute" }.map { it.key },
            muted.visibleRecords.map { it.key },
        )
    }
}

private class ListTestPlayerModerationSource(
    private val load: suspend (
        AccountSessionToken,
    ) -> PlayerModerationCleanupResponse<List<PlayerModerationData>>?,
) : PlayerModerationCleanupSource {
    override suspend fun getAll(
        sessionToken: AccountSessionToken,
    ): PlayerModerationCleanupResponse<List<PlayerModerationData>>? = load(sessionToken)

    override suspend fun get(
        sessionToken: AccountSessionToken,
        type: PlayerModerationType,
    ): PlayerModerationCleanupResponse<List<PlayerModerationData>> =
        PlayerModerationCleanupResponse(Result.success(emptyList()), sessionToken)

    override suspend fun remove(
        sessionToken: AccountSessionToken,
        targetUserId: String,
        type: PlayerModerationType,
    ): PlayerModerationCleanupResponse<Unit> =
        PlayerModerationCleanupResponse(Result.success(Unit), sessionToken)
}

private fun successfulResponse(
    token: AccountSessionToken,
    vararg records: PlayerModerationData,
): PlayerModerationCleanupResponse<List<PlayerModerationData>> =
    PlayerModerationCleanupResponse(Result.success(records.toList()), token)

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

private fun close(model: PlayerModerationListScreenModel) {
    ViewModelStore().apply {
        put("test", model)
        clear()
    }
}

private suspend fun awaitUntil(predicate: () -> Boolean) {
    withTimeout(3_000) {
        while (!predicate()) yield()
    }
}
