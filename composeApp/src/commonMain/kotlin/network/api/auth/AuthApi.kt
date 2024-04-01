package io.github.vrcmteam.vrcm.network.api.auth

import io.github.vrcmteam.vrcm.network.api.attributes.AUTH_API_PREFIX
import io.github.vrcmteam.vrcm.network.api.attributes.AuthState
import io.github.vrcmteam.vrcm.network.api.attributes.AuthType
import io.github.vrcmteam.vrcm.network.api.attributes.USER_API_PREFIX
import io.github.vrcmteam.vrcm.network.api.auth.data.AuthData
import io.github.vrcmteam.vrcm.network.api.auth.data.CurrentUserData
import io.github.vrcmteam.vrcm.network.extensions.ifOK
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.basicAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.path

class AuthApi(
    private val client: HttpClient,
) {

    suspend fun currentUser(): Result<CurrentUserData> = userRes().ifOK { body<CurrentUserData>() }


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

    /**
     * 请求验证验证码
     */
    suspend fun verify(code: String, authType: AuthType): Boolean {
        // emailOtp -> emailotp
        val authTypePath = authType.typeName.lowercase()
        val response = client.post("$AUTH_API_PREFIX/twofactorauth/$authTypePath/verify") {
            setBody(TextContent("""{"code":"$code"}""", ContentType.Application.Json))
        }
        return response.status == HttpStatusCode.OK
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




