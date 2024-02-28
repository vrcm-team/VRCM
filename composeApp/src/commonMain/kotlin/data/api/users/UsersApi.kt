package io.github.vrcmteam.vrcm.data.api.users

import io.github.vrcmteam.vrcm.data.api.VRCApiException
import io.github.vrcmteam.vrcm.data.api.ifOK
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*

internal const val USERS_API_PREFIX = "users"

class UsersApi(
    private val client: HttpClient
) {

    @Throws(VRCApiException::class)
    suspend fun fetchUser(userId: String) =
        client.get("$USERS_API_PREFIX/$userId").ifOK { body<UserData>() }


}
