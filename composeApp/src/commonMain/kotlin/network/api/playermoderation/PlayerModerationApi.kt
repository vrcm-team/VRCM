package io.github.vrcmteam.vrcm.network.api.playermoderation

import io.github.vrcmteam.vrcm.network.api.attributes.AUTH_API_PREFIX
import io.github.vrcmteam.vrcm.network.api.attributes.USER_API_PREFIX
import io.github.vrcmteam.vrcm.network.extensions.checkSuccess
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
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
    val created: String,
    val id: String,
    val sourceDisplayName: String,
    val sourceUserId: String,
    val targetDisplayName: String,
    val targetUserId: String,
    val type: String,
)

@Serializable
private data class ModeratePlayerRequest(
    val moderated: String,
    val type: String,
)

/** API operations needed to read and update per-player voice overrides. */
class PlayerModerationApi(private val client: HttpClient) {
    internal suspend fun getForTarget(targetUserId: String): List<PlayerModerationData> =
        client.get(PLAYER_MODERATIONS_PATH) {
            parameter("targetUserId", targetUserId)
        }.checkSuccess()

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
}
