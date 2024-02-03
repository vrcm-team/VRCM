package io.github.kamo.vrcm.domain.api.auth

import io.github.kamo.vrcm.domain.api.APIClient
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.util.*

internal const val AUTH_API_SUFFIX = "auth"

internal const val EMAIL_OTP = "emailOtp"

class AuthAPI(private val client: APIClient) {

    private suspend fun userRes(username: String? = null, password: String? = null) =
        client.get {
            url { path(AUTH_API_SUFFIX, "user") }
            if (username != null && password != null) {
                basicAuth(username, password)
            }
        }

    suspend fun login(username: String, password: String): AuthState {
        val response = userRes(username, password)
        return when (response.status) {
            HttpStatusCode.OK -> {
                val requiresTwoFactorAuth = response.body<AuthInfo>().requiresTwoFactorAuth
                if (requiresTwoFactorAuth == null) {
                    AuthState.Authed
                } else if (requiresTwoFactorAuth.contains(EMAIL_OTP)) {
                    AuthState.NeedEmailCode
                } else {
                    AuthState.NeedTFA
                }
            }
            HttpStatusCode.Unauthorized -> {
                AuthState.Unauthorized
            }

            else -> {
                AuthState.Unauthorized
            }
        }
    }


    suspend fun userInfo(): UseInfo = userRes().body()

    @OptIn(InternalAPI::class)
    suspend fun verify(code: String, authType: AuthType): Boolean {
        val response = client.post {
            url { path(AUTH_API_SUFFIX, "twofactorauth", authType.path, "verify") }
            this.body = TextContent("""{"code":"$code"}""", ContentType.Application.Json)
        }
        return response.status == HttpStatusCode.OK
    }

    suspend fun isAuthed(): Boolean {
        val response = client.get {
            url { path(AUTH_API_SUFFIX) }
        }
        return response.status == HttpStatusCode.OK && response.body<AuthInfo>().ok == true
    }


}

enum class AuthType {
    Email,
    TFA,
    TTFA;

    val path: String
        get() = when (this) {
            Email -> "emailotp"
            TFA -> "otp"
            TTFA -> "totp"
        }
}

enum class AuthState {
    Authed,
    NeedTFA,
    NeedEmailCode,
    Unauthorized;
}

