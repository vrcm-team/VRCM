package io.github.vrcmteam.vrcm.presentation.screens.world

import io.github.vrcmteam.vrcm.core.shared.AccountSessionToken
import io.github.vrcmteam.vrcm.network.api.attributes.BlueprintType
import io.github.vrcmteam.vrcm.network.api.instances.data.InstanceCloseResponse
import io.github.vrcmteam.vrcm.network.supports.VRCApiException
import io.github.vrcmteam.vrcm.presentation.screens.world.data.InstanceVo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal const val GROUP_INSTANCE_MANAGE_PERMISSION = "group-instance-manage"

internal data class InstanceCloseTarget(
    val worldId: String,
    val instanceId: String,
    val ownerId: String,
) {
    val location: String = "$worldId:$instanceId"
}

internal data class InstanceCloseRequest(
    val target: InstanceCloseTarget,
    val sessionToken: AccountSessionToken,
)

internal sealed interface InstanceCloseState {
    data object Idle : InstanceCloseState
    data class Authorizing(val request: InstanceCloseRequest) : InstanceCloseState
    data class AwaitingConfirmation(val request: InstanceCloseRequest) : InstanceCloseState
    data class Submitting(val request: InstanceCloseRequest) : InstanceCloseState
}

internal val InstanceCloseState.requestOrNull: InstanceCloseRequest?
    get() = when (this) {
        InstanceCloseState.Idle -> null
        is InstanceCloseState.Authorizing -> request
        is InstanceCloseState.AwaitingConfirmation -> request
        is InstanceCloseState.Submitting -> request
    }

internal data class InstanceCloseSessionResult<T>(
    val result: Result<T>,
    val sessionToken: AccountSessionToken,
)

internal class InstanceCloseResponseMismatchException : IllegalStateException()

internal sealed interface InstanceCloseAuthorizationResult {
    data object Ready : InstanceCloseAuthorizationResult
    data object Busy : InstanceCloseAuthorizationResult
    data object Abandoned : InstanceCloseAuthorizationResult
    data object NotAllowed : InstanceCloseAuthorizationResult
    data class SessionChanged(val userId: String?) : InstanceCloseAuthorizationResult
    data class Failed(val error: Throwable) : InstanceCloseAuthorizationResult
}

internal sealed interface InstanceCloseSubmissionResult {
    data class Closed(
        val request: InstanceCloseRequest,
        val instance: InstanceCloseResponse,
    ) : InstanceCloseSubmissionResult
    data object Busy : InstanceCloseSubmissionResult
    data object Abandoned : InstanceCloseSubmissionResult
    data class SessionChanged(val userId: String) : InstanceCloseSubmissionResult
    data class Failed(val error: Throwable) : InstanceCloseSubmissionResult
}

