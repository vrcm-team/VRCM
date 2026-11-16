package io.github.vrcmteam.vrcm.network.websocket

import io.github.vrcmteam.vrcm.core.shared.AccountSessionToken

/** Restores or invalidates an account after realtime authentication rejects its session. */
fun interface WebSocketSessionRecovery {
    suspend fun recoverExpiredSession(sessionToken: AccountSessionToken)
}
