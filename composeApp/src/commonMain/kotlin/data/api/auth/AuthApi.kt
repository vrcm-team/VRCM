package io.github.vrcmteam.vrcm.data.api.auth

import io.github.vrcmteam.vrcm.data.api.AuthType
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
                val requiresTwoFactorAuth = response.body<AuthData>().requiresTwoFactorAuth
                if (requiresTwoFactorAuth == null) {
                    AuthState.Authed
                } else{
                    when{
                        requiresTwoFactorAuth.contains(AuthType.Email.typeName) ->
                            AuthState.NeedEmailCode
                        requiresTwoFactorAuth.contains(AuthType.TFA.typeName) ->
                            AuthState.NeedTFA
                        requiresTwoFactorAuth.contains(AuthType.TTFA.typeName) ->
                            AuthState.NeedTTFA
                        else -> error("Unknown auth type: $requiresTwoFactorAuth")
                    }
                }
            }

            HttpStatusCode.Unauthorized -> AuthState.Unauthorized

            else -> AuthState.Unauthorized

        }
    }

    fun friendsFlow(offline: Boolean = false, offset: Int = 0, n: Int = 50): Flow<List<FriendData>> = flow {
        var count = 0
        while (true) {
            val bodyList: List<FriendData> = client.get {
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
        // emailOtp -> emailotp
        val authTypePath = authType.typeName.lowercase()
        val response = client.post("$AUTH_API_PREFIX/twofactorauth/$authTypePath/verify") {
            setBody(TextContent("""{"code":"$code"}""", ContentType.Application.Json))
        }
        return response.status == HttpStatusCode.OK
    }

    suspend fun isAuthed(): Boolean {
        val response = client.get(AUTH_API_PREFIX) {
            url { path(AUTH_API_PREFIX) }
        }
        return response.status == HttpStatusCode.OK && response.body<AuthData>().ok == true
    }

}

enum class AuthState {
    Authed,
    NeedEmailCode,
    NeedTFA,
    NeedTTFA,
    Unauthorized
}


