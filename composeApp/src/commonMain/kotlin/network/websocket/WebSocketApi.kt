package io.github.vrcmteam.vrcm.network.websocket

import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.core.shared.AccountSessionToken
import io.github.vrcmteam.vrcm.core.shared.AccountWebSocketEvent
import io.github.vrcmteam.vrcm.network.api.attributes.AUTH_API_PREFIX
import io.github.vrcmteam.vrcm.network.api.attributes.VRC_WSS_URL
import io.github.vrcmteam.vrcm.network.api.auth.data.AuthData
import io.github.vrcmteam.vrcm.network.websocket.data.WebSocketEvent
import io.github.vrcmteam.vrcm.presentation.compoments.ToastText
import io.ktor.client.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.client.call.*
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.*
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import org.koin.core.logger.Logger

class WebSocketApi(
    private val apiClient: HttpClient,
    private val logger: Logger,
) {

    private var currentJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val connectionLock = SynchronizedObject()
    private val isForeground = atomic(true)
    private val backgroundMonitoringEnabled = atomic(false)

    init {
        scope.launch {
            SharedFlowCentre.authed.collect { session ->
                if (isForeground.value || backgroundMonitoringEnabled.value) replaceConnection(session.token)
            }
        }
        scope.launch {
            SharedFlowCentre.logout.collect {
                replaceConnection(null)
            }
        }
    }

    fun onBackground() {
        isForeground.value = false
        if (!backgroundMonitoringEnabled.value) replaceConnection(null)
    }

    fun onForeground() {
        if (isForeground.getAndSet(true)) return
        replaceConnection(SharedFlowCentre.currentSession.value?.token)
    }

    fun setBackgroundMonitoringEnabled(enabled: Boolean) {
        backgroundMonitoringEnabled.value = enabled
        if (enabled || !isForeground.value) replaceConnection(SharedFlowCentre.currentSession.value?.token.takeIf { enabled || isForeground.value })
    }

    private fun replaceConnection(sessionToken: AccountSessionToken?) = synchronized(connectionLock) {
        currentJob?.cancel()
        currentJob = sessionToken?.let { token ->
            scope.launch { startWebSocket(token) }
        }
    }

    private suspend fun startWebSocket(sessionToken: AccountSessionToken) {
        retryWebSocketConnection(
            maxRetryDelayMillis = MAX_RETRY_DELAY_MILLIS,
            onFailure = ::reportConnectionFailure,
        ) {
            val authResponse = apiClient.get(AUTH_API_PREFIX)
            check(authResponse.status == HttpStatusCode.OK) {
                "WebSocket auth failed with HTTP ${authResponse.status.value}"
            }
            val authData = authResponse.body<AuthData>()
            val token = authData.token.takeIf { authData.ok == true && !it.isNullOrBlank() }
                ?: error("WebSocket auth response did not contain a token")
            apiClient.ws(
                urlString = VRC_WSS_URL,
                request = {
                    parameter("auth", token)
                }) {
                while (true) {
                    val othersMessage = receiveDeserialized<WebSocketEvent>()
                    SharedFlowCentre.emitWebSocket(
                        AccountWebSocketEvent(sessionToken, othersMessage)
                    )
                }
            }
        }
    }

    private suspend fun reportConnectionFailure(error: Exception, consecutiveFailures: Int) {
        val errorType = error::class.simpleName ?: "Exception"
        logger.error(
            "WebSocket reconnect attempt $consecutiveFailures failed ($errorType): ${error.message.orEmpty()}"
        )
        if (consecutiveFailures == 1 || consecutiveFailures % USER_NOTICE_INTERVAL == 0) {
            SharedFlowCentre.toastText.emit(
                ToastText.Error(
                    "好友实时连接失败，正在重试: ${error.message.orEmpty()}"
                )
            )
        }
    }

    private companion object {
        const val USER_NOTICE_INTERVAL = 12
        const val MAX_RETRY_DELAY_MILLIS = 5 * 60 * 1_000L
    }
}

internal suspend fun retryWebSocketConnection(
    retryDelayMillis: Long = 5_000L,
    maxRetryDelayMillis: Long = retryDelayMillis,
    onFailure: suspend (Exception, consecutiveFailures: Int) -> Unit,
    connect: suspend () -> Unit,
) {
    require(retryDelayMillis > 0) { "retryDelayMillis must be positive" }
    require(maxRetryDelayMillis >= retryDelayMillis) { "maxRetryDelayMillis must not be lower than retryDelayMillis" }

    var consecutiveFailures = 0
    while (currentCoroutineContext().isActive) {
        try {
            connect()
            consecutiveFailures = 0
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            consecutiveFailures++
            onFailure(e, consecutiveFailures)
        }
        // Exponential backoff avoids a permanent five-second reconnect loop
        // when the device has no network or is in Android's idle mode.
        val delayMillis = retryDelayMillis * (1L shl (consecutiveFailures - 1).coerceAtMost(6))
        delay(delayMillis.coerceAtMost(maxRetryDelayMillis))
    }
}
