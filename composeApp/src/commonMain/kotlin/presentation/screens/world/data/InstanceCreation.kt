package io.github.vrcmteam.vrcm.presentation.screens.world.data

import io.github.vrcmteam.vrcm.network.api.attributes.AccessType
import io.github.vrcmteam.vrcm.network.api.attributes.RegionType
import io.github.vrcmteam.vrcm.network.api.instances.data.MinimumAvatarPerformance

internal object GroupInstancePermission {
    const val All = "*"
    const val OpenCreate = "group-instance-open-create"
    const val RestrictedCreate = "group-instance-restricted-create"
    const val PlusCreate = "group-instance-plus-create"
    const val PublicCreate = "group-instance-public-create"
    const val AgeGatedCreate = "group-instance-age-gated-create"
    const val BypassAvatarPerformance = "group-instance-bypass-avatar-performance"
}

internal data class InstanceCreationRole(
    val id: String,
    val name: String,
)

internal data class InstanceCreationGroup(
    val id: String,
    val name: String,
    val permissions: Set<String>,
    val roles: List<InstanceCreationRole>,
) {
    fun hasPermission(permission: String): Boolean =
        GroupInstancePermission.All in permissions || permission in permissions

    fun canCreate(accessType: AccessType, restricted: Boolean = false): Boolean = when (accessType) {
        AccessType.GroupMembers -> hasPermission(
            if (restricted) GroupInstancePermission.RestrictedCreate
            else GroupInstancePermission.OpenCreate
        )
        AccessType.GroupPlus -> hasPermission(GroupInstancePermission.PlusCreate)
        AccessType.GroupPublic -> hasPermission(GroupInstancePermission.PublicCreate)
        else -> false
    }

    val canCreateAny: Boolean
        get() = GROUP_ACCESS_TYPES.any { canCreate(it) } ||
            hasPermission(GroupInstancePermission.RestrictedCreate)
}

internal data class InstanceCreationDraft(
    val accessType: AccessType,
    val region: RegionType,
    val queueEnabled: Boolean = false,
    val groupId: String? = null,
    val groupName: String? = null,
    val roleIds: List<String> = emptyList(),
    val ageGate: Boolean = false,
    val displayName: String? = null,
    val minimumAvatarPerformance: MinimumAvatarPerformance? = null,
)

internal sealed interface InstanceCreationGroupsState {
    data object Idle : InstanceCreationGroupsState
    data object Loading : InstanceCreationGroupsState
    data class Ready(val groups: List<InstanceCreationGroup>) : InstanceCreationGroupsState
    data object Failed : InstanceCreationGroupsState
}

internal sealed interface InstanceCreationSubmissionState {
    data object Idle : InstanceCreationSubmissionState
    data object Submitting : InstanceCreationSubmissionState
    data object Created : InstanceCreationSubmissionState
    data object Failed : InstanceCreationSubmissionState
}

internal enum class InstanceCreationValidationError {
    GroupRequired,
    AccessPermissionRequired,
    RoleRequired,
    InvalidRole,
    AgeGatePermissionRequired,
    PerformancePermissionRequired,
}

internal fun InstanceCreationDraft.validationError(
    groups: List<InstanceCreationGroup>,
): InstanceCreationValidationError? {
    if (accessType !in GROUP_ACCESS_TYPES) return null
    val group = groups.firstOrNull { it.id == groupId }
        ?: return InstanceCreationValidationError.GroupRequired
    val distinctRoleIds = roleIds.toSet()
    if (distinctRoleIds.any { roleId -> group.roles.none { it.id == roleId } }) {
        return InstanceCreationValidationError.InvalidRole
    }

    if (accessType == AccessType.GroupMembers && distinctRoleIds.isEmpty() &&
        !group.canCreate(AccessType.GroupMembers) &&
        group.hasPermission(GroupInstancePermission.RestrictedCreate)
    ) {
        return InstanceCreationValidationError.RoleRequired
    }
    if (!group.canCreate(accessType, restricted = distinctRoleIds.isNotEmpty())) {
        return InstanceCreationValidationError.AccessPermissionRequired
    }
    if (ageGate && !group.hasPermission(GroupInstancePermission.AgeGatedCreate)) {
        return InstanceCreationValidationError.AgeGatePermissionRequired
    }
    if (minimumAvatarPerformance != null &&
        !group.hasPermission(GroupInstancePermission.BypassAvatarPerformance)
    ) {
        return InstanceCreationValidationError.PerformancePermissionRequired
    }
    return null
}

internal val GROUP_ACCESS_TYPES = listOf(
    AccessType.GroupMembers,
    AccessType.GroupPlus,
    AccessType.GroupPublic,
)
