package io.github.vrcmteam.vrcm.network.api.invite

import io.github.vrcmteam.vrcm.network.api.attributes.INVITE_API_PREFIX
import io.github.vrcmteam.vrcm.network.api.attributes.VRChatResponse
import io.github.vrcmteam.vrcm.network.api.invite.data.InviteMyselfData
import io.github.vrcmteam.vrcm.network.extensions.checkSuccess
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.http.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class InviteApi(private val client: HttpClient) {

    suspend fun inviteUser (userId: String, instanceId: String, messageSlot: Int = 0): VRChatResponse =
         client.post("$INVITE_API_PREFIX/$userId"){
            setBody(mapOf("instanceId" to instanceId,"messageSlot" to messageSlot))
    }.checkSuccess()

    /** Sends a Gallery PNG as an invite attachment using VRChat's multipart contract. */
    suspend fun inviteUserWithPhoto(
        userId: String,
        instanceId: String,
        imageBytes: ByteArray,
        messageSlot: Int = 0,
    ) {
        require(userId.isNotBlank()) { "userId must not be blank" }
        require(instanceId.isNotBlank() && instanceId != "offline") {
            "instanceId must identify an active instance"
        }
        require(messageSlot in 0..11) { "messageSlot must be between 0 and 11" }
        require(imageBytes.hasPngSignature()) { "Invite image must be a PNG file" }
        require(imageBytes.size <= MAX_INVITE_IMAGE_BYTES) {
            "Invite image exceeds $MAX_INVITE_IMAGE_BYTES bytes"
        }

        client.submitFormWithBinaryData(
            url = "$INVITE_API_PREFIX/$userId/photo",
            formData = formData {
                append(
                    key = "data",
                    value = Json.encodeToString(
                        InvitePhotoRequest(
                            instanceId = instanceId,
                            messageSlot = messageSlot,
                        ),
                    ),
                    headers = Headers.build {
                        append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    },
                )
                append("image", imageBytes, Headers.build {
                    append(HttpHeaders.ContentType, ContentType.Image.PNG.toString())
                    append(HttpHeaders.ContentDisposition, "filename=\"image.png\"")
                })
            },
        ).checkSuccess { Unit }
    }


    suspend fun inviteMyselfToInstance(instanceId: String): InviteMyselfData =
        client.post("$INVITE_API_PREFIX/myself/to/$instanceId")
            .checkSuccess()

    private fun ByteArray.hasPngSignature(): Boolean = size >= PNG_SIGNATURE.size &&
            PNG_SIGNATURE.indices.all { index -> this[index] == PNG_SIGNATURE[index] }

    private companion object {
        const val MAX_INVITE_IMAGE_BYTES = 10_000_000
        val PNG_SIGNATURE = byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
        )
    }
}

@Serializable
private data class InvitePhotoRequest(
    val instanceId: String,
    val messageSlot: Int,
)
