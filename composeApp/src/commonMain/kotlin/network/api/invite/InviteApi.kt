package io.github.vrcmteam.vrcm.network.api.invite

import io.github.vrcmteam.vrcm.network.api.attributes.INVITE_API_PREFIX
import io.github.vrcmteam.vrcm.network.api.attributes.VRChatResponse
import io.github.vrcmteam.vrcm.network.api.invite.data.InviteMyselfData
import io.github.vrcmteam.vrcm.network.api.invite.data.InviteResponseRequest
import io.github.vrcmteam.vrcm.network.extensions.checkSuccess
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class InviteApi(private val client: HttpClient) {

    suspend fun inviteUser (userId: String, instanceId: String, messageSlot: Int = 0): VRChatResponse =
         client.post("$INVITE_API_PREFIX/$userId"){
            setBody(mapOf("instanceId" to instanceId,"messageSlot" to messageSlot))
    }.checkSuccess()


    suspend fun inviteMyselfToInstance(instanceId: String): InviteMyselfData =
        client.post("$INVITE_API_PREFIX/myself/to/$instanceId")
            .checkSuccess()

    /** Responds to an invite or request-invite notification with a PNG from Gallery. */
    suspend fun respondInviteWithPhoto(
        notificationId: String,
        responseSlot: Int,
        imageBytes: ByteArray,
    ): String {
        require(notificationId.isNotBlank()) { "notificationId must not be blank" }
        require(responseSlot in INVITE_RESPONSE_SLOT_RANGE) {
            "responseSlot must be between 0 and 11"
        }
        require(imageBytes.hasPngSignature()) { "Invite response image must be a PNG file" }

        return client.submitFormWithBinaryData(
            url = "$INVITE_API_PREFIX/$notificationId/response/photo",
            formData = formData {
                append(
                    key = "data",
                    value = Json.encodeToString(InviteResponseRequest(responseSlot)),
                    headers = Headers.build {
                        append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    },
                )
                append(
                    key = "image",
                    value = imageBytes,
                    headers = Headers.build {
                        append(HttpHeaders.ContentType, ContentType.Image.PNG.toString())
                        append(HttpHeaders.ContentDisposition, "filename=\"image.png\"")
                    },
                )
            },
        ).checkSuccess { bodyAsText() }
    }

}

private fun ByteArray.hasPngSignature(): Boolean = size >= PNG_SIGNATURE.size &&
        PNG_SIGNATURE.indices.all { index -> this[index] == PNG_SIGNATURE[index] }

private val INVITE_RESPONSE_SLOT_RANGE = 0..11

private val PNG_SIGNATURE = byteArrayOf(
    0x89.toByte(),
    0x50,
    0x4E,
    0x47,
    0x0D,
    0x0A,
    0x1A,
    0x0A,
)
