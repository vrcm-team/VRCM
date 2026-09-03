package io.github.vrcmteam.vrcm.presentation.screens.avatar

import io.github.vrcmteam.vrcm.core.shared.AccountSessionToken
import io.github.vrcmteam.vrcm.core.shared.AuthenticatedAccount
import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.network.api.avatars.AvatarsApi
import io.github.vrcmteam.vrcm.network.api.avatars.data.AvatarData
import io.github.vrcmteam.vrcm.network.api.avatars.data.hasImpostor
import io.github.vrcmteam.vrcm.service.AuthService
import io.github.vrcmteam.vrcm.service.SessionBoundResponse
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch

internal enum class AvatarImpostorDeletionPhase {
    Idle,
    Deleting,
    Verifying,
}

internal data class AvatarImpostorDeletionUiState(
    val isAvailable: Boolean = false,
    val hasImpostor: Boolean = false,
    val phase: AvatarImpostorDeletionPhase = AvatarImpostorDeletionPhase.Idle,
    val deleteFailed: Boolean = false,
    val verificationFailed: Boolean = false,
) {
    val isBusy: Boolean
        get() = phase != AvatarImpostorDeletionPhase.Idle

    val canDelete: Boolean
        get() = isAvailable && hasImpostor && !isBusy && !verificationFailed

    val canRetryVerification: Boolean
        get() = isAvailable && hasImpostor && !isBusy && verificationFailed
}

internal sealed interface AvatarImpostorDeletionNotice {
    data object Deleted : AvatarImpostorDeletionNotice
    data object DeleteFailed : AvatarImpostorDeletionNotice
    data object VerificationFailed : AvatarImpostorDeletionNotice
}

internal interface AvatarImpostorDeletionSource {
    suspend fun delete(
        sessionToken: AccountSessionToken,
        avatarId: String,
    ): SessionBoundResponse<Unit>?

    suspend fun load(
        sessionToken: AccountSessionToken,
        avatarId: String,
    ): SessionBoundResponse<AvatarData>?
}

internal class NetworkAvatarImpostorDeletionSource(
    private val avatarsApi: AvatarsApi,
    private val authService: AuthService,
) : AvatarImpostorDeletionSource {
    override suspend fun delete(
        sessionToken: AccountSessionToken,
        avatarId: String,
    ): SessionBoundResponse<Unit>? = authService.runSessionBoundCatching(sessionToken) {
        avatarsApi.deleteImpostor(avatarId)
    }

    override suspend fun load(
        sessionToken: AccountSessionToken,
        avatarId: String,
    ): SessionBoundResponse<AvatarData>? = authService.runSessionBoundCatching(sessionToken) {
        avatarsApi.getAvatarById(avatarId)
    }
}

