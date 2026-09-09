package io.github.vrcmteam.vrcm.core.shared

import io.github.vrcmteam.vrcm.network.websocket.data.WebSocketEvent
import io.github.vrcmteam.vrcm.presentation.compoments.ToastText
import io.github.vrcmteam.vrcm.service.data.AccountDto
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

data class AccountSessionToken(
    val userId: String,
    val generation: Long,
)

data class AuthenticatedAccount(
    val account: AccountDto,
    val token: AccountSessionToken,
)

data class AccountWebSocketEvent(
    val token: AccountSessionToken,
    val event: WebSocketEvent,
)

internal class AuthenticationSessionRegistry {
    private val lock = SynchronizedObject()
    private var generation = 0L
    private val _currentSession = MutableStateFlow<AuthenticatedAccount?>(null)
    val currentSession: StateFlow<AuthenticatedAccount?> = _currentSession.asStateFlow()

    fun authenticate(account: AccountDto): AuthenticatedAccount = synchronized(lock) {
        val token = AccountSessionToken(account.userId, ++generation)
        AuthenticatedAccount(account, token).also { _currentSession.value = it }
    }

    fun invalidate() = synchronized(lock) {
        generation++
        _currentSession.value = null
    }

    fun isCurrent(token: AccountSessionToken): Boolean = synchronized(lock) {
        _currentSession.value?.token == token
    }

    /** 在认证状态锁内校验 [token] 并完成不可挂起的状态提交。 */
    fun commitIfCurrent(
        token: AccountSessionToken,
        commit: (AuthenticatedAccount) -> Boolean,
    ): Boolean = synchronized(lock) {
        val session = _currentSession.value ?: return@synchronized false
        session.token == token && commit(session)
    }
}

object SharedFlowCentre {
    private val sessionRegistry = AuthenticationSessionRegistry()
    val currentSession: StateFlow<AuthenticatedAccount?> = sessionRegistry.currentSession

    private val _webSocket = MutableSharedFlow<AccountWebSocketEvent>()
    val webSocket: SharedFlow<AccountWebSocketEvent> = _webSocket.asSharedFlow()

    private val _authed = MutableSharedFlow<AuthenticatedAccount>()
    val authed: SharedFlow<AuthenticatedAccount> = _authed.asSharedFlow()

    private val _logout = MutableSharedFlow<Unit>()
    val logout: SharedFlow<Unit> = _logout.asSharedFlow()

    val toastText = MutableSharedFlow<ToastText>()

    val toPagerTop = MutableSharedFlow<Unit>()

    suspend fun emitAuthenticated(account: AccountDto) {
        _authed.emit(sessionRegistry.authenticate(account))
    }

    suspend fun emitWebSocket(event: AccountWebSocketEvent) {
        if (sessionRegistry.isCurrent(event.token)) _webSocket.emit(event)
    }

    suspend fun emitLogout() {
        sessionRegistry.invalidate()
        _logout.emit(Unit)
    }

    fun isCurrentSession(token: AccountSessionToken): Boolean =
        sessionRegistry.isCurrent(token)

    internal fun commitIfCurrentSession(
        token: AccountSessionToken,
        commit: (AuthenticatedAccount) -> Boolean,
    ): Boolean = sessionRegistry.commitIfCurrent(token, commit)
}
