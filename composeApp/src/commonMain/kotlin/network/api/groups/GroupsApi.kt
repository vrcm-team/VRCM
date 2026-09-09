package io.github.vrcmteam.vrcm.network.api.groups

import io.github.vrcmteam.vrcm.network.api.attributes.GROUPS_API_PREFIX
import io.github.vrcmteam.vrcm.network.api.attributes.USERS_API_PREFIX
import io.github.vrcmteam.vrcm.network.api.groups.data.GroupData
import io.github.vrcmteam.vrcm.network.api.groups.data.GroupGalleryImage
import io.github.vrcmteam.vrcm.network.api.groups.data.GroupInstancesResponse
import io.github.vrcmteam.vrcm.network.api.groups.data.GroupMember
import io.github.vrcmteam.vrcm.network.api.groups.data.GroupPostData
import io.github.vrcmteam.vrcm.network.api.groups.data.JoinGroupRequest
import io.github.vrcmteam.vrcm.network.api.groups.data.LimitedGroup
import io.github.vrcmteam.vrcm.network.api.groups.data.UpdateGroupRepresentationRequest
import io.github.vrcmteam.vrcm.network.extensions.checkSuccess
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.http.*

class GroupsApi(private val client: HttpClient) {

    suspend fun fetchGroup(groupId: String, includeRoles: Boolean = false, purpose: String = "other") =
        client.get("$GROUPS_API_PREFIX/$groupId") {
            parameter("includeRoles", includeRoles)
        }.checkSuccess<GroupData>()

    suspend fun searchGroups(
        query: String? = null,
        n: Int = 20,
        offset: Int = 0,
    ): List<LimitedGroup> =
        client.get(GROUPS_API_PREFIX) {
            query?.takeIf { it.isNotBlank() }?.let { parameter("query", it) }
            parameter("n", n)
            parameter("offset", offset)
        }.checkSuccess()

    suspend fun joinGroup(
        groupId: String,
        inviteId: String? = null,
        confirmOverrideBlock: Boolean? = null,
    ): GroupMember =
        client.post("$GROUPS_API_PREFIX/$groupId/join") {
            confirmOverrideBlock?.let { parameter("confirmOverrideBlock", it) }
            if (inviteId != null) {
                setBody(JoinGroupRequest(inviteId))
                contentType(ContentType.Application.Json)
            }
        }.checkSuccess()

    suspend fun leaveGroup(groupId: String) =
        client.post("$GROUPS_API_PREFIX/$groupId/leave")
            .checkSuccess { Unit }

    suspend fun updateRepresentation(groupId: String, isRepresenting: Boolean) =
        client.put("$GROUPS_API_PREFIX/$groupId/representation") {
            contentType(ContentType.Application.Json)
            setBody(UpdateGroupRepresentationRequest(isRepresenting))
        }.checkSuccess { Unit }

    suspend fun getGroupMembers(
        groupId: String,
        n: Int = 20,
        offset: Int = 0,
        sort: String? = null,
        roleId: String? = null,
    ): List<GroupMember> =
        client.get("$GROUPS_API_PREFIX/$groupId/members") {
            parameter("n", n)
            parameter("offset", offset)
            sort?.let { parameter("sort", it) }
            roleId?.let { parameter("roleId", it) }
        }.checkSuccess()

    suspend fun getGroupGalleryImages(
        groupId: String,
        groupGalleryId: String,
        n: Int = 20,
        offset: Int = 0,
        approved: Boolean? = null,
    ): List<GroupGalleryImage> =
        client.get("$GROUPS_API_PREFIX/$groupId/galleries/$groupGalleryId") {
            parameter("n", n)
            parameter("offset", offset)
            approved?.let { parameter("approved", it) }
        }.checkSuccess()

    suspend fun getGroupPosts(
        groupId: String,
        n: Int = 100,
        offset: Int = 0,
    ): GroupPostData =
        client.get("$GROUPS_API_PREFIX/$groupId/posts") {
            parameter("n", n)
            parameter("offset", offset)
        }.checkSuccess()

    suspend fun getGroupInstances(
        userId: String,
        groupId: String,
    ): GroupInstancesResponse =
        client.get("$USERS_API_PREFIX/$userId/instances/groups/$groupId")
            .checkSuccess()

}
