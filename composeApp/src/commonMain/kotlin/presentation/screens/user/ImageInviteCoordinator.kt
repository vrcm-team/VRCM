package io.github.vrcmteam.vrcm.presentation.screens.user

import io.github.vrcmteam.vrcm.core.shared.AccountSessionToken
import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.presentation.screens.gallery.GallerySelection
import io.github.vrcmteam.vrcm.presentation.screens.gallery.GallerySelectionSessionStore
import io.github.vrcmteam.vrcm.service.ImageInviteRemote
import io.github.vrcmteam.vrcm.service.ImageInviteRemoteResult
import io.github.vrcmteam.vrcm.service.PreparedImageInvite
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal enum class ImageInviteFailureStage {
    Preparation,
    Sending,
}

internal sealed interface ImageInviteUiState {
    data object Idle : ImageInviteUiState
    data class Selecting(val selectionSessionId: String) : ImageInviteUiState
    data class Preparing(val selection: GallerySelection) : ImageInviteUiState
    data class Ready(val selection: GallerySelection) : ImageInviteUiState
    data class Sending(val selection: GallerySelection) : ImageInviteUiState
    data class Failed(
        val selection: GallerySelection,
        val stage: ImageInviteFailureStage,
        val error: Throwable,
    ) : ImageInviteUiState
    data class Sent(val selection: GallerySelection) : ImageInviteUiState
    data object SessionChanged : ImageInviteUiState
}

