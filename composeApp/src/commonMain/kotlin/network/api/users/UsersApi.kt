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
import io.github.vrcmteam.vrcm.network.api.users.data.BoopData
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.Serializable

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

    suspend fun boop(userId: String, emojiId: String? = null): VRChatResponse =
        client.post("$USERS_API_PREFIX/$userId/boop") {
            contentType(ContentType.Application.Json)
            val normalizedEmojiId = emojiId?.trim().orEmpty()
            if (normalizedEmojiId.isEmpty()) {
                setBody(emptyMap<String, String>())
            } else {
                setBody(BoopData(emojiId = normalizedEmojiId))
            }
        }.checkSuccess()

    suspend fun isUserBlocked(userId: String): Boolean {
        requireValidModerationUserId(userId)
        val moderations = client.get(PLAYER_MODERATIONS_PATH) {
            parameter("type", PLAYER_BLOCK_TYPE)
            parameter("targetUserId", userId)
        }.checkSuccess<List<PlayerModerationData>>()
        return moderations.any {
            it.targetUserId == userId && it.type == PLAYER_BLOCK_TYPE
        }
    }

    suspend fun blockUser(userId: String) {
        requireValidModerationUserId(userId)
        val moderation = client.post(PLAYER_MODERATIONS_PATH) {
            contentType(ContentType.Application.Json)
            setBody(PlayerModerationRequest(moderated = userId, type = PLAYER_BLOCK_TYPE))
        }.checkSuccess<PlayerModerationData>()
        check(moderation.targetUserId == userId && moderation.type == PLAYER_BLOCK_TYPE) {
            "Player moderation response did not match the request"
        }
    }

    suspend fun unblockUser(userId: String) {
        requireValidModerationUserId(userId)
        client.put(UNPLAYER_MODERATE_PATH) {
            contentType(ContentType.Application.Json)
            setBody(PlayerModerationRequest(moderated = userId, type = PLAYER_BLOCK_TYPE))
        }.checkSuccess<VRChatResponse>().toResult().getOrThrow()
    }

    private fun requireValidModerationUserId(userId: String) {
        require(USER_ID_PATTERN.matches(userId)) { "Invalid user ID" }
    }

    private companion object {
        const val PLAYER_MODERATIONS_PATH = "auth/user/playermoderations"
        const val UNPLAYER_MODERATE_PATH = "auth/user/unplayermoderate"
        const val PLAYER_BLOCK_TYPE = "block"
        val USER_ID_PATTERN = Regex("[A-Za-z0-9_-]+")
    }

}

@Serializable
private data class PlayerModerationRequest(
    val moderated: String,
    val type: String,
)

@Serializable
private data class PlayerModerationData(
    val targetUserId: String,
    val type: String,
)
