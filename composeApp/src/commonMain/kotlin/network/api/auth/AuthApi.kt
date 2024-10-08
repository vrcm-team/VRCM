package io.github.vrcmteam.vrcm.network.api.auth

import io.github.vrcmteam.vrcm.network.api.attributes.AUTH_API_PREFIX
import io.github.vrcmteam.vrcm.network.api.attributes.AuthState
import io.github.vrcmteam.vrcm.network.api.attributes.AuthType
import io.github.vrcmteam.vrcm.network.api.attributes.USER_API_PREFIX
import io.github.vrcmteam.vrcm.network.api.auth.data.AuthData
import io.github.vrcmteam.vrcm.network.api.auth.data.CurrentUserData
import io.github.vrcmteam.vrcm.network.extensions.checkSuccess
import io.github.vrcmteam.vrcm.network.extensions.ifOK
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.content.*

class AuthApi(
    private val client: HttpClient,
) {

    suspend fun currentUser(): CurrentUserData = userRes().checkSuccess()


    private suspend fun userRes(username: String? = null, password: String? = null) =
        client.get {
            url { path(AUTH_API_PREFIX, USER_API_PREFIX) }
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
                        requiresTwoFactorAuth.contains(AuthType.TTFA.typeName) ->
                            AuthState.NeedTTFA
                        requiresTwoFactorAuth.contains(AuthType.TFA.typeName) ->
                            AuthState.NeedTFA
                        else -> error("Unknown auth type: $requiresTwoFactorAuth")
                    }
                }
            }

            HttpStatusCode.Unauthorized -> AuthState.Unauthorized(response.bodyAsText())

            else -> AuthState.Unauthorized(response.bodyAsText())

        }
    }

    /**
     * 请求验证验证码
     */
    suspend fun verify(code: String, authType: AuthType): Result<Unit> {
        // emailOtp -> emailotp
        val authTypePath = authType.typeName.lowercase()
        return client.post("$AUTH_API_PREFIX/twofactorauth/$authTypePath/verify") {
            setBody(TextContent("""{"code":"$code"}""", ContentType.Application.Json))
        }.ifOK { }
    }

    /**
     * 判断是否已经登录
     */
    suspend fun isAuthed(): Boolean {
        val response = client.get(AUTH_API_PREFIX) {
            url { path(AUTH_API_PREFIX) }
        }
        return response.status == HttpStatusCode.OK && response.body<AuthData>().ok == true
    }

}




