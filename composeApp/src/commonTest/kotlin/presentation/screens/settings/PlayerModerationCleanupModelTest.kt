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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerModerationCleanupModelTest : MainDispatcherTest() {
    @Test
    fun loginWhileModelIsAliveAutomaticallyLoadsCurrentRecords() = runBlocking {
        SharedFlowCentre.emitLogout()
        val source = FakePlayerModerationCleanupSource().apply {
            allRecords = listOf(record("current", "usr_a", "mute"))
        }
        val model = PlayerModerationCleanupModel(source)
        try {
            model.loadIfNeeded()
            assertFalse(model.state.value.isSessionAvailable)

            SharedFlowCentre.emitAuthenticated(AccountDto(userId = "usr_account", username = "account"))
            awaitUntil { model.state.value.hasLoaded }

            assertEquals(PlayerModerationType.Mute, model.state.value.selectedType)
        } finally {
            close(model)
            SharedFlowCentre.emitLogout()
        }
    }

    @Test
    fun loadKeepsOnlySupportedTypesAndCountsDistinctTargets() = runBlocking {
        val account = AccountDto(userId = "usr_account", username = "account")
        val source = FakePlayerModerationCleanupSource().apply {
            allRecords = listOf(
                record("one", "usr_a", "block"),
                record("duplicate", "usr_a", "block"),
                record("two", "usr_b", "mute"),
                record("deprecated", "usr_c", "hideAvatar"),
                record("future", "usr_d", "futureType"),
            )
        }
        SharedFlowCentre.emitAuthenticated(account)
        val model = PlayerModerationCleanupModel(source)
        try {
            model.loadIfNeeded()
            awaitUntil { model.state.value.hasLoaded }

            assertEquals(
                listOf(
                    PlayerModerationTypeCount(PlayerModerationType.Block, 1),
                    PlayerModerationTypeCount(PlayerModerationType.Mute, 1),
                ),
                model.state.value.availableTypes,
            )
            assertEquals(PlayerModerationType.Block, model.state.value.selectedType)
            assertFalse(model.state.value.loadFailed)
        } finally {
            close(model)
            SharedFlowCentre.emitLogout()
        }
    }

    @Test
    fun duplicateSubmitIsIgnoredAndVerificationReportsResidualTargets() = runBlocking {
        val account = AccountDto(userId = "usr_account", username = "account")
        val firstRemoveStarted = CompletableDeferred<Unit>()
        val releaseFirstRemove = CompletableDeferred<Unit>()
        val source = FakePlayerModerationCleanupSource().apply {
            allRecords = listOf(
                record("one", "usr_a", "block"),
                record("two", "usr_b", "block"),
                record("three", "usr_c", "mute"),
            )
            typedRecords = listOf(
                record("one", "usr_a", "block"),
                record("duplicate", "usr_a", "block"),
                record("two", "usr_b", "block"),
                record("wrong-type", "usr_c", "mute"),
            )
            removeHandler = { token, target, _ ->
                if (target == "usr_a") {
                    firstRemoveStarted.complete(Unit)
                    releaseFirstRemove.await()
                    typedRecords = typedRecords.filterNot { it.targetUserId == target }
                }
                // The second request reports success while the verification response still
                // contains its target, so the final state must not claim a full cleanup.
                PlayerModerationCleanupResponse(Result.success(Unit), token)
            }
        }
        SharedFlowCentre.emitAuthenticated(account)
        val model = PlayerModerationCleanupModel(source)
        try {
            model.loadIfNeeded()
            awaitUntil { model.state.value.hasLoaded }

            model.clearSelected()
            firstRemoveStarted.await()
            model.clearSelected()
            releaseFirstRemove.complete(Unit)
            awaitUntil { model.state.value.result != null }

            assertEquals(2, source.typedRequestCount)
            assertEquals(listOf("usr_a", "usr_b"), source.removedTargets)
            assertEquals(
                PlayerModerationCleanupResult(
                    kind = PlayerModerationCleanupResultKind.PartialFailure,
                    removedCount = 1,
                    failedCount = 1,
                ),
                model.state.value.result,
            )
            assertEquals(
                PlayerModerationTypeCount(PlayerModerationType.Block, 1),
                model.state.value.availableTypes.first(),
            )
            assertFalse(model.state.value.isClearing)
        } finally {
            releaseFirstRemove.complete(Unit)
            close(model)
            SharedFlowCentre.emitLogout()
        }
    }

    @Test
    fun renewedResponseTokenContinuesRemainingRequests() = runBlocking {
        val account = AccountDto(userId = "usr_account", username = "account")
        val source = FakePlayerModerationCleanupSource().apply {
            allRecords = listOf(
                record("one", "usr_a", "block"),
                record("two", "usr_b", "block"),
            )
            typedHandler = { token, _ ->
                val responseRecords = if (typedRequestCount == 1) allRecords else emptyList()
                PlayerModerationCleanupResponse(Result.success(responseRecords), token)
            }
            removeHandler = { token, target, _ ->
                if (target == "usr_a") {
                    SharedFlowCentre.emitAuthenticated(account)
                    val renewed = requireNotNull(SharedFlowCentre.currentSession.value?.token)
                    PlayerModerationCleanupResponse(Result.success(Unit), renewed)
                } else {
                    PlayerModerationCleanupResponse(Result.success(Unit), token)
                }
            }
        }
        SharedFlowCentre.emitAuthenticated(account)
        val initialToken = requireNotNull(SharedFlowCentre.currentSession.value?.token)
        val model = PlayerModerationCleanupModel(source)
        try {
            model.loadIfNeeded()
            awaitUntil { model.state.value.hasLoaded }
            model.clearSelected()
            awaitUntil { model.state.value.result != null }

            val renewedToken = requireNotNull(SharedFlowCentre.currentSession.value?.token)
            assertEquals(
                listOf(initialToken, renewedToken),
                source.removeRequestTokens,
            )
            assertEquals(listOf(initialToken, renewedToken), source.typedRequestTokens)
            assertEquals(
                PlayerModerationCleanupResultKind.Success,
                model.state.value.result?.kind,
            )
        } finally {
            close(model)
            SharedFlowCentre.emitLogout()
        }
    }

    @Test
    fun tokenReplacementDuringFirstPutStopsBeforeSecondAndDropsOldResult() = runBlocking {
        val account = AccountDto(userId = "usr_account", username = "account")
        val firstPutStarted = CompletableDeferred<Unit>()
        val releaseFirstPut = CompletableDeferred<Unit>()
        val source = FakePlayerModerationCleanupSource().apply {
            allRecords = listOf(
                record("one", "usr_a", "block"),
                record("two", "usr_b", "block"),
            )
            typedRecords = allRecords
            removeHandler = { token, target, _ ->
                if (target == "usr_a") {
                    firstPutStarted.complete(Unit)
                    withContext(NonCancellable) { releaseFirstPut.await() }
                }
                PlayerModerationCleanupResponse(Result.success(Unit), token)
            }
        }
        SharedFlowCentre.emitAuthenticated(account)
        val model = PlayerModerationCleanupModel(source)
        try {
            model.loadIfNeeded()
            awaitUntil { model.state.value.hasLoaded }
            model.clearSelected()
            firstPutStarted.await()

            source.allRecords = listOf(record("current", "usr_c", "mute"))
            SharedFlowCentre.emitAuthenticated(account)
            val replacementToken = requireNotNull(SharedFlowCentre.currentSession.value?.token)
            awaitUntil { model.state.value.sessionToken == replacementToken }
            releaseFirstPut.complete(Unit)
            awaitUntil {
                model.state.value.availableTypes.singleOrNull()?.type == PlayerModerationType.Mute
            }

            assertEquals(listOf("usr_a"), source.removedTargets)
            assertEquals(PlayerModerationType.Mute, model.state.value.selectedType)
            assertNull(model.state.value.result)
            assertFalse(model.state.value.isClearing)
        } finally {
            releaseFirstPut.complete(Unit)
            close(model)
            SharedFlowCentre.emitLogout()
        }
    }

    @Test
    fun accountSwitchDuringFirstPutStopsBeforeSecondAndDropsOldResult() = runBlocking {
        val accountA = AccountDto(userId = "usr_account_a", username = "account-a")
        val accountB = AccountDto(userId = "usr_account_b", username = "account-b")
        val firstPutStarted = CompletableDeferred<Unit>()
        val releaseFirstPut = CompletableDeferred<Unit>()
        val source = FakePlayerModerationCleanupSource().apply {
            allRecords = listOf(
                record("one", "usr_a", "block"),
                record("two", "usr_b", "block"),
            )
            typedRecords = allRecords
            removeHandler = { token, target, _ ->
                if (target == "usr_a") {
                    firstPutStarted.complete(Unit)
                    withContext(NonCancellable) { releaseFirstPut.await() }
                }
                PlayerModerationCleanupResponse(Result.success(Unit), token)
            }
        }
        SharedFlowCentre.emitAuthenticated(accountA)
        val model = PlayerModerationCleanupModel(source)
        try {
            model.loadIfNeeded()
            awaitUntil { model.state.value.hasLoaded }
            model.clearSelected()
            firstPutStarted.await()

            source.allRecords = listOf(record("current", "usr_c", "interactOn"))
            SharedFlowCentre.emitAuthenticated(accountB)
            awaitUntil {
                model.state.value.availableTypes.singleOrNull()?.type == PlayerModerationType.InteractOn
            }
            releaseFirstPut.complete(Unit)
            yield()

            assertEquals(listOf("usr_a"), source.removedTargets)
            assertEquals(PlayerModerationType.InteractOn, model.state.value.selectedType)
            assertNull(model.state.value.result)
            assertFalse(model.state.value.isClearing)
        } finally {
            releaseFirstPut.complete(Unit)
            close(model)
            SharedFlowCentre.emitLogout()
        }
    }
}

