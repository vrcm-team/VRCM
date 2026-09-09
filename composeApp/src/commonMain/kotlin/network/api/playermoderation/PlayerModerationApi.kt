package io.github.vrcmteam.vrcm.network.api.playermoderation

import io.github.vrcmteam.vrcm.core.shared.AccountSessionToken
import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.network.api.attributes.AUTH_API_PREFIX
import io.github.vrcmteam.vrcm.network.api.attributes.USER_API_PREFIX
import io.github.vrcmteam.vrcm.network.api.playermoderation.data.PlayerModerationData as CleanupPlayerModerationData
import io.github.vrcmteam.vrcm.network.api.playermoderation.data.PlayerModerationRequest as CleanupPlayerModerationRequest
import io.github.vrcmteam.vrcm.network.api.playermoderation.data.PlayerModerationType
import io.github.vrcmteam.vrcm.network.extensions.checkSuccess
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.CompletableDeferred
import kotlinx.serialization.Serializable

private const val PLAYER_MODERATIONS_PATH = "$AUTH_API_PREFIX/$USER_API_PREFIX/playermoderations"
private const val UNPLAYER_MODERATE_PATH = "$AUTH_API_PREFIX/$USER_API_PREFIX/unplayermoderate"

internal enum class VoiceModerationType(val apiValue: String) {
    Mute("mute"),
    Unmute("unmute"),
    ;

    companion object {
        fun fromApiValue(value: String): VoiceModerationType? =
            entries.firstOrNull { it.apiValue == value }
    }
}

@Serializable
internal data class PlayerModerationData(
    val created: String = "",
    val id: String = "",
    val sourceDisplayName: String = "",
    val sourceUserId: String = "",
    val targetDisplayName: String = "",
    val targetUserId: String = "",
    val type: String = "",
)

@Serializable
private data class ModeratePlayerRequest(
    val moderated: String,
    val type: String,
)

/** API operations for the account's player management records and voice overrides. */
class PlayerModerationApi(private val client: HttpClient) {
    private val targetReadLock = SynchronizedObject()
    private val inFlightTargetReads = mutableMapOf<TargetReadKey, CompletableDeferred<List<PlayerModerationData>>>()

    internal suspend fun getAll(): List<PlayerModerationData> =
        client.get(PLAYER_MODERATIONS_PATH).checkSuccess()

    suspend fun get(type: PlayerModerationType? = null): List<CleanupPlayerModerationData> =
        client.get(PLAYER_MODERATIONS_PATH) {
            type?.let { parameter("type", it.apiValue) }
        }.checkSuccess()

    internal suspend fun getForTarget(targetUserId: String): List<PlayerModerationData> {
        val key = TargetReadKey(
            sessionToken = SharedFlowCentre.currentSession.value?.token,
            targetUserId = targetUserId,
        )
        var ownsRequest = false
        val request = synchronized(targetReadLock) {
            inFlightTargetReads[key] ?: CompletableDeferred<List<PlayerModerationData>>().also {
                ownsRequest = true
                inFlightTargetReads[key] = it
            }
        }
        if (!ownsRequest) return request.await()

        try {
            val result = client.get(PLAYER_MODERATIONS_PATH) {
                parameter("targetUserId", targetUserId)
            }.checkSuccess<List<PlayerModerationData>>()
            request.complete(result)
        } catch (error: Throwable) {
            request.completeExceptionally(error)
        } finally {
            synchronized(targetReadLock) {
                if (inFlightTargetReads[key] === request) inFlightTargetReads.remove(key)
            }
        }
        return request.await()
    }

    internal suspend fun moderate(
        targetUserId: String,
        type: VoiceModerationType,
    ): PlayerModerationData = client.post(PLAYER_MODERATIONS_PATH) {
        contentType(ContentType.Application.Json)
        setBody(ModeratePlayerRequest(moderated = targetUserId, type = type.apiValue))
    }.checkSuccess()

    internal suspend fun remove(
        targetUserId: String,
        type: VoiceModerationType,
    ) {
        client.put(UNPLAYER_MODERATE_PATH) {
            contentType(ContentType.Application.Json)
            setBody(ModeratePlayerRequest(moderated = targetUserId, type = type.apiValue))
        }.checkSuccess { Unit }
    }

    suspend fun remove(targetUserId: String, type: PlayerModerationType) {
        require(CLEANUP_USER_ID_PATTERN.matches(targetUserId)) { "Invalid target user ID" }
        client.put(UNPLAYER_MODERATE_PATH) {
            contentType(ContentType.Application.Json)
            setBody(CleanupPlayerModerationRequest(moderated = targetUserId, type = type.apiValue))
        }.checkSuccess { Unit }
    }

    private companion object {
        val CLEANUP_USER_ID_PATTERN = Regex("usr_[A-Za-z0-9_-]+")
    }

    private data class TargetReadKey(
        val sessionToken: AccountSessionToken?,
        val targetUserId: String,
    )
}
