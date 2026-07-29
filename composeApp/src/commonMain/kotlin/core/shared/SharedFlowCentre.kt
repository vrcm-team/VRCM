package io.github.vrcmteam.vrcm.core.shared

import io.github.vrcmteam.vrcm.network.websocket.data.WebSocketEvent
import io.github.vrcmteam.vrcm.presentation.compoments.ToastText
import io.github.vrcmteam.vrcm.service.data.AccountDto
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

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
    private val lock = Any()
    private var generation = 0L
    private var currentToken: AccountSessionToken? = null

    fun authenticate(account: AccountDto): AuthenticatedAccount = synchronized(lock) {
        val token = AccountSessionToken(account.userId, ++generation)
        currentToken = token
        AuthenticatedAccount(account, token)
    }

    fun invalidate() = synchronized(lock) {
        generation++
        currentToken = null
    }

    fun isCurrent(token: AccountSessionToken): Boolean = synchronized(lock) {
        currentToken == token
    }
}

object SharedFlowCentre {
    private val sessionRegistry = AuthenticationSessionRegistry()

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
}
