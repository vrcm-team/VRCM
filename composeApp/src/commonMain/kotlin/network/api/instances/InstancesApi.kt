package io.github.vrcmteam.vrcm.network.api.instances


import io.github.vrcmteam.vrcm.network.api.attributes.AccessType
import io.github.vrcmteam.vrcm.network.api.attributes.AccessType.*
import io.github.vrcmteam.vrcm.network.api.attributes.INSTANCES_API_SUFFIX
import io.github.vrcmteam.vrcm.network.api.attributes.RegionType
import io.github.vrcmteam.vrcm.network.api.instances.data.CreateInstanceRequest
import io.github.vrcmteam.vrcm.network.api.instances.data.InstanceData
import io.github.vrcmteam.vrcm.network.extensions.checkSuccess
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.http.*


class InstancesApi(private val client: HttpClient) {
    /**
     * 通过位置获取单个实例信息
     */
    suspend fun instanceByLocation(location: String): InstanceData {
        return client.get { url { path(INSTANCES_API_SUFFIX, location) } }
            .checkSuccess()
    }

    /**
     * 创建世界实例
     *
     * @param worldId 世界ID
     * @param accessType 访问权限类型
     * @param region 区域类型
     * @param userId 用户ID（非public和group类型实例需要）
     * @param queueEnabled 是否启用排队功能
     * @param groupId 群组ID（仅当创建群组实例时使用）
     * @param groupAccessType 群组访问权限类型（仅当创建群组实例时使用）
     * @param roleIds 允许加入的角色ID列表（仅当创建群组实例且groupAccessType为members时使用）
     * @return InstanceData 创建的实例数据
     */
    suspend fun createInstance(
        worldId: String,
        accessType: AccessType,
        region: RegionType,
        userId: String? = null,
        queueEnabled: Boolean = false,
        groupId: String? = null,
        groupAccessType: String? = null,
        roleIds: List<String>? = null
    ): InstanceData {
        // 将AccessType映射到API需要的instanceType
        val instanceType = when(accessType) {
            Public -> Public
            Friend -> Friend
            FriendPlus -> FriendPlus
            Private, Invite, InvitePlus -> Private
            Group, GroupMembers, GroupPlus, GroupPublic -> Group
        }

        // 构建请求对象
        val requestBody = CreateInstanceRequest(
            worldId = worldId,
            type = instanceType.value,
            region = region.name.lowercase(),
            queueEnabled = if (queueEnabled) true else null,
            canRequestInvite = if (accessType == InvitePlus) true else null,
            ownerId = when {
                instanceType == Group && groupId != null -> groupId
                instanceType == Public -> null
                else -> userId
            },
            groupAccessType = if (instanceType == Group && groupId != null) {
                val gAccessType = when(accessType) {
                    GroupPublic -> GroupPublic.value
                    GroupPlus -> GroupPlus.value
                    GroupMembers -> GroupMembers.value
                    else -> groupAccessType ?: GroupMembers.value
                }
                gAccessType
            } else null,
            roleIds = if (instanceType == Group &&
                (accessType == GroupMembers ||
                        (groupAccessType == GroupMembers.value && !roleIds.isNullOrEmpty()))) {
                roleIds
            } else null
        )

        return client.post(INSTANCES_API_SUFFIX) {
            contentType(ContentType.Application.Json)
            setBody(requestBody)
        }.checkSuccess()
    }
}
