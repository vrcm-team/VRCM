package io.github.vrcmteam.vrcm.presentation.screens.inventory

import io.github.vrcmteam.vrcm.network.supports.VRCApiException
import io.ktor.http.HttpStatusCode
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal sealed interface CreditsBalanceState {
    data object Loading : CreditsBalanceState
    data class Available(val balance: Long) : CreditsBalanceState
    data object Unavailable : CreditsBalanceState
    data object Error : CreditsBalanceState
}

internal fun creditsBalanceFailureState(error: Throwable): CreditsBalanceState =
    if (error is VRCApiException &&
        (error.code == HttpStatusCode.Forbidden.value || error.code == HttpStatusCode.NotFound.value)
    ) {
        CreditsBalanceState.Unavailable
    } else {
        CreditsBalanceState.Error
    }

internal class CreditsBalanceStateMachine {
    private val lock = SynchronizedObject()
    private val _state = MutableStateFlow<CreditsBalanceState>(CreditsBalanceState.Loading)
    val state: StateFlow<CreditsBalanceState> = _state.asStateFlow()
    private var nextRequestId = 0L
    private var activeRequestId: Long? = null

    fun tryStart(): Long? = synchronized(lock) {
        if (activeRequestId != null) return@synchronized null
        (++nextRequestId).also { requestId ->
            activeRequestId = requestId
            _state.value = CreditsBalanceState.Loading
        }
    }

    fun complete(requestId: Long, balance: Long): Boolean =
        finish(requestId, CreditsBalanceState.Available(balance))

    fun fail(requestId: Long, error: Throwable): Boolean =
        finish(requestId, creditsBalanceFailureState(error))

    fun failDropped(requestId: Long): Boolean =
        finish(requestId, CreditsBalanceState.Error)

    fun invalidate() = synchronized(lock) {
        nextRequestId++
        activeRequestId = null
        _state.value = CreditsBalanceState.Unavailable
    }

    private fun finish(requestId: Long, result: CreditsBalanceState): Boolean = synchronized(lock) {
        if (activeRequestId != requestId) return@synchronized false
        activeRequestId = null
        _state.value = result
        true
    }
}