private class FakePlayerModerationCleanupSource : PlayerModerationCleanupSource {
    var allRecords: List<PlayerModerationData> = emptyList()
    var typedRecords: List<PlayerModerationData> = emptyList()
    var typedRequestCount: Int = 0
    val typedRequestTokens = mutableListOf<AccountSessionToken>()
    val removedTargets = mutableListOf<String>()
    val removeRequestTokens = mutableListOf<AccountSessionToken>()
    var typedHandler: (suspend (
        AccountSessionToken,
        PlayerModerationType,
    ) -> PlayerModerationCleanupResponse<List<PlayerModerationData>>?)? = null
    var removeHandler: suspend (
        AccountSessionToken,
        String,
        PlayerModerationType,
    ) -> PlayerModerationCleanupResponse<Unit>? = { token, _, _ ->
        PlayerModerationCleanupResponse(Result.success(Unit), token)
    }

    override suspend fun getAll(
        sessionToken: AccountSessionToken,
    ): PlayerModerationCleanupResponse<List<PlayerModerationData>>? =
        PlayerModerationCleanupResponse(Result.success(allRecords), sessionToken)

    override suspend fun get(
        sessionToken: AccountSessionToken,
        type: PlayerModerationType,
    ): PlayerModerationCleanupResponse<List<PlayerModerationData>>? {
        typedRequestCount++
        typedRequestTokens += sessionToken
        return typedHandler?.invoke(sessionToken, type)
            ?: PlayerModerationCleanupResponse(Result.success(typedRecords), sessionToken)
    }

    override suspend fun remove(
        sessionToken: AccountSessionToken,
        targetUserId: String,
        type: PlayerModerationType,
    ): PlayerModerationCleanupResponse<Unit>? {
        removedTargets += targetUserId
        removeRequestTokens += sessionToken
        return removeHandler(sessionToken, targetUserId, type)
    }
}

private fun record(id: String, target: String, type: String) = PlayerModerationData(
    id = "pmod_$id",
    targetUserId = target,
    type = type,
)

private fun close(model: PlayerModerationCleanupModel) {
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
