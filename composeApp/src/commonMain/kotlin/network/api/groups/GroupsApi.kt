package io.github.vrcmteam.vrcm.network.api.groups

import io.github.vrcmteam.vrcm.network.api.attributes.GROUPS_API_PREFIX
import io.github.vrcmteam.vrcm.network.api.groups.data.GroupData
import io.github.vrcmteam.vrcm.network.extensions.checkSuccess
import io.ktor.client.*
import io.ktor.client.request.*

class GroupsApi(private val client: HttpClient) {

    suspend fun fetchGroup(groupId: String, includeRoles: Boolean = false, purpose: String = "other") =
        client.get("$GROUPS_API_PREFIX/$groupId?includeRoles=$includeRoles")
            .checkSuccess<GroupData>()

}