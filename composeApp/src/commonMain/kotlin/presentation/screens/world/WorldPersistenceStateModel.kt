package io.github.vrcmteam.vrcm.presentation.screens.world

import io.github.vrcmteam.vrcm.core.shared.AccountSessionToken
import io.github.vrcmteam.vrcm.core.shared.AuthenticatedAccount
import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.network.api.users.UsersApi
import io.github.vrcmteam.vrcm.service.AuthService
import io.github.vrcmteam.vrcm.service.SessionBoundResponse
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

internal sealed interface WorldPersistenceStatus {
    data object Initial : WorldPersistenceStatus
    data object Checking : WorldPersistenceStatus
    data object Exists : WorldPersistenceStatus
    data class Missing(val deleted: Boolean = false) : WorldPersistenceStatus
    data object CheckFailed : WorldPersistenceStatus
    data object Deleting : WorldPersistenceStatus
    data object DeleteFailed : WorldPersistenceStatus
}

internal data class WorldPersistenceUiState(
    val status: WorldPersistenceStatus = WorldPersistenceStatus.Initial,
    val confirmingDeletion: Boolean = false,
)

internal interface WorldPersistenceRequest {
    suspend fun exists(
        sessionToken: AccountSessionToken,
        worldId: String,
    ): SessionBoundResponse<Boolean>?

    suspend fun delete(
        sessionToken: AccountSessionToken,
        worldId: String,
    ): SessionBoundResponse<Unit>?
}

internal class NetworkWorldPersistenceRequest(
    private val authService: AuthService,
    private val usersApi: UsersApi,
) : WorldPersistenceRequest {
    override suspend fun exists(
        sessionToken: AccountSessionToken,
        worldId: String,
    ): SessionBoundResponse<Boolean>? =
        authService.runSessionBoundCatching(sessionToken) {
            usersApi.hasWorldPersistence(sessionToken.userId, worldId)
        }

    override suspend fun delete(
        sessionToken: AccountSessionToken,
        worldId: String,
    ): SessionBoundResponse<Unit>? =
        authService.runSessionBoundCatching(sessionToken) {
            usersApi.deleteWorldPersistence(sessionToken.userId, worldId)
        }
}

internal class WorldPersistenceStateModel(
    private val request: WorldPersistenceRequest,
    private val scope: CoroutineScope,
    private val requestDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val sessionFlow: StateFlow<AuthenticatedAccount?> = SharedFlowCentre.currentSession,
    private val isCurrentSession: (AccountSessionToken) -> Boolean = SharedFlowCentre::isCurrentSession,
) {
    private val _state = MutableStateFlow(WorldPersistenceUiState())
    val state: StateFlow<WorldPersistenceUiState> = _state.asStateFlow()

    private var worldId: String = ""
    private var requestGeneration = 0L
    private var observedSessionToken = sessionFlow.value?.token
    private var stateOwnerUserId: String? = null

    init {
        scope.launch {
            sessionFlow.collect { session ->
                val nextToken = session?.token
                val previousToken = observedSessionToken
                if (nextToken == previousToken) return@collect
                observedSessionToken = nextToken
                if (previousToken?.userId != nextToken?.userId) {
                    requestGeneration++
                    resetState()
                }
            }
        }
    }

    fun bindWorld(newWorldId: String) {
        if (worldId == newWorldId) return
        worldId = newWorldId
        requestGeneration++
        resetState()
    }

    fun check() {
        if (_state.value.status.isBusy()) return
        val targetWorldId = worldId.takeIf(String::isNotBlank) ?: return
        val sessionToken = sessionFlow.value?.token
        if (sessionToken == null) {
            stateOwnerUserId = null
            _state.value = WorldPersistenceUiState(WorldPersistenceStatus.CheckFailed)
            return
        }
        val generation = ++requestGeneration
        stateOwnerUserId = null
        _state.value = WorldPersistenceUiState(WorldPersistenceStatus.Checking)
        scope.launch(requestDispatcher) {
            val response = request.exists(sessionToken, targetWorldId)
            if (!accepts(targetWorldId, generation)) return@launch
            if (response == null || !isCurrentSession(response.sessionToken)) {
                resetState()
                return@launch
            }
            _state.value = response.result.fold(
                onSuccess = { exists ->
                    stateOwnerUserId = response.sessionToken.userId
                    WorldPersistenceUiState(
                        if (exists) WorldPersistenceStatus.Exists else WorldPersistenceStatus.Missing()
                    )
                },
                onFailure = {
                    stateOwnerUserId = null
                    WorldPersistenceUiState(WorldPersistenceStatus.CheckFailed)
                },
            )
        }
    }

    fun requestDeletion() {
        val current = _state.value
        if (current.status != WorldPersistenceStatus.Exists &&
            current.status != WorldPersistenceStatus.DeleteFailed
        ) {
            return
        }
        if (stateOwnerUserId != sessionFlow.value?.token?.userId) {
            resetState()
            return
        }
        _state.value = current.copy(confirmingDeletion = true)
    }

    fun dismissDeletionConfirmation() {
        _state.value = _state.value.copy(confirmingDeletion = false)
    }

    fun confirmDeletion() {
        val current = _state.value
        if (!current.confirmingDeletion ||
            current.status != WorldPersistenceStatus.Exists &&
            current.status != WorldPersistenceStatus.DeleteFailed
        ) {
            return
        }
        if (stateOwnerUserId != sessionFlow.value?.token?.userId) {
            resetState()
            return
        }
        val targetWorldId = worldId.takeIf(String::isNotBlank) ?: return
        val sessionToken = sessionFlow.value?.token
        if (sessionToken == null) {
            stateOwnerUserId = null
            _state.value = WorldPersistenceUiState(WorldPersistenceStatus.DeleteFailed)
            return
        }
        val generation = ++requestGeneration
        stateOwnerUserId = sessionToken.userId
        _state.value = WorldPersistenceUiState(WorldPersistenceStatus.Deleting)
        scope.launch(requestDispatcher) {
            val response = request.delete(sessionToken, targetWorldId)
            if (!accepts(targetWorldId, generation)) return@launch
            if (response == null || !isCurrentSession(response.sessionToken)) {
                resetState()
                return@launch
            }
            _state.value = response.result.fold(
                onSuccess = {
                    stateOwnerUserId = response.sessionToken.userId
                    WorldPersistenceUiState(WorldPersistenceStatus.Missing(deleted = true))
                },
                onFailure = {
                    stateOwnerUserId = response.sessionToken.userId
                    WorldPersistenceUiState(WorldPersistenceStatus.DeleteFailed)
                },
            )
        }
    }

    private fun resetState() {
        stateOwnerUserId = null
        _state.value = WorldPersistenceUiState()
    }

    private fun accepts(expectedWorldId: String, expectedGeneration: Long): Boolean =
        worldId == expectedWorldId && requestGeneration == expectedGeneration
}

private fun WorldPersistenceStatus.isBusy(): Boolean =
    this == WorldPersistenceStatus.Checking || this == WorldPersistenceStatus.Deleting
