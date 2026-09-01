package io.github.vrcmteam.vrcm.network.api.invite

import io.github.vrcmteam.vrcm.network.api.attributes.INVITE_API_PREFIX
import io.github.vrcmteam.vrcm.network.api.attributes.REQUEST_INVITE_API_PREFIX
import io.github.vrcmteam.vrcm.network.api.attributes.VRChatResponse
import io.github.vrcmteam.vrcm.network.api.invite.data.InviteMessageData
import io.github.vrcmteam.vrcm.network.api.invite.data.InviteMessageType
import io.github.vrcmteam.vrcm.network.api.invite.data.InviteMyselfData
import io.github.vrcmteam.vrcm.network.api.invite.data.InviteUserRequest
import io.github.vrcmteam.vrcm.network.api.invite.data.RequestInviteRequest
import io.github.vrcmteam.vrcm.network.extensions.checkSuccess
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.http.ContentType
import io.ktor.http.contentType

class InviteApi(private val client: HttpClient) {

    suspend fun inviteUser(userId: String, instanceId: String, messageSlot: Int = 0): VRChatResponse {
        requireValidSlot(messageSlot)
        return client.post("$INVITE_API_PREFIX/$userId") {
            contentType(ContentType.Application.Json)
            setBody(InviteUserRequest(instanceId = instanceId, messageSlot = messageSlot))
        }.checkSuccess()
    }


    suspend fun inviteMyselfToInstance(instanceId: String): InviteMyselfData =
        client.post("$INVITE_API_PREFIX/myself/to/$instanceId")
            .checkSuccess()

    suspend fun requestInvite(userId: String, requestSlot: Int = 0) {
        requireValidSlot(requestSlot)
        client.post("$REQUEST_INVITE_API_PREFIX/$userId") {
            contentType(ContentType.Application.Json)
            setBody(RequestInviteRequest(requestSlot))
        }.checkSuccess { Unit }
    }

    suspend fun getInviteMessages(
        userId: String,
        messageType: InviteMessageType,
    ): List<InviteMessageData> =
        client.get("message/$userId/${messageType.pathValue}").checkSuccess()

    private fun requireValidSlot(slot: Int) {
        require(slot in INVITE_MESSAGE_SLOT_RANGE) { "Invalid invite message slot" }
    }

    private companion object {
        val INVITE_MESSAGE_SLOT_RANGE = 0..11
    }

}
