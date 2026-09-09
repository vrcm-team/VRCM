package io.github.vrcmteam.vrcm.network.api.instances.data

import io.github.vrcmteam.vrcm.network.api.attributes.AccessType
import io.github.vrcmteam.vrcm.network.api.attributes.RegionType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Minimum avatar performance accepted by a group instance. */
@Serializable
enum class MinimumAvatarPerformance {
    @SerialName("Poor")
    Poor,

    @SerialName("Medium")
    Medium,

    @SerialName("Good")
    Good,
}

/** User-selected values for a create-instance request. */
data class InstanceCreationOptions(
    val worldId: String,
    val accessType: AccessType,
    val region: RegionType,
    val userId: String? = null,
    val queueEnabled: Boolean = false,
    val groupId: String? = null,
    val roleIds: List<String> = emptyList(),
    val ageGate: Boolean = false,
    val displayName: String? = null,
    val minimumAvatarPerformance: MinimumAvatarPerformance? = null,
)

@Serializable
internal data class CreateInstanceRequest(
    val worldId: String,
    val type: String,
    val region: String,
    val queueEnabled: Boolean? = null,
    val canRequestInvite: Boolean? = null,
    val ownerId: String? = null,
    val groupAccessType: String? = null,
    val roleIds: List<String>? = null,
    val ageGate: Boolean? = null,
    val displayName: String? = null,
    val minimumAvatarPerformance: MinimumAvatarPerformance? = null,
)

internal fun InstanceCreationOptions.toCreateInstanceRequest(): CreateInstanceRequest {
    val normalizedWorldId = worldId.trim()
    require(normalizedWorldId.startsWith("wrld_")) { "worldId must begin with wrld_" }
    require(region != RegionType.Unknown) { "A supported instance region is required" }

    val normalizedUserId = userId?.trim()?.takeIf(String::isNotEmpty)
    val normalizedGroupId = groupId?.trim()?.takeIf(String::isNotEmpty)
    val normalizedRoleIds = roleIds
        .map(String::trim)
        .filter(String::isNotEmpty)
        .distinct()
    val normalizedDisplayName = displayName?.trim()?.takeIf(String::isNotEmpty)
    val isGroup = accessType in GROUP_ACCESS_TYPES

    if (isGroup) {
        require(normalizedGroupId?.startsWith("grp_") == true) {
            "Group instances require a groupId beginning with grp_"
        }
        require(normalizedRoleIds.all { it.startsWith("grol_") }) {
            "roleIds must begin with grol_"
        }
        require(accessType == AccessType.GroupMembers || normalizedRoleIds.isEmpty()) {
            "roleIds are only supported by Group Members instances"
        }
    } else {
        require(normalizedGroupId == null) { "groupId requires a group instance" }
        require(normalizedRoleIds.isEmpty()) { "roleIds require a group instance" }
        require(!queueEnabled) { "queueEnabled requires a group instance" }
        require(!ageGate) { "ageGate requires a group instance" }
        require(minimumAvatarPerformance == null) {
            "minimumAvatarPerformance requires a group instance"
        }
    }

    val instanceType = when (accessType) {
        AccessType.Public -> AccessType.Public.value
        AccessType.Friend -> AccessType.Friend.value
        AccessType.FriendPlus -> AccessType.FriendPlus.value
        AccessType.Private, AccessType.Invite, AccessType.InvitePlus -> AccessType.Private.value
        AccessType.GroupMembers, AccessType.GroupPlus, AccessType.GroupPublic -> AccessType.Group.value
        AccessType.Group -> error("A concrete group access type is required")
    }
    if (accessType != AccessType.Public && !isGroup) {
        require(normalizedUserId?.startsWith("usr_") == true) {
            "Private instances require a userId beginning with usr_"
        }
    }

    return CreateInstanceRequest(
        worldId = normalizedWorldId,
        type = instanceType,
        region = region.name.lowercase(),
        queueEnabled = queueEnabled.takeIf { isGroup && it },
        canRequestInvite = true.takeIf { accessType == AccessType.InvitePlus },
        ownerId = when {
            isGroup -> normalizedGroupId
            accessType == AccessType.Public -> null
            else -> normalizedUserId
        },
        groupAccessType = accessType.value.takeIf { isGroup },
        roleIds = normalizedRoleIds.takeIf { it.isNotEmpty() },
        ageGate = ageGate.takeIf { isGroup && it },
        displayName = normalizedDisplayName,
        minimumAvatarPerformance = minimumAvatarPerformance.takeIf { isGroup },
    )
}

private val GROUP_ACCESS_TYPES = setOf(
    AccessType.GroupMembers,
    AccessType.GroupPlus,
    AccessType.GroupPublic,
)

