package io.github.vrcmteam.vrcm.network.api.playermoderation

import io.github.vrcmteam.vrcm.network.api.attributes.AUTH_API_PREFIX
import io.github.vrcmteam.vrcm.network.api.attributes.USER_API_PREFIX
import io.github.vrcmteam.vrcm.network.api.playermoderation.data.PlayerModerationData
import io.github.vrcmteam.vrcm.network.api.playermoderation.data.PlayerModerationRequest
import io.github.vrcmteam.vrcm.network.api.playermoderation.data.PlayerModerationType
import io.github.vrcmteam.vrcm.network.extensions.checkSuccess
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.path

/** Reads and removes current-account player moderation overrides. */
class PlayerModerationApi(private val client: HttpClient) {
    suspend fun get(type: PlayerModerationType? = null): List<PlayerModerationData> =
        client.get {
            url { path(AUTH_API_PREFIX, USER_API_PREFIX, "playermoderations") }
            type?.let { parameter("type", it.apiValue) }
        }.checkSuccess()

    suspend fun remove(targetUserId: String, type: PlayerModerationType) {
        require(USER_ID_PATTERN.matches(targetUserId)) { "Invalid target user ID" }

        client.put {
            url { path(AUTH_API_PREFIX, USER_API_PREFIX, "unplayermoderate") }
            contentType(ContentType.Application.Json)
            setBody(PlayerModerationRequest(moderated = targetUserId, type = type.apiValue))
        }.checkSuccess<Unit> { Unit }
    }

    private companion object {
        val USER_ID_PATTERN = Regex("usr_[A-Za-z0-9_-]+")
    }
}
