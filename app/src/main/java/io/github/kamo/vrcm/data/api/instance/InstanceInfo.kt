package io.github.kamo.vrcm.data.api.instance

import com.google.gson.annotations.SerializedName
import io.github.kamo.vrcm.data.api.AccessType

data class InstanceInfo(
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
    val instanceId: String,
    val location: String,
    @SerializedName("n_users")
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
    val world: WorldInfo,
    val worldId: String
) {
    val accessType: AccessType
        get() =
            when (type) {
                AccessType.Group.typeName -> {
                    when (instanceId.substringAfter("groupAccessType(").substringBefore(")")) {
                        AccessType.GroupPublic.typeName -> AccessType.GroupPublic
                        AccessType.GroupPlus.typeName -> AccessType.GroupPlus
                        AccessType.GroupMembers.typeName -> AccessType.Group
                        else -> AccessType.Group
                    }
                }

                AccessType.Private.typeName -> {
                    if (canRequestInvite) AccessType.InvitePlus else AccessType.Invite
                }

                AccessType.FriendPlus.typeName -> AccessType.FriendPlus

                AccessType.Friend.typeName -> AccessType.Friend

                else -> AccessType.Public
            }
}

