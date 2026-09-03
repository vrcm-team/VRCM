package io.github.vrcmteam.vrcm.presentation.screens.world

import io.github.vrcmteam.vrcm.core.shared.AccountSessionToken
import io.github.vrcmteam.vrcm.core.shared.AuthenticatedAccount
import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.network.api.worlds.WorldsApi
import io.github.vrcmteam.vrcm.service.AuthService
import io.github.vrcmteam.vrcm.service.SessionBoundResponse
import kotlinx.coroutines.CancellationException
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

internal data class WorldDeletionUiState(
    val isAvailable: Boolean = false,
    val isDeleting: Boolean = false,
    val isDeleted: Boolean = false,
) {
    val canDelete: Boolean
        get() = isAvailable && !isDeleting && !isDeleted
}

internal sealed interface WorldDeletionNotice {
    data class Deleted(val cacheCleanupFailed: Boolean = false) : WorldDeletionNotice
    data object Failed : WorldDeletionNotice
}

internal fun canDeleteWorld(
    worldId: String,
    authorId: String?,
    currentUserId: String?,
): Boolean = worldId.isNotBlank() &&
    !authorId.isNullOrBlank() &&
    authorId == currentUserId

internal fun interface WorldDeletionSource {
    suspend fun delete(
        sessionToken: AccountSessionToken,
        worldId: String,
    ): SessionBoundResponse<Unit>?
}

internal class NetworkWorldDeletionSource(
    private val worldsApi: WorldsApi,
    private val authService: AuthService,
) : WorldDeletionSource {
    override suspend fun delete(
        sessionToken: AccountSessionToken,
        worldId: String,
    ): SessionBoundResponse<Unit>? = authService.runSessionBoundCatching(sessionToken) {
        worldsApi.deleteWorld(worldId)
    }
}

/**
 * Coordinates one irreversible deletion against a specific world and account owner.
 * Only the exact session token returned by the authenticated request may commit the result.
 */
internal class WorldDeletionStateModel(
    private val source: WorldDeletionSource,
    private val scope: CoroutineScope,
    private val removeCachedWorld: suspend (String) -> Unit,
    private val requestDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val sessionFlow: StateFlow<AuthenticatedAccount?> = SharedFlowCentre.currentSession,
) {
    private data class Target(
        val worldId: String = "",
        val authorId: String? = null,
    )

    private val target = MutableStateFlow(Target())
    private val generation = MutableStateFlow(0L)
    private var observedUserId = sessionFlow.value?.token?.userId
    private var deletionJob: Job? = null

    private val _state = MutableStateFlow(WorldDeletionUiState())
    val state: StateFlow<WorldDeletionUiState> = _state.asStateFlow()

    private val _notices = MutableSharedFlow<WorldDeletionNotice>(extraBufferCapacity = 1)
    val notices: SharedFlow<WorldDeletionNotice> = _notices.asSharedFlow()

    init {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            sessionFlow.collect { session ->
                val nextUserId = session?.token?.userId
                if (nextUserId != observedUserId) {
                    observedUserId = nextUserId
                    invalidatePendingDeletion(preserveDeleted = true)
                } else {
                    refreshAvailability(preserveDeletion = true)
                }
            }
        }
    }

    fun setTarget(worldId: String, authorId: String?) {
        val next = Target(worldId, authorId)
        if (target.value == next) return
        target.value = next
        invalidatePendingDeletion()
    }

    fun delete(): Boolean {
        val ready = _state.value
        val currentTarget = target.value
        val sessionToken = sessionFlow.value?.token ?: return false
        if (!ready.canDelete ||
            currentTarget.authorId != sessionToken.userId ||
            !canDeleteWorld(currentTarget.worldId, currentTarget.authorId, sessionToken.userId)
        ) {
            return false
        }
        if (!_state.compareAndSet(ready, ready.copy(isDeleting = true))) return false

        val requestGeneration = generation.value
        deletionJob = scope.launch(requestDispatcher) {
            val response = source.delete(sessionToken, currentTarget.worldId)
            if (!acceptsTarget(currentTarget, requestGeneration)) return@launch

            val currentSessionToken = sessionFlow.value?.token
            if (response == null || response.sessionToken != currentSessionToken) {
                refreshAvailability()
                return@launch
            }

            val error = response.result.exceptionOrNull()
            if (error != null) {
                refreshAvailability()
                _notices.emit(WorldDeletionNotice.Failed)
                return@launch
            }

            if (!acceptsResponse(currentTarget, requestGeneration, response.sessionToken)) return@launch
            _state.value = WorldDeletionUiState(isDeleted = true)
            val cacheCleanupFailed = try {
                removeCachedWorld(currentTarget.worldId)
                false
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                true
            }

            val mayNotify = acceptsDeletedCommit(
                expected = currentTarget,
                requestGeneration = requestGeneration,
                ownerUserId = response.sessionToken.userId,
            )
            if (!mayNotify) return@launch
            _notices.emit(WorldDeletionNotice.Deleted(cacheCleanupFailed))
        }
        return true
    }

    private fun invalidatePendingDeletion(preserveDeleted: Boolean = false) {
        val deletionCommitted = _state.value.isDeleted
        val wasDeleted = preserveDeleted && deletionCommitted
        generation.updateAndGet { it + 1L }
        if (!deletionCommitted) deletionJob?.cancel()
        deletionJob = null
        _state.value = if (wasDeleted) {
            WorldDeletionUiState(isDeleted = true)
        } else {
            availableState()
        }
    }

    private fun refreshAvailability(preserveDeletion: Boolean = false) {
        val current = _state.value
        _state.value = if (current.isDeleted) {
            WorldDeletionUiState(isDeleted = true)
        } else {
            availableState().copy(isDeleting = preserveDeletion && current.isDeleting)
        }
    }

    private fun availableState(): WorldDeletionUiState {
        val currentTarget = target.value
        return WorldDeletionUiState(
            isAvailable = canDeleteWorld(
                worldId = currentTarget.worldId,
                authorId = currentTarget.authorId,
                currentUserId = sessionFlow.value?.token?.userId,
            )
        )
    }

    private fun acceptsTarget(expected: Target, requestGeneration: Long): Boolean =
        target.value == expected && generation.value == requestGeneration

    private fun acceptsResponse(
        expected: Target,
        requestGeneration: Long,
        responseToken: AccountSessionToken,
    ): Boolean = acceptsTarget(expected, requestGeneration) &&
        sessionFlow.value?.token == responseToken

    private fun acceptsDeletedCommit(
        expected: Target,
        requestGeneration: Long,
        ownerUserId: String,
    ): Boolean = acceptsTarget(expected, requestGeneration) &&
        sessionFlow.value?.token?.userId == ownerUserId &&
        _state.value.isDeleted
}
