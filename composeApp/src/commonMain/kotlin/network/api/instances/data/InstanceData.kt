package io.github.vrcmteam.vrcm.network.api.instances.data

import io.github.vrcmteam.vrcm.network.api.attributes.AccessType
import io.github.vrcmteam.vrcm.network.api.attributes.IAccessType
import io.github.vrcmteam.vrcm.network.api.attributes.RegionType
import io.github.vrcmteam.vrcm.network.api.worlds.data.WorldData
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class InstanceData(
    // 协议里这几个字段是可选的（hidden 只有 Friends+ 房间才带、ownerId 只有群组/个人房间才带），
    // 给默认值，少一个键不要把整份实例列表变成解析失败
    val active: Boolean = true,
    val canRequestInvite: Boolean = true,
    val capacity: Int = 0,
    val clientNumber: String,
    val closedAt: String? = null,
    val displayName: String? = null,
    val full: Boolean,
    val gameServerVersion: Int? = null,
    val hardClose: Boolean? = null,
    val hasCapacityForYou: Boolean? = null,
    val hidden: String? = null,
    val id: String,
    override val instanceId: String,
    val location: String,
    @SerialName("n_users")
    val nUsers: Int,
    val name: String,
    val ownerId: String? = null,
    val permanent: Boolean,
    val photonRegion: String,
    val platforms: Platforms,
    val queueEnabled: Boolean,
    val queueSize: Int,
    val recommendedCapacity: Int,
    val region: RegionType,
    val secureName: String,
    val shortName: String? = null,
    val strict: Boolean,
    val tags: List<String>,
    val type: String,
    val userCount: Int,
    val world: WorldData,
    val worldId: String
) : IAccessType {
    override val accessType: AccessType
            get() = when (type) {
                AccessType.Group.value -> {
                    when (instanceId.substringAfter("groupAccessType(").substringBefore(")")) {
                        AccessType.GroupPublic.value -> AccessType.GroupPublic
                        AccessType.GroupPlus.value -> AccessType.GroupPlus
                        AccessType.GroupMembers.value -> AccessType.GroupMembers
                        else -> AccessType.Group
                    }
                }

                AccessType.Private.value -> {
                    if (canRequestInvite) AccessType.InvitePlus else AccessType.Invite
                }

                AccessType.FriendPlus.value -> AccessType.FriendPlus

                AccessType.Friend.value -> AccessType.Friend

                else -> AccessType.Public
            }
}