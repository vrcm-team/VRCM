package io.github.vrcmteam.vrcm.network.websocket

import io.github.vrcmteam.vrcm.core.shared.AccountSessionToken

/** Restores or invalidates an authenticated account after Pipeline rejects its session. */
fun interface WebSocketSessionRecovery {
    suspend fun recoverExpiredSession(sessionToken: AccountSessionToken)
}
