package io.github.vrcmteam.vrcm.presentation.screens.world

import io.github.vrcmteam.vrcm.core.shared.AccountSessionToken
import io.github.vrcmteam.vrcm.core.shared.AuthenticatedAccount
import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.network.api.worlds.WorldsApi
import io.github.vrcmteam.vrcm.network.api.worlds.data.WorldData
import io.github.vrcmteam.vrcm.service.AuthService
import io.github.vrcmteam.vrcm.service.SessionBoundResponse
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CancellationException
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

internal enum class WorldPublicationAction {
    Publish,
    Unpublish,
}

internal enum class WorldPublicationBlockReason {
    WeeklyLimit,
    CheckFailed,
    RefreshRequired,
}

internal data class WorldPublicationUiState(
    val action: WorldPublicationAction? = null,
    val canExecute: Boolean = false,
    val isChecking: Boolean = false,
    val isChanging: Boolean = false,
    val blockReason: WorldPublicationBlockReason? = null,
)

internal sealed interface WorldPublicationNotice {
    data class Changed(val action: WorldPublicationAction) : WorldPublicationNotice
    data class ChangeFailed(
        val action: WorldPublicationAction,
        val message: String?,
    ) : WorldPublicationNotice

    data class RefreshFailed(val message: String?) : WorldPublicationNotice

    data class CacheSyncFailed(val message: String?) : WorldPublicationNotice
}

internal fun worldPublicationAction(
    authorId: String?,
    releaseStatus: String?,
    currentUserId: String?,
): WorldPublicationAction? {
    if (authorId.isNullOrBlank() || authorId != currentUserId) return null
    return when (releaseStatus) {
        "private" -> WorldPublicationAction.Publish
        "public" -> WorldPublicationAction.Unpublish
        else -> null
    }
}

internal interface WorldPublicationSource {
    suspend fun loadWorld(
        sessionToken: AccountSessionToken,
        worldId: String,
    ): SessionBoundResponse<WorldData>?

    suspend fun canPublish(
        sessionToken: AccountSessionToken,
        worldId: String,
    ): SessionBoundResponse<Boolean>?

    suspend fun changePublication(
        sessionToken: AccountSessionToken,
        worldId: String,
        action: WorldPublicationAction,
    ): SessionBoundResponse<Unit>?
}

internal class NetworkWorldPublicationSource(
    private val worldsApi: WorldsApi,
    private val authService: AuthService,
) : WorldPublicationSource {
    override suspend fun loadWorld(
        sessionToken: AccountSessionToken,
        worldId: String,
    ): SessionBoundResponse<WorldData>? =
        authService.runSessionBoundCatching(sessionToken) {
            worldsApi.getWorldById(worldId)
        }

    override suspend fun canPublish(
        sessionToken: AccountSessionToken,
        worldId: String,
    ): SessionBoundResponse<Boolean>? {
        val response = authService.runSessionBoundCatching(sessionToken) {
            worldsApi.getWorldPublishStatus(worldId)
        } ?: return null
        return SessionBoundResponse(
            result = response.result.map { it.canPublish },
            sessionToken = response.sessionToken,
        )
    }

    override suspend fun changePublication(
        sessionToken: AccountSessionToken,
        worldId: String,
        action: WorldPublicationAction,
    ): SessionBoundResponse<Unit>? =
        authService.runSessionBoundCatching(sessionToken) {
            when (action) {
                WorldPublicationAction.Publish -> worldsApi.publishWorld(worldId)
                WorldPublicationAction.Unpublish -> worldsApi.unpublishWorld(worldId)
            }
        }
}

/**
 * Coordinates owner-only publication mutations against a specific account session and world.
 * Results from replaced sessions or targets are discarded before they can update UI state.
 */