/** Coordinates permission checks and submission without letting responses cross account sessions. */
internal class InstanceCloseCoordinator(
    private val currentSessionToken: () -> AccountSessionToken?,
    private val isCurrentSession: (AccountSessionToken) -> Boolean,
    private val fetchGroupPermissions: suspend (
        AccountSessionToken,
        String,
    ) -> InstanceCloseSessionResult<List<String>>?,
    private val fetchInstance: suspend (
        AccountSessionToken,
        InstanceCloseTarget,
    ) -> InstanceCloseSessionResult<InstanceCloseResponse>?,
    private val closeInstance: suspend (
        AccountSessionToken,
        InstanceCloseTarget,
    ) -> InstanceCloseSessionResult<InstanceCloseResponse>?,
) {
    private val _state = MutableStateFlow<InstanceCloseState>(InstanceCloseState.Idle)
    val state: StateFlow<InstanceCloseState> = _state.asStateFlow()

    suspend fun authorize(target: InstanceCloseTarget): InstanceCloseAuthorizationResult {
        val token = currentSessionToken()
            ?: return InstanceCloseAuthorizationResult.SessionChanged(userId = null)
        val request = InstanceCloseRequest(target, token)
        val authorizing = InstanceCloseState.Authorizing(request)
        if (!_state.compareAndSet(InstanceCloseState.Idle, authorizing)) {
            return InstanceCloseAuthorizationResult.Busy
        }

        if (!isCurrentSession(token)) {
            return finishAuthorization(
                authorizing,
                InstanceCloseState.Idle,
                InstanceCloseAuthorizationResult.SessionChanged(token.userId),
            )
        }

        return when (target.ownerId.blueprintTypeOrNull()) {
            BlueprintType.User -> authorizePersonalInstance(authorizing)
            BlueprintType.Group -> authorizeGroupInstance(authorizing)
            else -> finishAuthorization(
                authorizing,
                InstanceCloseState.Idle,
                InstanceCloseAuthorizationResult.NotAllowed,
            )
        }
    }

    private fun authorizePersonalInstance(
        authorizing: InstanceCloseState.Authorizing,
    ): InstanceCloseAuthorizationResult {
        val request = authorizing.request
        if (request.target.ownerId != request.sessionToken.userId) {
            return finishAuthorization(
                authorizing,
                InstanceCloseState.Idle,
                InstanceCloseAuthorizationResult.NotAllowed,
            )
        }
        if (!isCurrentSession(request.sessionToken)) {
            return finishAuthorization(
                authorizing,
                InstanceCloseState.Idle,
                InstanceCloseAuthorizationResult.SessionChanged(request.sessionToken.userId),
            )
        }
        return finishAuthorization(
            authorizing,
            InstanceCloseState.AwaitingConfirmation(request),
            InstanceCloseAuthorizationResult.Ready,
        )
    }

    private suspend fun authorizeGroupInstance(
        authorizing: InstanceCloseState.Authorizing,
    ): InstanceCloseAuthorizationResult {
        val request = authorizing.request
        val response = try {
            fetchGroupPermissions(request.sessionToken, request.target.ownerId)
        } catch (cancellation: CancellationException) {
            _state.compareAndSet(authorizing, InstanceCloseState.Idle)
            throw cancellation
        } catch (error: Throwable) {
            return finishAuthorization(
                authorizing,
                InstanceCloseState.Idle,
                InstanceCloseAuthorizationResult.Failed(error),
            )
        } ?: return finishAuthorization(
            authorizing,
            InstanceCloseState.Idle,
            InstanceCloseAuthorizationResult.SessionChanged(request.sessionToken.userId),
        )

        if (response.sessionToken.userId != request.sessionToken.userId ||
            !isCurrentSession(response.sessionToken)
        ) {
            return finishAuthorization(
                authorizing,
                InstanceCloseState.Idle,
                InstanceCloseAuthorizationResult.SessionChanged(request.sessionToken.userId),
            )
        }

        val permissions = response.result.getOrElse { error ->
            return finishAuthorization(
                authorizing,
                InstanceCloseState.Idle,
                InstanceCloseAuthorizationResult.Failed(error),
            )
        }
        if (GROUP_INSTANCE_MANAGE_PERMISSION !in permissions && "*" !in permissions) {
            return finishAuthorization(
                authorizing,
                InstanceCloseState.Idle,
                InstanceCloseAuthorizationResult.NotAllowed,
            )
        }

        val refreshedRequest = request.copy(sessionToken = response.sessionToken)
        return finishAuthorization(
            authorizing,
            InstanceCloseState.AwaitingConfirmation(refreshedRequest),
            InstanceCloseAuthorizationResult.Ready,
        )
    }

    suspend fun submit(): InstanceCloseSubmissionResult {
        val awaiting = _state.value as? InstanceCloseState.AwaitingConfirmation
            ?: return InstanceCloseSubmissionResult.Busy
        val submitting = InstanceCloseState.Submitting(awaiting.request)
        if (!_state.compareAndSet(awaiting, submitting)) return InstanceCloseSubmissionResult.Busy

        val request = submitting.request
        if (!isCurrentSession(request.sessionToken)) {
            _state.compareAndSet(submitting, InstanceCloseState.Idle)
            return InstanceCloseSubmissionResult.SessionChanged(request.sessionToken.userId)
        }

        val response = try {
            closeInstance(request.sessionToken, request.target)
        } catch (cancellation: CancellationException) {
            _state.compareAndSet(submitting, InstanceCloseState.Idle)
            throw cancellation
        } catch (error: Throwable) {
            _state.compareAndSet(submitting, InstanceCloseState.AwaitingConfirmation(request))
            return InstanceCloseSubmissionResult.Failed(error)
        } ?: run {
            _state.compareAndSet(submitting, InstanceCloseState.Idle)
            return InstanceCloseSubmissionResult.SessionChanged(request.sessionToken.userId)
        }

        if (response.sessionToken.userId != request.sessionToken.userId ||
            !isCurrentSession(response.sessionToken)
        ) {
            _state.compareAndSet(submitting, InstanceCloseState.Idle)
            return InstanceCloseSubmissionResult.SessionChanged(request.sessionToken.userId)
        }

        val refreshedRequest = request.copy(sessionToken = response.sessionToken)
        val closedInstance = response.result.getOrElse { error ->
            if (error is VRCApiException && error.code == 403) {
                return verifyForbiddenClose(submitting, refreshedRequest, error)
            }
            return failSubmission(submitting, refreshedRequest, error)
        }
        if (!closedInstance.matches(request.target)) {
            return failSubmission(
                submitting,
                refreshedRequest,
                InstanceCloseResponseMismatchException(),
            )
        }

        return completeSubmission(submitting, refreshedRequest, closedInstance)
    }

    private suspend fun verifyForbiddenClose(
        submitting: InstanceCloseState.Submitting,
        request: InstanceCloseRequest,
        originalError: VRCApiException,
    ): InstanceCloseSubmissionResult {
        val response = try {
            fetchInstance(request.sessionToken, request.target)
        } catch (cancellation: CancellationException) {
            _state.compareAndSet(submitting, InstanceCloseState.Idle)
            throw cancellation
        } catch (_: Throwable) {
            return failSubmission(submitting, request, originalError)
        } ?: run {
            _state.compareAndSet(submitting, InstanceCloseState.Idle)
            return InstanceCloseSubmissionResult.SessionChanged(request.sessionToken.userId)
        }

        if (response.sessionToken.userId != request.sessionToken.userId ||
            !isCurrentSession(response.sessionToken)
        ) {
            _state.compareAndSet(submitting, InstanceCloseState.Idle)
            return InstanceCloseSubmissionResult.SessionChanged(request.sessionToken.userId)
        }

        val refreshedRequest = request.copy(sessionToken = response.sessionToken)
        val instance = response.result.getOrElse {
            return failSubmission(submitting, refreshedRequest, originalError)
        }
        if (!instance.matches(request.target)) {
            return failSubmission(
                submitting,
                refreshedRequest,
                InstanceCloseResponseMismatchException(),
            )
        }
        if (!instance.isClosed) {
            return failSubmission(submitting, refreshedRequest, originalError)
        }
        return completeSubmission(submitting, refreshedRequest, instance)
    }

    private fun failSubmission(
        submitting: InstanceCloseState.Submitting,
        request: InstanceCloseRequest,
        error: Throwable,
    ): InstanceCloseSubmissionResult {
        _state.compareAndSet(
            submitting,
            InstanceCloseState.AwaitingConfirmation(request),
        )
        return InstanceCloseSubmissionResult.Failed(error)
    }

    private fun completeSubmission(
        submitting: InstanceCloseState.Submitting,
        request: InstanceCloseRequest,
        response: InstanceCloseResponse,
    ): InstanceCloseSubmissionResult =
        if (_state.compareAndSet(submitting, InstanceCloseState.Idle)) {
            InstanceCloseSubmissionResult.Closed(request, response)
        } else {
            InstanceCloseSubmissionResult.Abandoned
        }

    /** Only an idle confirmation can be invalidated immediately by an unrelated session change. */
    fun onSessionChanged(sessionToken: AccountSessionToken?) {
        while (true) {
            val awaiting = _state.value as? InstanceCloseState.AwaitingConfirmation ?: return
            if (awaiting.request.sessionToken == sessionToken) return
            if (_state.compareAndSet(awaiting, InstanceCloseState.Idle)) return
        }
    }

    fun abandon(location: String) {
        while (true) {
            val current = _state.value
            val canAbandon = current is InstanceCloseState.Authorizing ||
                current is InstanceCloseState.AwaitingConfirmation
            if (!canAbandon || current.requestOrNull?.target?.location != location) return
            if (_state.compareAndSet(current, InstanceCloseState.Idle)) return
        }
    }

    private fun finishAuthorization(
        expected: InstanceCloseState.Authorizing,
        next: InstanceCloseState,
        result: InstanceCloseAuthorizationResult,
    ): InstanceCloseAuthorizationResult =
        if (_state.compareAndSet(expected, next)) result else InstanceCloseAuthorizationResult.Abandoned
}

internal fun InstanceVo.closeTargetOrNull(): InstanceCloseTarget? {
    if (worldId.isBlank() || instanceId.isBlank() || ownerId.isNullOrBlank()) return null
    val target = InstanceCloseTarget(
        worldId = worldId,
        instanceId = instanceId,
        ownerId = ownerId,
    )
    return target.takeIf { location == target.location }
}

internal fun InstanceVo.canOfferInstanceClose(sessionToken: AccountSessionToken?): Boolean {
    val target = closeTargetOrNull() ?: return false
    return when (target.ownerId.blueprintTypeOrNull()) {
        BlueprintType.User -> target.ownerId == sessionToken?.userId
        BlueprintType.Group -> sessionToken != null
        else -> false
    }
}

private fun String.blueprintTypeOrNull(): BlueprintType? =
    runCatching { BlueprintType.fromValue(this) }.getOrNull()

private fun InstanceCloseResponse.matches(target: InstanceCloseTarget): Boolean =
    worldId == target.worldId && instanceId == target.instanceId && location == target.location
