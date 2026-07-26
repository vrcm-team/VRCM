package io.github.vrcmteam.vrcm.network.api.users

import io.github.vrcmteam.vrcm.network.api.attributes.USERS_API_PREFIX
import io.github.vrcmteam.vrcm.network.api.attributes.USER_NOTES_API_PREFIX
import io.github.vrcmteam.vrcm.network.api.attributes.VRChatResponse
import io.github.vrcmteam.vrcm.network.api.users.data.SearchUserData
import io.github.vrcmteam.vrcm.network.api.users.data.LimitedUserGroup
import io.github.vrcmteam.vrcm.network.api.users.data.MutualFriendData
import io.github.vrcmteam.vrcm.network.api.users.data.UserData
import io.github.vrcmteam.vrcm.network.extensions.checkSuccess
import io.github.vrcmteam.vrcm.network.api.users.data.CurrentUpdateUserData
import io.github.vrcmteam.vrcm.network.api.users.data.UpdateUserInfoData
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*

class UsersApi(private val client: HttpClient) {

    suspend fun fetchUser(userId: String) =
        client.get("$USERS_API_PREFIX/$userId").checkSuccess<UserData>()

    suspend fun fetchUserResponse(userId: String) =
        client.get("$USERS_API_PREFIX/$userId").checkSuccess { this }

    suspend fun searchUser(
        search: String,
        sort: String = "relevance",
        n: Int = 20,
        offset: Int = 0,
    ) =
        client.get(USERS_API_PREFIX) {
            parameter("sort", sort)
            parameter("n", n)
            parameter("offset", offset)
            parameter("search", search)
        }.checkSuccess<List<SearchUserData>>()

    suspend fun updateUserInfo(userId: String, updateUserInfoData: UpdateUserInfoData) = client.put("$USERS_API_PREFIX/$userId"){
        setBody(updateUserInfoData)
        contentType(ContentType.Application.Json)
    }.checkSuccess<CurrentUpdateUserData>()

    suspend fun addTags(userId: String, tags: List<String>) = client.post("$USERS_API_PREFIX/$userId/addTags"){
        setBody(mapOf("tags" to tags))
        contentType(ContentType.Application.Json)
    }.checkSuccess<CurrentUpdateUserData>()

    suspend fun removeTags(userId: String, tags: List<String>) = client.post("$USERS_API_PREFIX/$userId/removeTags"){
        setBody(mapOf("tags" to tags))
        contentType(ContentType.Application.Json)
    }.checkSuccess<CurrentUpdateUserData>()

    suspend fun getUserGroups(userId: String): List<LimitedUserGroup> =
        client.get("$USERS_API_PREFIX/$userId/groups").checkSuccess()

    suspend fun getMutualFriends(
        userId: String,
        n: Int = 100,
        offset: Int = 0,
    ): List<MutualFriendData> =
        client.get("$USERS_API_PREFIX/$userId/mutuals/friends") {
            parameter("n", n)
            parameter("offset", offset)
        }.checkSuccess()

    suspend fun saveUserNote(targetUserId: String, note: String): String =
        client.post(USER_NOTES_API_PREFIX) {
            setBody(mapOf("targetUserId" to targetUserId, "note" to note))
            contentType(ContentType.Application.Json)
        }.checkSuccess { bodyAsText() }

    suspend fun boop(userId: String): VRChatResponse =
        client.post("$USERS_API_PREFIX/$userId/boop") {
            contentType(ContentType.Application.Json)
            setBody(emptyMap<String, String>())
        }.checkSuccess()

}