/** Owns one image-invite attempt and rejects stale Gallery or network completions. */
internal class ImageInviteCoordinator(
    private val gallerySessions: GallerySelectionSessionStore,
    private val remote: ImageInviteRemote,
    private val currentSessionToken: () -> AccountSessionToken? = {
        SharedFlowCentre.currentSession.value?.token
    },
) {
    private data class ActiveAttempt(
        val generation: Long,
        val userId: String,
        val sessionToken: AccountSessionToken,
        val selectionSessionId: String,
        val selection: GallerySelection? = null,
        val prepared: PreparedImageInvite? = null,
    )

    private val lock = SynchronizedObject()
    private var generation = 0L
    private var active: ActiveAttempt? = null
    private val _state = MutableStateFlow<ImageInviteUiState>(ImageInviteUiState.Idle)
    val state: StateFlow<ImageInviteUiState> = _state.asStateFlow()

    fun beginSelection(userId: String): String? = synchronized(lock) {
        if (_state.value is ImageInviteUiState.Preparing ||
            _state.value is ImageInviteUiState.Sending
        ) return@synchronized null
        val token = currentSessionToken() ?: return@synchronized null
        val normalizedUserId = userId.trim().takeIf(String::isNotEmpty) ?: return@synchronized null
        active?.selectionSessionId?.let(gallerySessions::cancel)
        val selectionSessionId = gallerySessions.create()
        active = ActiveAttempt(
            generation = ++generation,
            userId = normalizedUserId,
            sessionToken = token,
            selectionSessionId = selectionSessionId,
        )
        _state.value = ImageInviteUiState.Selecting(selectionSessionId)
        selectionSessionId
    }

    fun isSelectionPending(selectionSessionId: String): Boolean =
        gallerySessions.isPending(selectionSessionId)

    suspend fun finishSelection(selectionSessionId: String) {
        val operation = synchronized(lock) {
            val attempt = active?.takeIf { it.selectionSessionId == selectionSessionId }
                ?: return@synchronized null
            if (currentSessionToken() != attempt.sessionToken) {
                clearLocked(ImageInviteUiState.SessionChanged)
                return@synchronized null
            }
            val selection = gallerySessions.consume(selectionSessionId)
            if (selection == null) {
                if (!gallerySessions.isPending(selectionSessionId)) clearLocked(ImageInviteUiState.Idle)
                return@synchronized null
            }
            attempt.copy(selection = selection).also {
                active = it
                _state.value = ImageInviteUiState.Preparing(selection)
            }
        } ?: return
        completePreparation(operation, remote.prepare(operation.selection!!, operation.sessionToken))
    }

    suspend fun retryPreparation() {
        val operation = synchronized(lock) {
            val failed = _state.value as? ImageInviteUiState.Failed
                ?: return@synchronized null
            if (failed.stage != ImageInviteFailureStage.Preparation) return@synchronized null
            val attempt = active?.takeIf { it.selection == failed.selection }
                ?: return@synchronized null
            if (currentSessionToken() != attempt.sessionToken) {
                clearLocked(ImageInviteUiState.SessionChanged)
                return@synchronized null
            }
            _state.value = ImageInviteUiState.Preparing(failed.selection)
            attempt
        } ?: return
        completePreparation(operation, remote.prepare(operation.selection!!, operation.sessionToken))
    }

    suspend fun send() {
        val operation = synchronized(lock) {
            val attempt = active ?: return@synchronized null
            val prepared = attempt.prepared ?: return@synchronized null
            val retryable = _state.value is ImageInviteUiState.Ready ||
                    (_state.value as? ImageInviteUiState.Failed)?.stage == ImageInviteFailureStage.Sending
            if (!retryable) return@synchronized null
            if (currentSessionToken() != attempt.sessionToken) {
                clearLocked(ImageInviteUiState.SessionChanged)
                return@synchronized null
            }
            _state.value = ImageInviteUiState.Sending(prepared.selection)
            attempt
        } ?: return
        val result = remote.send(operation.userId, operation.prepared!!, operation.sessionToken)
        synchronized(lock) {
            if (active?.generation != operation.generation) return@synchronized
            applyRemoteResult(
                result = result,
                onSuccess = { token ->
                    active = operation.copy(sessionToken = token)
                    _state.value = ImageInviteUiState.Sent(operation.prepared.selection)
                },
                onFailure = { error ->
                    _state.value = ImageInviteUiState.Failed(
                        selection = operation.prepared.selection,
                        stage = ImageInviteFailureStage.Sending,
                        error = error,
                    )
                },
            )
        }
    }

    fun dismiss() = synchronized(lock) {
        if (_state.value is ImageInviteUiState.Preparing ||
            _state.value is ImageInviteUiState.Sending
        ) return@synchronized
        clearLocked(ImageInviteUiState.Idle)
    }

    private fun completePreparation(
        operation: ActiveAttempt,
        result: ImageInviteRemoteResult<PreparedImageInvite>,
    ) = synchronized(lock) {
        if (active?.generation != operation.generation) return@synchronized
        applyRemoteResult(
            result = result,
            onSuccess = { token, image ->
                active = operation.copy(sessionToken = token, prepared = image)
                _state.value = ImageInviteUiState.Ready(image.selection)
            },
            onFailure = { error ->
                _state.value = ImageInviteUiState.Failed(
                    selection = operation.selection!!,
                    stage = ImageInviteFailureStage.Preparation,
                    error = error,
                )
            },
        )
    }

    private fun <T> applyRemoteResult(
        result: ImageInviteRemoteResult<T>,
        onSuccess: (AccountSessionToken, T) -> Unit,
        onFailure: (Throwable) -> Unit,
    ) {
        when (result) {
            is ImageInviteRemoteResult.Success -> {
                if (currentSessionToken() == result.sessionToken) {
                    onSuccess(result.sessionToken, result.value)
                } else {
                    clearLocked(ImageInviteUiState.SessionChanged)
                }
            }
            is ImageInviteRemoteResult.Failure -> {
                if (currentSessionToken() == result.sessionToken) {
                    active = active?.copy(sessionToken = result.sessionToken)
                    onFailure(result.error)
                } else {
                    clearLocked(ImageInviteUiState.SessionChanged)
                }
            }
            ImageInviteRemoteResult.SessionChanged -> clearLocked(ImageInviteUiState.SessionChanged)
        }
    }

    private fun <T> applyRemoteResult(
        result: ImageInviteRemoteResult<T>,
        onSuccess: (AccountSessionToken) -> Unit,
        onFailure: (Throwable) -> Unit,
    ) = applyRemoteResult(result, { token, _ -> onSuccess(token) }, onFailure)

    private fun clearLocked(nextState: ImageInviteUiState) {
        active?.selectionSessionId?.let(gallerySessions::cancel)
        generation++
        active = null
        _state.value = nextState
    }
}