/** Coordinates destructive Impostor deletion and its authoritative server refresh. */
internal class AvatarImpostorDeletionStateModel(
    private val source: AvatarImpostorDeletionSource,
    private val scope: CoroutineScope,
    private val onAvatarReloaded: (AvatarData) -> Unit,
    private val requestDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val sessionFlow: StateFlow<AuthenticatedAccount?> = SharedFlowCentre.currentSession,
) {
    private data class Target(
        val avatarId: String = "",
        val authorId: String = "",
        val hasImpostor: Boolean = false,
    )

    private val target = MutableStateFlow(Target())
    private val generation = MutableStateFlow(0L)
    private var observedUserId = sessionFlow.value?.token?.userId
    private var requestJob: Job? = null

    private val _state = MutableStateFlow(AvatarImpostorDeletionUiState())
    val state: StateFlow<AvatarImpostorDeletionUiState> = _state.asStateFlow()

    private val _notices = MutableSharedFlow<AvatarImpostorDeletionNotice>(extraBufferCapacity = 1)
    val notices: SharedFlow<AvatarImpostorDeletionNotice> = _notices.asSharedFlow()

    init {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            sessionFlow.collect { session ->
                val nextUserId = session?.token?.userId
                if (nextUserId != observedUserId) {
                    observedUserId = nextUserId
                    invalidatePendingRequest()
                } else {
                    refreshAvailability(preservePhase = true)
                }
            }
        }
    }

    fun setTarget(avatarId: String, authorId: String, hasImpostor: Boolean) {
        val next = Target(avatarId, authorId, hasImpostor)
        if (target.value == next) return
        target.value = next
        invalidatePendingRequest()
    }

    fun clearTarget() {
        setTarget(avatarId = "", authorId = "", hasImpostor = false)
    }

    fun delete(): Boolean {
        val ready = _state.value
        val currentTarget = target.value
        val sessionToken = sessionFlow.value?.token ?: return false
        if (!ready.canDelete || currentTarget.authorId != sessionToken.userId) return false
        if (!_state.compareAndSet(
                ready,
                ready.copy(
                    phase = AvatarImpostorDeletionPhase.Deleting,
                    deleteFailed = false,
                    verificationFailed = false,
                )
            )
        ) {
            return false
        }

        val requestGeneration = generation.value
        requestJob = scope.launch(requestDispatcher) {
            val response = source.delete(sessionToken, currentTarget.avatarId)
            if (!acceptsTarget(currentTarget, requestGeneration)) return@launch
            if (response == null || !acceptsResponse(response.sessionToken)) {
                handleVerificationSessionMismatch(currentTarget)
                return@launch
            }
            if (response.result.isFailure) {
                _state.value = availableState().copy(deleteFailed = true)
                _notices.emit(AvatarImpostorDeletionNotice.DeleteFailed)
                return@launch
            }

            verifyAuthoritativeState(
                currentTarget = currentTarget,
                requestGeneration = requestGeneration,
                sessionToken = response.sessionToken,
            )
        }
        return true
    }

    fun retryVerification(): Boolean {
        val ready = _state.value
        val currentTarget = target.value
        val sessionToken = sessionFlow.value?.token ?: return false
        if (!ready.canRetryVerification || currentTarget.authorId != sessionToken.userId) return false
        if (!_state.compareAndSet(
                ready,
                ready.copy(
                    phase = AvatarImpostorDeletionPhase.Verifying,
                    verificationFailed = false,
                )
            )
        ) {
            return false
        }

        val requestGeneration = generation.value
        requestJob = scope.launch(requestDispatcher) {
            loadAuthoritativeState(currentTarget, requestGeneration, sessionToken)
        }
        return true
    }

    private suspend fun verifyAuthoritativeState(
        currentTarget: Target,
        requestGeneration: Long,
        sessionToken: AccountSessionToken,
    ) {
        if (!acceptsTarget(currentTarget, requestGeneration)) return
        _state.value = _state.value.copy(phase = AvatarImpostorDeletionPhase.Verifying)
        loadAuthoritativeState(currentTarget, requestGeneration, sessionToken)
    }

    private suspend fun loadAuthoritativeState(
        currentTarget: Target,
        requestGeneration: Long,
        sessionToken: AccountSessionToken,
    ) {
        val response = source.load(sessionToken, currentTarget.avatarId)
        if (!acceptsTarget(currentTarget, requestGeneration)) return
        if (response == null || !acceptsResponse(response.sessionToken)) {
            handleVerificationSessionMismatch(currentTarget)
            return
        }
        val updated = response.result.getOrElse {
            _state.value = availableState().copy(verificationFailed = true)
            _notices.emit(AvatarImpostorDeletionNotice.VerificationFailed)
            return
        }
        if (updated.id != currentTarget.avatarId) {
            _state.value = availableState().copy(verificationFailed = true)
            _notices.emit(AvatarImpostorDeletionNotice.VerificationFailed)
            return
        }

        onAvatarReloaded(updated)
        commitAuthoritativeTarget(
            avatarId = updated.id,
            authorId = updated.authorId,
            hasImpostor = updated.hasImpostor,
        )
        if (updated.hasImpostor) {
            _state.value = availableState().copy(deleteFailed = true)
            _notices.emit(AvatarImpostorDeletionNotice.DeleteFailed)
        } else {
            _notices.emit(AvatarImpostorDeletionNotice.Deleted)
        }
    }

    private suspend fun handleVerificationSessionMismatch(currentTarget: Target) {
        if (sessionFlow.value?.token?.userId == currentTarget.authorId) {
            _state.value = availableState().copy(verificationFailed = true)
            _notices.emit(AvatarImpostorDeletionNotice.VerificationFailed)
        } else {
            refreshAvailability()
        }
    }

    private fun commitAuthoritativeTarget(
        avatarId: String,
        authorId: String,
        hasImpostor: Boolean,
    ) {
        target.value = Target(avatarId, authorId, hasImpostor)
        generation.updateAndGet { it + 1L }
        requestJob = null
        _state.value = availableState()
    }

    private fun invalidatePendingRequest() {
        generation.updateAndGet { it + 1L }
        requestJob?.cancel()
        requestJob = null
        _state.value = availableState()
    }

    private fun refreshAvailability(preservePhase: Boolean = false) {
        val current = _state.value
        _state.value = availableState().copy(
            phase = if (preservePhase) current.phase else AvatarImpostorDeletionPhase.Idle,
            deleteFailed = if (preservePhase) current.deleteFailed else false,
            verificationFailed = if (preservePhase) current.verificationFailed else false,
        )
    }

    private fun availableState(): AvatarImpostorDeletionUiState {
        val currentTarget = target.value
        return AvatarImpostorDeletionUiState(
            isAvailable = currentTarget.avatarId.isNotBlank() &&
                currentTarget.authorId.isNotBlank() &&
                currentTarget.authorId == sessionFlow.value?.token?.userId,
            hasImpostor = currentTarget.hasImpostor,
        )
    }

    private fun acceptsTarget(expected: Target, requestGeneration: Long): Boolean =
        target.value == expected && generation.value == requestGeneration

    private fun acceptsResponse(responseToken: AccountSessionToken?): Boolean =
        responseToken != null && sessionFlow.value?.token == responseToken
}
