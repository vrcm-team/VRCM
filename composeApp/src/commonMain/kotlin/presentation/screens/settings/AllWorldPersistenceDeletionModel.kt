package io.github.vrcmteam.vrcm.presentation.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.vrcmteam.vrcm.core.shared.AccountSessionToken
import io.github.vrcmteam.vrcm.core.shared.AuthenticatedAccount
import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.network.api.users.UsersApi
import io.github.vrcmteam.vrcm.service.AuthService
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal sealed interface AllWorldPersistenceDeletionState {
    data object Unavailable : AllWorldPersistenceDeletionState
    data object Ready : AllWorldPersistenceDeletionState
    data object Deleting : AllWorldPersistenceDeletionState
    data object Deleted : AllWorldPersistenceDeletionState
    data class Failed(val error: Throwable) : AllWorldPersistenceDeletionState
}

internal class AllWorldPersistenceDeletionModel(
    private val authService: AuthService,
    private val usersApi: UsersApi,
    private val sessionFlow: StateFlow<AuthenticatedAccount?> = SharedFlowCentre.currentSession,
    private val requestDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    private val _state = MutableStateFlow(initialState(sessionFlow.value?.token))
    val state: StateFlow<AllWorldPersistenceDeletionState> = _state.asStateFlow()

    private var observedSessionToken = sessionFlow.value?.token
    private var requestRevision = 0L
    private var deletionJob: Job? = null

    init {
        viewModelScope.launch {
            sessionFlow.collect { session ->
                val nextToken = session?.token
                val previousToken = observedSessionToken
                if (previousToken == nextToken) return@collect
                observedSessionToken = nextToken

                val accountChanged = previousToken?.userId != nextToken?.userId
                if (accountChanged || nextToken == null) {
                    requestRevision++
                    deletionJob?.cancel()
                    deletionJob = null
                    _state.value = initialState(nextToken)
                } else if (_state.value !is AllWorldPersistenceDeletionState.Deleting &&
                    _state.value !is AllWorldPersistenceDeletionState.Deleted
                ) {
                    requestRevision++
                    _state.value = AllWorldPersistenceDeletionState.Ready
                }
                // Deleting may receive its own 401 renewal, while Deleted is irreversible for
                // this account. The exact response-token check still rejects external churn.
            }
        }
    }

    fun deleteAllWorldSaveData() {
        if (_state.value !is AllWorldPersistenceDeletionState.Ready &&
            _state.value !is AllWorldPersistenceDeletionState.Failed
        ) {
            return
        }
        val requestToken = sessionFlow.value?.token ?: run {
            _state.value = AllWorldPersistenceDeletionState.Unavailable
            return
        }
        val requestUserId = requestToken.userId
        val revision = ++requestRevision
        _state.value = AllWorldPersistenceDeletionState.Deleting

        deletionJob = viewModelScope.launch {
            val response = withContext(requestDispatcher) {
                authService.runSessionBoundCatching(requestToken) {
                    usersApi.deleteAllWorldPersistence(requestUserId)
                }
            }
            if (revision != requestRevision) return@launch

            val currentToken = sessionFlow.value?.token
            val acceptedResponse = response?.takeIf {
                it.sessionToken == currentToken && it.sessionToken.userId == requestUserId
            }
            if (acceptedResponse == null) {
                _state.value = initialState(currentToken)
                return@launch
            }
            acceptedResponse.result.fold(
                onSuccess = { _state.value = AllWorldPersistenceDeletionState.Deleted },
                onFailure = { error ->
                    _state.value = AllWorldPersistenceDeletionState.Failed(error)
                },
            )
        }
    }

    private companion object {
        fun initialState(token: AccountSessionToken?): AllWorldPersistenceDeletionState =
            if (token == null) {
                AllWorldPersistenceDeletionState.Unavailable
            } else {
                AllWorldPersistenceDeletionState.Ready
            }
    }
}
