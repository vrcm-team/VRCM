package io.github.vrcmteam.vrcm.network.api.users

import io.github.vrcmteam.vrcm.network.api.users.data.UserData
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import network.extensions.ifOK

internal const val USERS_API_PREFIX = "users"

class UsersApi(
    private val client: HttpClient
) {

    suspend fun fetchUser(userId: String) =
        client.get("$USERS_API_PREFIX/$userId").ifOK { body<UserData>() }

}
