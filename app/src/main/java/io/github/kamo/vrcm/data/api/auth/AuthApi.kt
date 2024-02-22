package io.github.kamo.vrcm.data.api.auth

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.http.content.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

internal const val AUTH_API_PREFIX = "auth"

internal const val EMAIL_OTP = "emailOtp"

class AuthApi(private val client: HttpClient) {

    private suspend fun userRes(username: String? = null, password: String? = null) =
        client.get("$AUTH_API_PREFIX/user") {
            url { path(AUTH_API_PREFIX, "user") }
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

            HttpStatusCode.Unauthorized -> AuthState.Unauthorized

            else -> AuthState.Unauthorized

        }
    }

    suspend fun currentUser(): UserInfo = userRes().body()


    fun friendsFlow(offline: Boolean = false, offset: Int = 0, n: Int = 50): Flow<List<FriendInfo>> = flow {
        var count = 0
        while (true) {
            val bodyList: List<FriendInfo> = client.get {
                    url {
                        path(AUTH_API_PREFIX, "user", "friends")
                        this.parameters.run {
                            append("offline", offline.toString())
                            append("offset", (offset + count * n).toString())
                            append("n", n.toString())
                        }
                    }
                }.body()
            if (bodyList.isEmpty()) break
            emit(bodyList)
            count++
        }
    }

    suspend fun verify(code: String, authType: AuthType): Boolean {
        val response = client.post("$AUTH_API_PREFIX/twofactorauth/${authType.path}/verify") {
            setBody(TextContent("""{"code":"$code"}""", ContentType.Application.Json))
        }
        return response.status == HttpStatusCode.OK
    }

    suspend fun isAuthed(): Boolean? = runCatching {
        val response = client.get(AUTH_API_PREFIX) {
            url { path(AUTH_API_PREFIX) }
        }
        return response.status == HttpStatusCode.OK && response.body<AuthInfo>().ok == true
    }.getOrNull()
}


enum class AuthType(val path: String) {
    Email("emailotp"),
    TFA("otp"),
    TTFA("totp");
}

enum class AuthState {
    Authed,
    NeedTFA,
    NeedEmailCode,
    Unauthorized;
}

