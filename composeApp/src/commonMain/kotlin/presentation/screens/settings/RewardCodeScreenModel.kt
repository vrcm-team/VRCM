package io.github.vrcmteam.vrcm.presentation.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.vrcmteam.vrcm.core.shared.AccountSessionToken
import io.github.vrcmteam.vrcm.core.shared.AuthenticatedAccount
import io.github.vrcmteam.vrcm.network.api.inventory.data.RewardRedemption
import io.github.vrcmteam.vrcm.service.AuthenticatedRewardRedemptionResponse
import io.github.vrcmteam.vrcm.service.RewardCodeRedeemer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal enum class RewardCodeFailure {
    EmptyCode,
    RequestFailed,
    SessionUnavailable,
}

internal data class RewardCodeUiState(
    val sessionToken: AccountSessionToken? = null,
    val code: String = "",
    val isSubmitting: Boolean = false,
    val rewards: List<RewardRedemption>? = null,
    val failure: RewardCodeFailure? = null,
)

internal class RewardCodeScreenModel(
    private val redeemer: RewardCodeRedeemer,
    private val sessions: StateFlow<AuthenticatedAccount?>,
    private val requestDispatcher: CoroutineDispatcher,
) : ViewModel() {
    private val _state = MutableStateFlow(
        RewardCodeUiState(sessionToken = sessions.value?.token),
    )
    val state = _state.asStateFlow()

    private var submissionJob: Job? = null
    private var submissionGeneration = 0L

    init {
        viewModelScope.launch {
            sessions.collect { session ->
                val nextToken = session?.token
                val current = _state.value
                if (current.sessionToken == nextToken) return@collect

                val accountChanged = current.sessionToken?.userId != nextToken?.userId
                if (accountChanged || nextToken == null) {
                    submissionJob?.cancel()
                    _state.value = RewardCodeUiState(sessionToken = nextToken)
                } else {
                    _state.value = current.copy(sessionToken = nextToken)
                }
            }
        }
    }

    fun updateCode(code: String) {
        _state.update { current ->
            if (current.isSubmitting || current.sessionToken == null) {
                current
            } else {
                current.copy(code = code, failure = null)
            }
        }
    }

    fun submit() {
        val current = _state.value
        if (current.isSubmitting) return

        val token = sessions.value?.token
        if (token == null || current.sessionToken != token) {
            _state.value = current.copy(failure = RewardCodeFailure.SessionUnavailable)
            return
        }
        val code = current.code.trim()
        if (code.isEmpty()) {
            _state.value = current.copy(failure = RewardCodeFailure.EmptyCode)
            return
        }

        val requestGeneration = ++submissionGeneration
        _state.value = current.copy(
            isSubmitting = true,
            rewards = null,
            failure = null,
        )
        submissionJob = viewModelScope.launch {
            try {
                val response = withContext(requestDispatcher) {
                    try {
                        redeemer.redeem(token, code)
                    } catch (cancellation: CancellationException) {
                        throw cancellation
                    } catch (error: Throwable) {
                        AuthenticatedRewardRedemptionResponse(
                            result = Result.failure(error),
                            sessionToken = token,
                        )
                    }
                }
                applyResponse(response)
            } finally {
                if (submissionGeneration == requestGeneration) {
                    _state.update { state ->
                        if (state.isSubmitting) state.copy(isSubmitting = false) else state
                    }
                }
            }
        }
    }

    private fun applyResponse(response: AuthenticatedRewardRedemptionResponse?) {
        response ?: return
        if (sessions.value?.token != response.sessionToken) return

        _state.update { current ->
            if (sessions.value?.token != response.sessionToken) return@update current
            response.result.fold(
                onSuccess = { redemptions ->
                    current.copy(
                        sessionToken = response.sessionToken,
                        code = "",
                        isSubmitting = false,
                        rewards = redemptions.flatMap { it.redeemedRewards },
                        failure = null,
                    )
                },
                onFailure = {
                    current.copy(
                        sessionToken = response.sessionToken,
                        isSubmitting = false,
                        rewards = null,
                        failure = RewardCodeFailure.RequestFailed,
                    )
                },
            )
        }
    }
}
