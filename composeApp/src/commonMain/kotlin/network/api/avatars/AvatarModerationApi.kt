package io.github.vrcmteam.vrcm.network.api.avatars

import io.github.vrcmteam.vrcm.network.api.avatars.data.AvatarModerationData
import io.github.vrcmteam.vrcm.network.api.avatars.data.AVATAR_BLOCK_MODERATION_TYPE
import io.github.vrcmteam.vrcm.network.api.avatars.data.CreateAvatarModerationRequest
import io.github.vrcmteam.vrcm.network.extensions.checkSuccess
import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

internal class AvatarModerationApi(private val client: HttpClient) {
    suspend fun getAvatarModerations(): List<AvatarModerationData> =
        client.get(AVATAR_MODERATIONS_PATH).checkSuccess()

    suspend fun blockAvatar(avatarId: String): AvatarModerationData =
        client.post(AVATAR_MODERATIONS_PATH) {
            contentType(ContentType.Application.Json)
            setBody(
                CreateAvatarModerationRequest(
                    avatarModerationType = AVATAR_BLOCK_MODERATION_TYPE,
                    targetAvatarId = avatarId,
                )
            )
        }.checkSuccess()

    suspend fun unblockAvatar(avatarId: String) {
        client.delete(AVATAR_MODERATIONS_PATH) {
            parameter("targetAvatarId", avatarId)
            parameter("avatarModerationType", AVATAR_BLOCK_MODERATION_TYPE)
        }.checkSuccess { Unit }
    }

    private companion object {
        const val AVATAR_MODERATIONS_PATH = "auth/user/avatarmoderations"
    }
}
