package io.github.kamo.vrcm.data.api.auth

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.basicAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.path
import io.ktor.util.InternalAPI
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

internal const val AUTH_API_SUFFIX = "auth"

internal const val EMAIL_OTP = "emailOtp"

class AuthAPI(private val client: HttpClient) {

    private suspend fun userRes(username: String? = null, password: String? = null) =
        client.get("$AUTH_API_SUFFIX/user") {
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

    suspend fun currentUser(): UserInfo = userRes().body()


    suspend fun friendsFlow(offline: Boolean = false, offset: Int = 0, n: Int = 50): Flow<List<FriendInfo>> {
        return flow {
            var count = 0
            while (true) {
                val response = client.get {
                    url {
                        path(AUTH_API_SUFFIX, "user", "friends")
                        this.parameters.run {
                            append("offline", offline.toString())
                            append("offset", (offset + count * n).toString())
                            append("n", n.toString())
                        }
                    }
                }
                val bodyList = response.body<List<FriendInfo>>()
                if (bodyList.isEmpty())  break
                emit(bodyList)
                count++
            }
        }
    }


    @OptIn(InternalAPI::class)
    suspend fun verify(code: String, authType: AuthType): Boolean {
        val response = client.post("$AUTH_API_SUFFIX/twofactorauth/${authType.path}/verify") {
            this.body = TextContent("""{"code":"$code"}""", ContentType.Application.Json)
        }
        return response.status == HttpStatusCode.OK
    }

    suspend fun isAuthed(): Boolean {
        val response = client.get(AUTH_API_SUFFIX) {
            url { path(AUTH_API_SUFFIX) }
        }
        return response.status == HttpStatusCode.OK && response.body<AuthInfo>().ok == true
    }
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

