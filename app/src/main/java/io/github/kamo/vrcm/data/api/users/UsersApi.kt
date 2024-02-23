package io.github.kamo.vrcm.data.api.users

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get

internal const val USERS_API_PREFIX = "users"

class UsersApi(
    private val client: HttpClient
) {

    suspend fun fetchUser(userId: String) = client.get("$USERS_API_PREFIX/$userId").body<UserData>()


}