package io.github.vrcmteam.vrcm.network.api.playermoderation

import io.github.vrcmteam.vrcm.network.api.attributes.AUTH_API_PREFIX
import io.github.vrcmteam.vrcm.network.api.attributes.USER_API_PREFIX
import io.github.vrcmteam.vrcm.network.extensions.checkSuccess
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import kotlinx.serialization.Serializable

private const val PLAYER_MODERATIONS_PATH = "$AUTH_API_PREFIX/$USER_API_PREFIX/playermoderations"

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

/** Read-only access to the current account's player management records. */
class PlayerModerationApi(private val client: HttpClient) {
    internal suspend fun getAll(): List<PlayerModerationData> =
        client.get(PLAYER_MODERATIONS_PATH).checkSuccess()
}
