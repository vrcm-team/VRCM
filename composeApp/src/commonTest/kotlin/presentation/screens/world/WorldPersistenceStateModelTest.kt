package io.github.vrcmteam.vrcm.presentation.screens.world

import io.github.vrcmteam.vrcm.core.shared.AccountSessionToken
import io.github.vrcmteam.vrcm.core.shared.AuthenticatedAccount
import io.github.vrcmteam.vrcm.service.SessionBoundResponse
import io.github.vrcmteam.vrcm.service.data.AccountDto
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class WorldPersistenceStateModelTest {
    @Test
    fun confirmedDeletionMovesExistingDataToDeletedMissingState() = runTest {
        val account = authenticated("usr_a", generation = 1)
        val sessions = MutableStateFlow<AuthenticatedAccount?>(account)
        val request = ControlledWorldPersistenceRequest().apply {
            completeExists(account.token, exists = true)
            completeDelete(account.token)
        }
        val model = stateModel(request, sessions)
        model.bindWorld("wrld_target")

        model.check()
        advanceUntilIdle()
        assertEquals(WorldPersistenceStatus.Exists, model.state.value.status)

        model.confirmDeletion()
        assertEquals(0, request.deleteCount)

        model.requestDeletion()
        assertTrue(model.state.value.confirmingDeletion)
        model.confirmDeletion()
        advanceUntilIdle()

        assertEquals(WorldPersistenceStatus.Missing(deleted = true), model.state.value.status)
        assertEquals(1, request.deleteCount)
    }

    @Test
    fun repeatedActionsCannotStartDuplicateRequests() = runTest {
        val account = authenticated("usr_a", generation = 1)
        val sessions = MutableStateFlow<AuthenticatedAccount?>(account)
        val request = ControlledWorldPersistenceRequest()
        val model = stateModel(request, sessions)
        model.bindWorld("wrld_target")

        model.check()
        model.check()
        assertEquals(1, request.existsCount)
        assertEquals(WorldPersistenceStatus.Checking, model.state.value.status)

        request.completeExists(account.token, exists = true)
        advanceUntilIdle()
        model.requestDeletion()
        model.confirmDeletion()
        model.confirmDeletion()

        assertEquals(1, request.deleteCount)
        assertEquals(WorldPersistenceStatus.Deleting, model.state.value.status)

        request.completeDelete(account.token)
        advanceUntilIdle()
        assertEquals(WorldPersistenceStatus.Missing(deleted = true), model.state.value.status)
    }

    @Test
    fun refreshedSameAccountTokenAllowsRetriedCheckToCommit() = runTest {
        val first = authenticated("usr_a", generation = 1)
        val refreshed = authenticated("usr_a", generation = 2)
        val sessions = MutableStateFlow<AuthenticatedAccount?>(first)
        val request = ControlledWorldPersistenceRequest()
        val model = stateModel(request, sessions)
        model.bindWorld("wrld_target")

        model.check()
        sessions.value = refreshed
        runCurrent()
        request.completeExists(refreshed.token, exists = true)
        advanceUntilIdle()

        assertEquals(WorldPersistenceStatus.Exists, model.state.value.status)
        assertEquals(first.token, request.existsTokens.single())
    }

    @Test
    fun oldTokenResultAfterExternalSameAccountGenerationChangeIsRejected() = runTest {
        val first = authenticated("usr_a", generation = 1)
        val refreshed = authenticated("usr_a", generation = 2)
        val sessions = MutableStateFlow<AuthenticatedAccount?>(first)
        val request = ControlledWorldPersistenceRequest()
        val model = stateModel(request, sessions)
        model.bindWorld("wrld_target")

        model.check()
        sessions.value = refreshed
        runCurrent()
        request.completeExists(first.token, exists = true)
        advanceUntilIdle()

        assertEquals(WorldPersistenceUiState(), model.state.value)
    }

    @Test
    fun accountSwitchClearsExistingStateAndCannotReuseDeletionConfirmation() = runTest {
        val first = authenticated("usr_a", generation = 1)
        val second = authenticated("usr_b", generation = 2)
        val sessions = MutableStateFlow<AuthenticatedAccount?>(first)
        val request = ControlledWorldPersistenceRequest().apply {
            completeExists(first.token, exists = true)
        }
        val model = stateModel(request, sessions)
        model.bindWorld("wrld_target")
        model.check()
        advanceUntilIdle()
        model.requestDeletion()
        assertTrue(model.state.value.confirmingDeletion)

        sessions.value = second
        model.requestDeletion()
        model.confirmDeletion()
        assertEquals(WorldPersistenceUiState(), model.state.value)
        assertFalse(model.state.value.confirmingDeletion)
        assertEquals(0, request.deleteCount)
        runCurrent()
    }

    @Test
    fun accountSwitchRejectsLateExistenceResult() = runTest {
        val first = authenticated("usr_a", generation = 1)
        val second = authenticated("usr_b", generation = 2)
        val sessions = MutableStateFlow<AuthenticatedAccount?>(first)
        val request = ControlledWorldPersistenceRequest()
        val model = stateModel(request, sessions)
        model.bindWorld("wrld_target")

        model.check()
        sessions.value = second
        runCurrent()
        assertEquals(WorldPersistenceUiState(), model.state.value)

        request.completeExists(first.token, exists = true)
        advanceUntilIdle()
        assertEquals(WorldPersistenceUiState(), model.state.value)
    }

    @Test
    fun accountSwitchRejectsLateDeletionResult() = runTest {
        val first = authenticated("usr_a", generation = 1)
        val second = authenticated("usr_b", generation = 2)
        val sessions = MutableStateFlow<AuthenticatedAccount?>(first)
        val request = ControlledWorldPersistenceRequest().apply {
            completeExists(first.token, exists = true)
        }
        val model = stateModel(request, sessions)
        model.bindWorld("wrld_target")
        model.check()
        advanceUntilIdle()
        model.requestDeletion()
        model.confirmDeletion()
        assertEquals(WorldPersistenceStatus.Deleting, model.state.value.status)

        sessions.value = second
        runCurrent()
        assertEquals(WorldPersistenceUiState(), model.state.value)

        request.completeDelete(first.token)
        advanceUntilIdle()
        assertEquals(WorldPersistenceUiState(), model.state.value)
    }

    private fun TestScope.stateModel(
        request: WorldPersistenceRequest,
        sessions: MutableStateFlow<AuthenticatedAccount?>,
    ) = WorldPersistenceStateModel(
        request = request,
        scope = backgroundScope,
        requestDispatcher = UnconfinedTestDispatcher(testScheduler),
        sessionFlow = sessions,
        isCurrentSession = { token -> sessions.value?.token == token },
    )
}

