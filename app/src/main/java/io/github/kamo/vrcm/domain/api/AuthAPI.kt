package io.github.kamo.vrcm.domain.api

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.util.*

internal const val AUTH_API_SUFFIX   = "auth"

internal const val EMAIL_OTP = "emailOtp"
object AuthAPI {

    suspend fun login(username: String, password: String) : AuthState {
        val response = APIClient.get {
            url { path(AUTH_API_SUFFIX, "user")}
            basicAuth(username, password)
        }
        println(response)
        return when(response.status){
            HttpStatusCode.OK -> {
                if (response.body<AuthInfo>().requiresTwoFactorAuth.contains(EMAIL_OTP)) {
                    AuthState.NeedEmailCode
                } else {
                    AuthState.Authed
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
    @OptIn(InternalAPI::class)
    suspend fun verify(code: String, authType: AuthType) : Boolean{
        val response = APIClient.post {
            url { path(AUTH_API_SUFFIX, "twofactorauth",authType.path,"verify")}
            this.body = TextContent("""{"code":"$code"}""", ContentType.Application.Json)
        }
        println(response)
        return  response.status == HttpStatusCode.OK
    }
}

class AuthInfo(
  val  requiresTwoFactorAuth:List<String>
)
enum class AuthType {
    Email,
    TFA,
    TTFA;
    val path: String
        get() = when(this){
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
