package io.github.vrcmteam.vrcm.presentation.screens.user

import io.github.vrcmteam.vrcm.core.shared.AccountSessionToken
import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.network.api.users.UsersApi
import io.github.vrcmteam.vrcm.network.api.users.data.PlayerInteractionOverride
import io.github.vrcmteam.vrcm.network.api.users.data.PlayerInteractionSnapshot
import io.github.vrcmteam.vrcm.service.AuthService
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal sealed interface PlayerInteractionState {
    data object Unavailable : PlayerInteractionState

    data class Checking(val targetUserId: String) : PlayerInteractionState

    data class Ready(
        val targetUserId: String,
        val snapshot: PlayerInteractionSnapshot,
    ) : PlayerInteractionState

    data class Updating(
        val targetUserId: String,
        val snapshot: PlayerInteractionSnapshot,
        val requestedOverride: PlayerInteractionOverride,
    ) : PlayerInteractionState

    data class Failed(
        val targetUserId: String,
        val snapshot: PlayerInteractionSnapshot?,
        val retryOverride: PlayerInteractionOverride?,
    ) : PlayerInteractionState
}

internal sealed interface PlayerInteractionRequestResult {
    data object Succeeded : PlayerInteractionRequestResult
    data object Ignored : PlayerInteractionRequestResult
    data class Failed(val error: Throwable) : PlayerInteractionRequestResult
    data class Stale(val canReload: Boolean) : PlayerInteractionRequestResult
}

/**
 * Serializes one profile's interaction reads and writes while keeping every completion bound to
 * the account session and target that started it.
 */