private class ControlledWorldPersistenceRequest : WorldPersistenceRequest {
    private val existsResponse = CompletableDeferred<SessionBoundResponse<Boolean>?>()
    private val deleteResponse = CompletableDeferred<SessionBoundResponse<Unit>?>()

    var existsCount = 0
        private set
    var deleteCount = 0
        private set
    val existsTokens = mutableListOf<AccountSessionToken>()

    override suspend fun exists(
        sessionToken: AccountSessionToken,
        worldId: String,
    ): SessionBoundResponse<Boolean>? {
        existsCount++
        existsTokens += sessionToken
        return existsResponse.await()
    }

    override suspend fun delete(
        sessionToken: AccountSessionToken,
        worldId: String,
    ): SessionBoundResponse<Unit>? {
        deleteCount++
        return deleteResponse.await()
    }

    fun completeExists(sessionToken: AccountSessionToken, exists: Boolean) {
        existsResponse.complete(
            SessionBoundResponse(Result.success(exists), sessionToken)
        )
    }

    fun completeDelete(sessionToken: AccountSessionToken) {
        deleteResponse.complete(
            SessionBoundResponse(Result.success(Unit), sessionToken)
        )
    }
}

private fun authenticated(userId: String, generation: Long) = AuthenticatedAccount(
    account = AccountDto(userId = userId),
    token = AccountSessionToken(userId = userId, generation = generation),
)
