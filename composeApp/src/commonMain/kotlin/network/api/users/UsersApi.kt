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
import io.github.vrcmteam.vrcm.network.api.users.data.PlayerInteractionModerationData
import io.github.vrcmteam.vrcm.network.api.users.data.PlayerInteractionOverride
import io.github.vrcmteam.vrcm.network.api.users.data.PlayerInteractionSnapshot
import io.github.vrcmteam.vrcm.network.api.users.data.resolvePlayerInteractionSnapshot
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

    internal suspend fun getPlayerInteractionSnapshot(userId: String): PlayerInteractionSnapshot {
        requireValidModerationUserId(userId)
        val moderations = client.get(PLAYER_MODERATIONS_PATH) {
            parameter("targetUserId", userId)
        }.checkSuccess<List<PlayerInteractionModerationData>>()
        return resolvePlayerInteractionSnapshot(userId, moderations)
    }

    internal suspend fun removePlayerInteractionOverride(
        userId: String,
        override: PlayerInteractionOverride,
    ) {
        requireValidModerationUserId(userId)
        requireExplicitInteractionOverride(override)
        client.put(UNPLAYER_MODERATE_PATH) {
            contentType(ContentType.Application.Json)
            setBody(PlayerInteractionModerationRequest(userId, override.apiValue!!))
        }.checkSuccess<VRChatResponse>().toResult().getOrThrow()
    }

    internal suspend fun createPlayerInteractionOverride(
        userId: String,
        override: PlayerInteractionOverride,
    ) {
        requireValidModerationUserId(userId)
        requireExplicitInteractionOverride(override)
        val moderation = client.post(PLAYER_MODERATIONS_PATH) {
            contentType(ContentType.Application.Json)
            setBody(PlayerInteractionModerationRequest(userId, override.apiValue!!))
        }.checkSuccess<PlayerInteractionModerationData>()
        check(moderation.targetUserId == userId && moderation.type == override.apiValue) {
            "Player interaction moderation response did not match the request"
        }
    }

    private fun requireValidModerationUserId(userId: String) {
        require(USER_ID_PATTERN.matches(userId)) { "Invalid user ID" }
    }

    private fun requireExplicitInteractionOverride(override: PlayerInteractionOverride) {
        require(override != PlayerInteractionOverride.Default) {
            "The default interaction setting cannot be written as a moderation"
        }
    }

    private companion object {
        const val PLAYER_MODERATIONS_PATH = "auth/user/playermoderations"
        const val UNPLAYER_MODERATE_PATH = "auth/user/unplayermoderate"
        val USER_ID_PATTERN = Regex("[A-Za-z0-9_-]+")
    }

}

@Serializable
private data class PlayerInteractionModerationRequest(
    val moderated: String,
    val type: String,
)
