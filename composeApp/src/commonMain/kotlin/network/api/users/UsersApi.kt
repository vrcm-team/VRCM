package io.github.vrcmteam.vrcm.network.api.users

import io.github.vrcmteam.vrcm.network.api.attributes.USERS_API_PREFIX
import io.github.vrcmteam.vrcm.network.api.users.data.UserData
import io.github.vrcmteam.vrcm.network.extensions.ifOK
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get

class UsersApi(private val client: HttpClient) {

    suspend fun fetchUser(userId: String) =
        client.get("$USERS_API_PREFIX/$userId").ifOK { body<UserData>() }
    suspend fun fetchUserResponse(userId: String) =
        client.get("$USERS_API_PREFIX/$userId").ifOK { this }

}
