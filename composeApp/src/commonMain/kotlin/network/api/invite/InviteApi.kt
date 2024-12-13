package io.github.vrcmteam.vrcm.network.api.invite

import io.github.vrcmteam.vrcm.network.api.attributes.INVITE_API_PREFIX
import io.github.vrcmteam.vrcm.network.api.attributes.VRChatResponse
import io.github.vrcmteam.vrcm.network.api.invite.data.InviteMyselfData
import io.github.vrcmteam.vrcm.network.extensions.checkSuccess
import io.ktor.client.*
import io.ktor.client.request.*

class InviteApi(private val client: HttpClient) {

    suspend fun inviteUser (userId: String, instanceId: String, messageSlot: Int = 0): VRChatResponse =
         client.post("$INVITE_API_PREFIX/$userId"){
            setBody(mapOf("instanceId" to instanceId,"messageSlot" to messageSlot))
    }.checkSuccess()


    suspend fun inviteMyselfToInstance(instanceId: String): InviteMyselfData =
        client.post("$INVITE_API_PREFIX/myself/to/$instanceId")
            .checkSuccess()

}