package io.github.vrcmteam.vrcm.presentation.compoments

import io.github.vrcmteam.vrcm.service.OfficialLinkRequest
import io.github.vrcmteam.vrcm.service.OfficialLinkTarget
import io.github.vrcmteam.vrcm.service.parseOfficialLink
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal data class OfficialLinkOperation(
    val id: Long,
    val target: OfficialLinkTarget,
    val externalRequest: OfficialLinkRequest? = null,
)

internal sealed interface OfficialLinkPromptState {
    data object Idle : OfficialLinkPromptState
    data class ClipboardConfirmation(val operation: OfficialLinkOperation) : OfficialLinkPromptState
    data class Resolving(val operation: OfficialLinkOperation) : OfficialLinkPromptState
    data class Failure(val operation: OfficialLinkOperation?) : OfficialLinkPromptState
}

/** Saveable prompt data used to rebuild a controller after its coroutine scope is gone. */
internal data class OfficialLinkPromptSnapshot(
    val state: OfficialLinkPromptState,
    val nextOperationId: Long,
    val lastInspectedTargetKey: String?,
)

/** Owns the prompt state and guarantees that only the latest authenticated resolution can finish. */
internal class OfficialLinkPromptController<T>(
    private val scope: CoroutineScope,
    private val resolve: suspend (OfficialLinkTarget) -> Result<T>,
    private val onResolved: (T) -> Unit,
    private val onExternalConsumed: (OfficialLinkRequest) -> Unit,
    private val isOperationCurrent: (OfficialLinkOperation) -> Boolean = { true },
    initialSnapshot: OfficialLinkPromptSnapshot? = null,
) {
    private val _state = MutableStateFlow(
        initialSnapshot?.restoredState() ?: OfficialLinkPromptState.Idle,
    )
    val state: StateFlow<OfficialLinkPromptState> = _state.asStateFlow()

    private var activeJob: Job? = null
    private var isAuthenticated = false
    private var nextOperationId = initialSnapshot?.nextOperationId ?: 0L
    private var lastInspectedTargetKey = initialSnapshot?.lastInspectedTargetKey
    private var pendingRestoredExternalFailure = (initialSnapshot?.state as? OfficialLinkPromptState.Failure)
        ?.operation
        ?.takeIf { it.externalRequest != null }

    fun snapshot() = OfficialLinkPromptSnapshot(
        state = _state.value,
        nextOperationId = nextOperationId,
        lastInspectedTargetKey = lastInspectedTargetKey,
    )

    fun updateAuthentication(authenticated: Boolean) {
        isAuthenticated = authenticated
        if (!authenticated) {
            cancelActiveResolution()
            _state.value = OfficialLinkPromptState.Idle
        }
    }

    fun inspectClipboard(value: String) {
        if (!isAuthenticated) return
        val target = parseOfficialLink(value) ?: return
        val targetKey = target.key()
        if (targetKey == lastInspectedTargetKey) return

        discardCurrentExternalRequest()
        cancelActiveResolution()
        markInspected(targetKey)
        _state.value = OfficialLinkPromptState.ClipboardConfirmation(
            newOperation(target = target),
        )
    }

    fun openExternal(request: OfficialLinkRequest) {
        if (!isAuthenticated) return
        val restoredFailure = pendingRestoredExternalFailure
        pendingRestoredExternalFailure = null
        val target = parseOfficialLink(request.url)
        if (target == null) {
            cancelActiveResolution()
            onExternalConsumed(request)
            _state.value = OfficialLinkPromptState.Failure(operation = null)
            return
        }

        if (
            restoredFailure != null &&
            _state.value == OfficialLinkPromptState.Failure(restoredFailure) &&
            restoredFailure.externalRequest?.url == request.url &&
            restoredFailure.target == target
        ) {
            markInspected(target.key())
            _state.value = OfficialLinkPromptState.Failure(
                restoredFailure.copy(externalRequest = request),
            )
            return
        }

        markInspected(target.key())
        startResolution(newOperation(target = target, externalRequest = request))
    }

    fun confirmClipboard() {
        val operation = (_state.value as? OfficialLinkPromptState.ClipboardConfirmation)
            ?.operation
            ?: return
        startResolution(operation)
    }

    fun retry() {
        val operation = (_state.value as? OfficialLinkPromptState.Failure)?.operation ?: return
        startResolution(operation)
    }

    fun dismiss() {
        when (val current = _state.value) {
            is OfficialLinkPromptState.ClipboardConfirmation -> Unit
            is OfficialLinkPromptState.Failure -> current.operation
                ?.externalRequest
                ?.let(onExternalConsumed)
            OfficialLinkPromptState.Idle,
            is OfficialLinkPromptState.Resolving,
            -> return
        }
        _state.value = OfficialLinkPromptState.Idle
    }

    private fun startResolution(operation: OfficialLinkOperation) {
        if (!isAuthenticated) return
        cancelActiveResolution()
        _state.value = OfficialLinkPromptState.Resolving(operation)
        activeJob = scope.launch {
            val result = try {
                resolve(operation.target)
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Throwable) {
                Result.failure(exception)
            }
            result.exceptionOrNull()?.let { if (it is CancellationException) throw it }
            if (
                !isAuthenticated ||
                _state.value != OfficialLinkPromptState.Resolving(operation) ||
                !isOperationCurrent(operation)
            ) {
                return@launch
            }

            result.fold(
                onSuccess = { content ->
                    operation.externalRequest?.let(onExternalConsumed)
                    _state.value = OfficialLinkPromptState.Idle
                    onResolved(content)
                },
                onFailure = {
                    _state.value = OfficialLinkPromptState.Failure(operation)
                },
            )
        }
    }

    private fun discardCurrentExternalRequest() {
        when (val current = _state.value) {
            is OfficialLinkPromptState.ClipboardConfirmation -> current.operation.externalRequest
            is OfficialLinkPromptState.Resolving -> current.operation.externalRequest
            is OfficialLinkPromptState.Failure -> current.operation?.externalRequest
            OfficialLinkPromptState.Idle -> null
        }?.let(onExternalConsumed)
    }

    private fun cancelActiveResolution() {
        activeJob?.cancel()
        activeJob = null
    }

    private fun newOperation(
        target: OfficialLinkTarget,
        externalRequest: OfficialLinkRequest? = null,
    ) = OfficialLinkOperation(
        id = ++nextOperationId,
        target = target,
        externalRequest = externalRequest,
    )

    private fun markInspected(targetKey: String) {
        lastInspectedTargetKey = targetKey
    }
}

private fun OfficialLinkPromptSnapshot.restoredState(): OfficialLinkPromptState =
    when (val savedState = state) {
        is OfficialLinkPromptState.Resolving -> if (savedState.operation.externalRequest == null) {
            OfficialLinkPromptState.ClipboardConfirmation(savedState.operation)
        } else {
            OfficialLinkPromptState.Idle
        }
        else -> savedState
    }

private fun OfficialLinkTarget.key(): String = "$type:$id"
