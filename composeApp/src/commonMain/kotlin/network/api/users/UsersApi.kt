package io.github.vrcmteam.vrcm.network.api.users

import io.github.vrcmteam.vrcm.network.api.attributes.USERS_API_PREFIX
import io.github.vrcmteam.vrcm.network.api.users.data.SearchUserData
import io.github.vrcmteam.vrcm.network.api.users.data.UserData
import io.github.vrcmteam.vrcm.network.extensions.ifOKOrThrow
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*

class UsersApi(private val client: HttpClient) {

    suspend fun fetchUser(userId: String) =
        client.get("$USERS_API_PREFIX/$userId").ifOKOrThrow { body<UserData>() }

    suspend fun fetchUserResponse(userId: String) =
        client.get("$USERS_API_PREFIX/$userId").ifOKOrThrow { this }

    suspend fun searchUser(
        search: String,
        sort: String = "relevance",
        n: Int = 50,
        offset: Int = 0
    ) =
        client.get(USERS_API_PREFIX) {
            parameter("sort", sort)
            parameter("n", n)
            parameter("offset", offset)
            parameter("search", search)
        }.ifOKOrThrow { body<List<SearchUserData>>() }

}
