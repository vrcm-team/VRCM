package io.github.vrcmteam.vrcm.service

import io.github.vrcmteam.vrcm.core.shared.AccountSessionToken
import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.network.api.auth.AuthApi
import io.ktor.http.URLProtocol
import io.ktor.http.Url

/** Only official HTTPS device links may initiate a device approval. */
internal fun parseDeviceAuthCode(value: String): String? {
    val url = runCatching { Url(value.trim()) }.getOrNull() ?: return null
    if (url.protocol != URLProtocol.HTTPS || url.port != 443) return null
    if (url.host != "vrchat.com" && url.host != "www.vrchat.com") return null
    if (url.user != null || url.password != null || url.encodedPath != "/home/device") return null
    val code = url.parameters.getAll("code")?.singleOrNull() ?: return null
    return code.takeIf { Regex("[A-Z0-9]{8}").matches(it) }
}

/** Approvals are bound to the account that inspected the QR code. */
internal fun interface DeviceAuthApprover {
    suspend fun approve(userCode: String, session: AccountSessionToken): Result<Unit>
}

internal class DeviceAuthService(
    private val authApi: AuthApi,
    private val authService: AuthService,
) : DeviceAuthApprover {
    override suspend fun approve(userCode: String, session: AccountSessionToken): Result<Unit> {
        val response = authService.runSessionBoundCatching(session) {
            authApi.approveDevice(userCode)
        }
        return if (response != null && SharedFlowCentre.isCurrentSession(response.sessionToken)) {
            response.result
        } else {
            Result.failure(IllegalStateException("Authentication session changed"))
        }
    }
}
