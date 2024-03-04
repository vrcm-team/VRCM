package io.github.vrcmteam.vrcm.network.api.instance.data

import io.github.vrcmteam.vrcm.network.api.attributes.AccessType
import io.github.vrcmteam.vrcm.network.api.attributes.IAccessType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class InstanceData(
    val active: Boolean,
    val canRequestInvite: Boolean,
    val capacity: Int,
    val clientNumber: String,
    val closedAt: String? = null,
    val displayName: String? = null,
    val full: Boolean,
    val gameServerVersion: Int,
    val hardClose: String? = null,
    val hasCapacityForYou: Boolean,
    val hidden: String?,
    val id: String,
    override val instanceId: String,
    val location: String,
    @SerialName("n_users")
    val nUsers: Int,
    val name: String,
    val ownerId: String?,
    val permanent: Boolean,
    val photonRegion: String,
    val platforms: Platforms,
    val queueEnabled: Boolean,
    val queueSize: Int,
    val recommendedCapacity: Int,
    val region: String,
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