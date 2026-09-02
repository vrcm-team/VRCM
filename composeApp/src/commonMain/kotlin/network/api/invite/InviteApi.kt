package io.github.vrcmteam.vrcm.network.api.invite

import io.github.vrcmteam.vrcm.network.api.attributes.INVITE_API_PREFIX
import io.github.vrcmteam.vrcm.network.api.attributes.REQUEST_INVITE_API_PREFIX
import io.github.vrcmteam.vrcm.network.api.attributes.VRChatResponse
import io.github.vrcmteam.vrcm.network.api.invite.data.InviteMessageData
import io.github.vrcmteam.vrcm.network.api.invite.data.InviteMessageType
import io.github.vrcmteam.vrcm.network.api.invite.data.InviteMyselfData
import io.github.vrcmteam.vrcm.network.api.invite.data.RequestInviteRequest
import io.github.vrcmteam.vrcm.network.api.invite.data.UpdateInviteMessageRequest
import io.github.vrcmteam.vrcm.network.extensions.checkSuccess
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.http.ContentType
import io.ktor.http.contentType

class InviteApi(private val client: HttpClient) {

    suspend fun inviteUser (userId: String, instanceId: String, messageSlot: Int = 0): VRChatResponse =
         client.post("$INVITE_API_PREFIX/$userId"){
            setBody(mapOf("instanceId" to instanceId,"messageSlot" to messageSlot))
    }.checkSuccess()


    suspend fun inviteMyselfToInstance(instanceId: String): InviteMyselfData =
        client.post("$INVITE_API_PREFIX/myself/to/$instanceId")
            .checkSuccess()

    suspend fun requestInvite(userId: String, requestSlot: Int = 0) {
        client.post("$REQUEST_INVITE_API_PREFIX/$userId") {
            contentType(ContentType.Application.Json)
            setBody(RequestInviteRequest(requestSlot = requestSlot))
        }.checkSuccess { Unit }
    }

    suspend fun getInviteMessages(
        userId: String,
        messageType: InviteMessageType,
    ): List<InviteMessageData> {
        requireValidUserId(userId)
        return client.get("message/$userId/${messageType.pathValue}").checkSuccess()
    }

    suspend fun updateInviteMessage(
        userId: String,
        messageType: InviteMessageType,
        slot: Int,
        message: String,
    ): List<InviteMessageData> {
        requireValidUserId(userId)
        requireValidSlot(slot)
        require(message.isNotBlank()) { "Invite message must not be blank" }
        require(message.inviteMessageCodePointCount() <= MAX_INVITE_MESSAGE_CODE_POINTS) {
            "Invite message must not exceed $MAX_INVITE_MESSAGE_CODE_POINTS characters"
        }
        return client.put("message/$userId/${messageType.pathValue}/$slot") {
            contentType(ContentType.Application.Json)
            setBody(UpdateInviteMessageRequest(message))
        }.checkSuccess()
    }

    suspend fun resetInviteMessage(
        userId: String,
        messageType: InviteMessageType,
        slot: Int,
    ): List<InviteMessageData> {
        requireValidUserId(userId)
        requireValidSlot(slot)
        return client.delete("message/$userId/${messageType.pathValue}/$slot").checkSuccess()
    }

    private fun requireValidUserId(userId: String) {
        require(ID_PATTERN.matches(userId)) { "Invalid user ID" }
    }

    private fun requireValidSlot(slot: Int) {
        require(slot in INVITE_MESSAGE_SLOT_RANGE) { "Invalid invite message slot" }
    }

    companion object {
        const val MAX_INVITE_MESSAGE_CODE_POINTS = 64
        private val ID_PATTERN = Regex("[A-Za-z0-9_-]+")
        private val INVITE_MESSAGE_SLOT_RANGE = 0..11
    }

}

internal fun String.inviteMessageCodePointCount(): Int {
    var index = 0
    var count = 0
    while (index < length) {
        val current = this[index]
        val isSurrogatePair = current in '\uD800'..'\uDBFF' &&
            index + 1 < length && this[index + 1] in '\uDC00'..'\uDFFF'
        index += if (isSurrogatePair) 2 else 1
        count++
    }
    return count
}