internal class PlayerInteractionOverrideController(
    private val ownerUserId: String,
    initialSessionToken: AccountSessionToken?,
    private val authService: AuthService,
    private val usersApi: UsersApi,
    private val currentSessionToken: () -> AccountSessionToken? = {
        SharedFlowCentre.currentSession.value?.token
    },
    private val isCurrentSession: (AccountSessionToken) -> Boolean =
        SharedFlowCentre::isCurrentSession,
) {
    private val lock = SynchronizedObject()
    private val _state = MutableStateFlow<PlayerInteractionState>(PlayerInteractionState.Unavailable)
    val state: StateFlow<PlayerInteractionState> = _state.asStateFlow()

    private var expectedSessionToken = initialSessionToken
    private var boundTargetUserId: String? = null
    private var revision = 0L
    private var sessionChangedDuringOperation = false
    private var observedReplacementToken: AccountSessionToken? = null

    /** Returns true when a same-account replacement happened while idle and needs a fresh read. */
    fun onSessionChanged(sessionToken: AccountSessionToken?): Boolean = synchronized(lock) {
        if (sessionToken == expectedSessionToken) return@synchronized false
        if (sessionToken?.userId != ownerUserId || !isCurrentSession(sessionToken)) {
            invalidateLocked(clearExpectedSession = true)
            return@synchronized false
        }
        if (_state.value.isOperationInProgress()) {
            sessionChangedDuringOperation = true
            observedReplacementToken = sessionToken
            return@synchronized false
        }
        revision++
        expectedSessionToken = sessionToken
        _state.value = PlayerInteractionState.Unavailable
        true
    }

    suspend fun refresh(targetUserId: String): PlayerInteractionRequestResult {
        val operation = startLoad(targetUserId)
            ?: return PlayerInteractionRequestResult.Ignored
        return when (
            val step = runBoundRequest(operation.id, operation.sessionToken) {
                usersApi.getPlayerInteractionSnapshot(targetUserId)
            }
        ) {
            is BoundStep.Success -> {
                if (completeLoad(operation.id, targetUserId, step.value)) {
                    PlayerInteractionRequestResult.Succeeded
                } else {
                    PlayerInteractionRequestResult.Stale(canReload = false)
                }
            }
            is BoundStep.Failure -> {
                if (failLoad(operation.id, targetUserId)) {
                    PlayerInteractionRequestResult.Failed(step.error)
                } else {
                    PlayerInteractionRequestResult.Stale(canReload = false)
                }
            }
            is BoundStep.Stale -> PlayerInteractionRequestResult.Stale(step.canReload)
        }
    }

    suspend fun setOverride(
        targetUserId: String,
        requestedOverride: PlayerInteractionOverride,
    ): PlayerInteractionRequestResult {
        val operation = startUpdate(targetUserId, requestedOverride)
            ?: return if (canReloadRejectedUpdate(targetUserId)) {
                PlayerInteractionRequestResult.Stale(canReload = true)
            } else {
                PlayerInteractionRequestResult.Ignored
            }
        var sessionToken = operation.sessionToken

        for (existingOverride in operation.snapshot.explicitOverrides) {
            when (
                val removal = runBoundRequest(operation.id, sessionToken) {
                    usersApi.removePlayerInteractionOverride(
                        targetUserId,
                        existingOverride,
                    )
                }
            ) {
                is BoundStep.Success -> sessionToken = removal.sessionToken
                is BoundStep.Failure -> return reconcileAfterWriteFailure(
                    operation = operation,
                    sessionToken = removal.sessionToken,
                    originalError = removal.error,
                )
                is BoundStep.Stale -> return PlayerInteractionRequestResult.Stale(removal.canReload)
            }
        }

        return when (
            val creation = runBoundRequest(operation.id, sessionToken) {
                usersApi.createPlayerInteractionOverride(targetUserId, requestedOverride)
            }
        ) {
            is BoundStep.Success -> {
                val snapshot = PlayerInteractionSnapshot(
                    effectiveOverride = requestedOverride,
                    explicitOverrides = setOf(requestedOverride),
                )
                if (completeReady(operation.id, targetUserId, snapshot)) {
                    PlayerInteractionRequestResult.Succeeded
                } else {
                    PlayerInteractionRequestResult.Stale(canReload = false)
                }
            }
            is BoundStep.Failure -> reconcileAfterWriteFailure(
                operation = operation,
                sessionToken = creation.sessionToken,
                originalError = creation.error,
            )
            is BoundStep.Stale -> PlayerInteractionRequestResult.Stale(creation.canReload)
        }
    }

    private suspend fun reconcileAfterWriteFailure(
        operation: UpdateOperation,
        sessionToken: AccountSessionToken,
        originalError: Throwable,
    ): PlayerInteractionRequestResult = when (
        val authority = runBoundRequest(operation.id, sessionToken) {
            usersApi.getPlayerInteractionSnapshot(operation.targetUserId)
        }
    ) {
        is BoundStep.Success -> {
            if (authority.value.isSettledAt(operation.requestedOverride)) {
                if (completeReady(operation.id, operation.targetUserId, authority.value)) {
                    PlayerInteractionRequestResult.Succeeded
                } else {
                    PlayerInteractionRequestResult.Stale(canReload = false)
                }
            } else if (
                completeFailure(
                    operationId = operation.id,
                    targetUserId = operation.targetUserId,
                    snapshot = authority.value,
                    retryOverride = operation.requestedOverride,
                )
            ) {
                PlayerInteractionRequestResult.Failed(originalError)
            } else {
                PlayerInteractionRequestResult.Stale(canReload = false)
            }
        }
        is BoundStep.Failure -> {
            if (
                completeFailure(
                    operationId = operation.id,
                    targetUserId = operation.targetUserId,
                    snapshot = null,
                    retryOverride = null,
                )
            ) {
                PlayerInteractionRequestResult.Failed(originalError)
            } else {
                PlayerInteractionRequestResult.Stale(canReload = false)
            }
        }
        is BoundStep.Stale -> PlayerInteractionRequestResult.Stale(authority.canReload)
    }

    private suspend fun <T> runBoundRequest(
        operationId: Long,
        sessionToken: AccountSessionToken,
        request: suspend () -> T,
    ): BoundStep<T> {
        val response = authService.runSessionBoundCatching(sessionToken, request)
            ?: return BoundStep.Stale(markStale(operationId))
        if (!acceptResponseSession(operationId, response.sessionToken)) {
            return BoundStep.Stale(markStale(operationId))
        }
        return response.result.fold(
            onSuccess = { BoundStep.Success(it, response.sessionToken) },
            onFailure = { BoundStep.Failure(it, response.sessionToken) },
        )
    }

    private fun startLoad(targetUserId: String): Operation? {
        val currentToken = validCurrentSessionToken() ?: run {
            synchronized(lock) { invalidateLocked(clearExpectedSession = true) }
            return null
        }
        return synchronized(lock) {
            val currentState = _state.value
            if (boundTargetUserId == targetUserId && currentState.isOperationInProgress()) {
                return@synchronized null
            }
            if (boundTargetUserId != targetUserId) {
                revision++
                boundTargetUserId = targetUserId
            }
            expectedSessionToken = currentToken
            clearObservedSessionChangeLocked()
            Operation(
                id = ++revision,
                sessionToken = currentToken,
            ).also {
                _state.value = PlayerInteractionState.Checking(targetUserId)
            }
        }
    }

    private fun startUpdate(
        targetUserId: String,
        requestedOverride: PlayerInteractionOverride,
    ): UpdateOperation? {
        if (requestedOverride == PlayerInteractionOverride.Default) return null
        val currentToken = validCurrentSessionToken() ?: run {
            synchronized(lock) { invalidateLocked(clearExpectedSession = true) }
            return null
        }
        return synchronized(lock) {
            if (boundTargetUserId != targetUserId) return@synchronized null
            if (expectedSessionToken != currentToken) {
                invalidateLocked(clearExpectedSession = false)
                expectedSessionToken = currentToken
                return@synchronized null
            }
            val snapshot = when (val currentState = _state.value) {
                is PlayerInteractionState.Ready -> currentState.snapshot
                is PlayerInteractionState.Failed -> currentState.snapshot
                    ?.takeIf { currentState.retryOverride == requestedOverride }
                else -> null
            } ?: return@synchronized null
            if (snapshot.isSettledAt(requestedOverride)) return@synchronized null

            clearObservedSessionChangeLocked()
            UpdateOperation(
                id = ++revision,
                sessionToken = currentToken,
                targetUserId = targetUserId,
                snapshot = snapshot,
                requestedOverride = requestedOverride,
            ).also {
                _state.value = PlayerInteractionState.Updating(
                    targetUserId = targetUserId,
                    snapshot = snapshot,
                    requestedOverride = requestedOverride,
                )
            }
        }
    }

    private fun validCurrentSessionToken(): AccountSessionToken? =
        currentSessionToken()?.takeIf {
            it.userId == ownerUserId && isCurrentSession(it)
        }

    private fun canReloadRejectedUpdate(targetUserId: String): Boolean = synchronized(lock) {
        boundTargetUserId == targetUserId &&
            _state.value is PlayerInteractionState.Unavailable &&
            validCurrentSessionToken() != null
    }

    private fun acceptResponseSession(
        operationId: Long,
        responseSessionToken: AccountSessionToken,
    ): Boolean = synchronized(lock) {
        if (operationId != revision || !_state.value.isOperationInProgress()) {
            return@synchronized false
        }
        if (responseSessionToken.userId != ownerUserId || !isCurrentSession(responseSessionToken)) {
            return@synchronized false
        }
        if (sessionChangedDuringOperation && observedReplacementToken != responseSessionToken) {
            return@synchronized false
        }
        expectedSessionToken = responseSessionToken
        clearObservedSessionChangeLocked()
        true
    }

    private fun completeLoad(
        operationId: Long,
        targetUserId: String,
        snapshot: PlayerInteractionSnapshot,
    ): Boolean = synchronized(lock) {
        if (operationId != revision || _state.value !is PlayerInteractionState.Checking) {
            return@synchronized false
        }
        _state.value = PlayerInteractionState.Ready(targetUserId, snapshot)
        true
    }

    private fun failLoad(operationId: Long, targetUserId: String): Boolean = synchronized(lock) {
        if (operationId != revision || _state.value !is PlayerInteractionState.Checking) {
            return@synchronized false
        }
        _state.value = PlayerInteractionState.Failed(
            targetUserId = targetUserId,
            snapshot = null,
            retryOverride = null,
        )
        true
    }

    private fun completeReady(
        operationId: Long,
        targetUserId: String,
        snapshot: PlayerInteractionSnapshot,
    ): Boolean = synchronized(lock) {
        if (operationId != revision || _state.value !is PlayerInteractionState.Updating) {
            return@synchronized false
        }
        _state.value = PlayerInteractionState.Ready(targetUserId, snapshot)
        true
    }

    private fun completeFailure(
        operationId: Long,
        targetUserId: String,
        snapshot: PlayerInteractionSnapshot?,
        retryOverride: PlayerInteractionOverride?,
    ): Boolean = synchronized(lock) {
        if (operationId != revision || _state.value !is PlayerInteractionState.Updating) {
            return@synchronized false
        }
        _state.value = PlayerInteractionState.Failed(
            targetUserId = targetUserId,
            snapshot = snapshot,
            retryOverride = retryOverride,
        )
        true
    }

    private fun markStale(operationId: Long): Boolean = synchronized(lock) {
        if (operationId != revision) return@synchronized false
        val replacement = validCurrentSessionToken()
        invalidateLocked(clearExpectedSession = replacement == null)
        if (replacement != null) expectedSessionToken = replacement
        replacement != null
    }

    private fun invalidateLocked(clearExpectedSession: Boolean) {
        revision++
        clearObservedSessionChangeLocked()
        if (clearExpectedSession) expectedSessionToken = null
        _state.value = PlayerInteractionState.Unavailable
    }

    private fun clearObservedSessionChangeLocked() {
        sessionChangedDuringOperation = false
        observedReplacementToken = null
    }

    private fun PlayerInteractionState.isOperationInProgress(): Boolean =
        this is PlayerInteractionState.Checking || this is PlayerInteractionState.Updating

    private data class Operation(
        val id: Long,
        val sessionToken: AccountSessionToken,
    )

    private data class UpdateOperation(
        val id: Long,
        val sessionToken: AccountSessionToken,
        val targetUserId: String,
        val snapshot: PlayerInteractionSnapshot,
        val requestedOverride: PlayerInteractionOverride,
    )

    private sealed interface BoundStep<out T> {
        data class Success<T>(
            val value: T,
            val sessionToken: AccountSessionToken,
        ) : BoundStep<T>

        data class Failure(
            val error: Throwable,
            val sessionToken: AccountSessionToken,
        ) : BoundStep<Nothing>

        data class Stale(val canReload: Boolean) : BoundStep<Nothing>
    }
}