internal class WorldPublicationStateModel(
    private val source: WorldPublicationSource,
    private val scope: CoroutineScope,
    private val requestDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val sessionFlow: StateFlow<AuthenticatedAccount?> = SharedFlowCentre.currentSession,
    private val onWorldRefreshed: suspend (WorldData) -> Result<Unit> = { Result.success(Unit) },
) {
    private data class Target(
        val worldId: String = "",
        val knownAuthorId: String? = null,
    )

    private data class VerifiedWorld(
        val worldId: String,
        val authorId: String,
        val releaseStatus: String,
        val sessionToken: AccountSessionToken,
    )

    private val target = MutableStateFlow(Target())
    private val generation = MutableStateFlow(0L)
    private var verifiedWorld: VerifiedWorld? = null
    private var observedSessionToken = sessionFlow.value?.token
    private var refreshJob: Job? = null
    private var eligibilityJob: Job? = null
    private var mutationJob: Job? = null

    private val _state = MutableStateFlow(WorldPublicationUiState())
    val state: StateFlow<WorldPublicationUiState> = _state.asStateFlow()

    private val _notices = MutableSharedFlow<WorldPublicationNotice>(extraBufferCapacity = 1)
    val notices: SharedFlow<WorldPublicationNotice> = _notices.asSharedFlow()

    init {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            sessionFlow.collect { session ->
                val nextToken = session?.token
                if (nextToken == observedSessionToken) return@collect
                observedSessionToken = nextToken
                invalidateRequests()
                if (target.value.knownAuthorId == nextToken?.userId) {
                    refreshIfOwned()
                }
            }
        }
    }

    fun setTarget(worldId: String, knownAuthorId: String?) {
        val current = target.value
        if (current.worldId == worldId) {
            if (!knownAuthorId.isNullOrBlank() && current.knownAuthorId != knownAuthorId) {
                target.value = current.copy(knownAuthorId = knownAuthorId)
            }
            return
        }

        invalidateRequests()
        target.value = Target(worldId = worldId, knownAuthorId = knownAuthorId)
    }

    fun observeKnownWorld(world: WorldData) {
        val current = target.value
        if (current.worldId != world.id) return
        target.value = current.copy(knownAuthorId = world.authorId)
    }

    fun acceptVerifiedWorld(world: WorldData, sessionToken: AccountSessionToken) {
        applyVerifiedWorld(
            world = world,
            sessionToken = sessionToken,
            requestGeneration = generation.value,
        )
    }

    fun refreshIfOwned(): Boolean {
        val currentTarget = target.value
        val sessionToken = sessionFlow.value?.token ?: return false
        if (currentTarget.worldId.isBlank() || currentTarget.knownAuthorId != sessionToken.userId) {
            return false
        }

        val requestGeneration = generation.value
        refreshJob?.cancel()
        refreshJob = scope.launch(requestDispatcher) {
            val response = source.loadWorld(sessionToken, currentTarget.worldId) ?: return@launch
            if (!accepts(currentTarget.worldId, response.sessionToken, requestGeneration)) {
                return@launch
            }
            val world = response.result.getOrNull() ?: return@launch
            if (world.id != currentTarget.worldId) return@launch
            val cacheSync = syncWorld(world)
            if (cacheSync.isFailure && accepts(world.id, response.sessionToken, requestGeneration)) {
                _notices.emit(WorldPublicationNotice.CacheSyncFailed(cacheSync.exceptionOrNull()?.message))
            }
            applyVerifiedWorld(world, response.sessionToken, requestGeneration)
        }
        return true
    }

    fun changePublication(action: WorldPublicationAction) {
        val readyState = _state.value
        val verified = verifiedWorld ?: return
        val sessionToken = sessionFlow.value?.token ?: return
        val currentTarget = target.value
        if (
            readyState.action != action || !readyState.canExecute ||
            readyState.isChecking || readyState.isChanging ||
            verified.worldId != currentTarget.worldId ||
            verified.authorId != sessionToken.userId ||
            verified.sessionToken != sessionToken
        ) {
            return
        }

        val changingState = readyState.copy(canExecute = false, isChanging = true)
        if (!_state.compareAndSet(readyState, changingState)) return

        val requestGeneration = generation.value
        mutationJob = scope.launch(requestDispatcher) {
            val mutation = source.changePublication(
                sessionToken = sessionToken,
                worldId = verified.worldId,
                action = action,
            )
            if (mutation == null) {
                if (accepts(verified.worldId, sessionToken, requestGeneration)) {
                    _state.value = readyState
                }
                return@launch
            }
            if (!accepts(verified.worldId, mutation.sessionToken, requestGeneration)) {
                return@launch
            }

            val mutationError = mutation.result.exceptionOrNull()
            if (mutationError != null) {
                _state.value = readyState
                _notices.emit(WorldPublicationNotice.ChangeFailed(action, mutationError.message))
                return@launch
            }

            val refreshed = source.loadWorld(mutation.sessionToken, verified.worldId)
            if (refreshed == null) {
                if (accepts(verified.worldId, mutation.sessionToken, requestGeneration)) {
                    _state.value = changingState.copy(
                        isChanging = false,
                        blockReason = WorldPublicationBlockReason.RefreshRequired,
                    )
                    _notices.emit(WorldPublicationNotice.RefreshFailed(null))
                }
                return@launch
            }
            if (!accepts(verified.worldId, refreshed.sessionToken, requestGeneration)) {
                return@launch
            }

            val refreshedWorld = refreshed.result.getOrElse { error ->
                _state.value = changingState.copy(
                    isChanging = false,
                    blockReason = WorldPublicationBlockReason.RefreshRequired,
                )
                _notices.emit(WorldPublicationNotice.RefreshFailed(error.message))
                return@launch
            }
            if (refreshedWorld.id != verified.worldId) {
                _state.value = changingState.copy(
                    isChanging = false,
                    blockReason = WorldPublicationBlockReason.RefreshRequired,
                )
                _notices.emit(WorldPublicationNotice.RefreshFailed(null))
                return@launch
            }

            val expectedStatus = when (action) {
                WorldPublicationAction.Publish -> "public"
                WorldPublicationAction.Unpublish -> "private"
            }
            val cacheSync = syncWorld(refreshedWorld)
            if (cacheSync.isFailure) {
                applyVerifiedWorld(
                    world = refreshedWorld,
                    sessionToken = refreshed.sessionToken,
                    requestGeneration = requestGeneration,
                    forcedBlockReason = WorldPublicationBlockReason.RefreshRequired,
                )
                _notices.emit(WorldPublicationNotice.CacheSyncFailed(cacheSync.exceptionOrNull()?.message))
                return@launch
            }
            if (refreshedWorld.releaseStatus != expectedStatus) {
                applyVerifiedWorld(
                    world = refreshedWorld,
                    sessionToken = refreshed.sessionToken,
                    requestGeneration = requestGeneration,
                    forcedBlockReason = WorldPublicationBlockReason.RefreshRequired,
                )
                _notices.emit(WorldPublicationNotice.RefreshFailed(null))
                return@launch
            }

            applyVerifiedWorld(refreshedWorld, refreshed.sessionToken, requestGeneration)
            _notices.emit(WorldPublicationNotice.Changed(action))
        }
    }

    private fun applyVerifiedWorld(
        world: WorldData,
        sessionToken: AccountSessionToken,
        requestGeneration: Long,
        forcedBlockReason: WorldPublicationBlockReason? = null,
    ) {
        if (!accepts(world.id, sessionToken, requestGeneration)) return

        val currentTarget = target.value
        target.value = currentTarget.copy(knownAuthorId = world.authorId)
        verifiedWorld = VerifiedWorld(
            worldId = world.id,
            authorId = world.authorId,
            releaseStatus = world.releaseStatus,
            sessionToken = sessionToken,
        )
        eligibilityJob?.cancel()

        when (val action = worldPublicationAction(
            authorId = world.authorId,
            releaseStatus = world.releaseStatus,
            currentUserId = sessionToken.userId,
        )) {
            null -> _state.value = WorldPublicationUiState()
            WorldPublicationAction.Unpublish -> {
                _state.value = WorldPublicationUiState(
                    action = action,
                    canExecute = forcedBlockReason == null,
                    blockReason = forcedBlockReason,
                )
            }
            WorldPublicationAction.Publish -> {
                if (forcedBlockReason == null) {
                    _state.value = WorldPublicationUiState(
                        action = action,
                        isChecking = true,
                    )
                    checkPublishAvailability(world.id, sessionToken, requestGeneration)
                } else {
                    _state.value = WorldPublicationUiState(
                        action = action,
                        blockReason = forcedBlockReason,
                    )
                }
            }
        }
    }

    private fun checkPublishAvailability(
        worldId: String,
        sessionToken: AccountSessionToken,
        requestGeneration: Long,
    ) {
        eligibilityJob = scope.launch(requestDispatcher) {
            val response = source.canPublish(sessionToken, worldId)
            if (response == null) {
                if (accepts(worldId, sessionToken, requestGeneration)) {
                    _state.value = WorldPublicationUiState(
                        action = WorldPublicationAction.Publish,
                        blockReason = WorldPublicationBlockReason.CheckFailed,
                    )
                }
                return@launch
            }
            if (!accepts(worldId, response.sessionToken, requestGeneration) ||
                verifiedWorld?.releaseStatus != "private"
            ) {
                return@launch
            }

            response.result
                .onSuccess { canPublish ->
                    _state.value = WorldPublicationUiState(
                        action = WorldPublicationAction.Publish,
                        canExecute = canPublish,
                        blockReason = if (canPublish) {
                            null
                        } else {
                            WorldPublicationBlockReason.WeeklyLimit
                        },
                    )
                }
                .onFailure {
                    _state.value = WorldPublicationUiState(
                        action = WorldPublicationAction.Publish,
                        blockReason = WorldPublicationBlockReason.CheckFailed,
                    )
                }
        }
    }

    private fun invalidateRequests() {
        generation.updateAndGet { it + 1 }
        refreshJob?.cancel()
        eligibilityJob?.cancel()
        mutationJob?.cancel()
        verifiedWorld = null
        _state.value = WorldPublicationUiState()
    }

    private suspend fun syncWorld(world: WorldData): Result<Unit> = try {
        onWorldRefreshed(world)
    } catch (error: CancellationException) {
        throw error
    } catch (error: Throwable) {
        Result.failure(error)
    }

    private fun accepts(
        worldId: String,
        sessionToken: AccountSessionToken,
        requestGeneration: Long,
    ): Boolean = generation.value == requestGeneration &&
        target.value.worldId == worldId &&
        sessionFlow.value?.token == sessionToken
}
