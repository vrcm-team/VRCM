package io.github.vrcmteam.vrcm.presentation.screens.world

import io.github.vrcmteam.vrcm.network.api.attributes.AccessType
import io.github.vrcmteam.vrcm.network.api.attributes.RegionType
import io.github.vrcmteam.vrcm.network.api.instances.data.MinimumAvatarPerformance
import io.github.vrcmteam.vrcm.presentation.screens.world.data.GroupInstancePermission
import io.github.vrcmteam.vrcm.presentation.screens.world.data.InstanceCreationDraft
import io.github.vrcmteam.vrcm.presentation.screens.world.data.InstanceCreationGroup
import io.github.vrcmteam.vrcm.presentation.screens.world.data.InstanceCreationRole
import io.github.vrcmteam.vrcm.presentation.screens.world.data.InstanceCreationValidationError
import io.github.vrcmteam.vrcm.presentation.screens.world.data.validationError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class InstanceCreationPolicyTest {
    @Test
    fun eachGroupAccessTypeRequiresItsMatchingPermission() {
        val group = group(
            permissions = setOf(
                GroupInstancePermission.OpenCreate,
                GroupInstancePermission.PlusCreate,
            )
        )

        assertNull(draft(AccessType.GroupMembers).validationError(listOf(group)))
        assertNull(draft(AccessType.GroupPlus).validationError(listOf(group)))
        assertEquals(
            InstanceCreationValidationError.AccessPermissionRequired,
            draft(AccessType.GroupPublic).validationError(listOf(group)),
        )
    }

    @Test
    fun restrictedMembersRequirePermissionAndOnlyServerRolesAreAccepted() {
        val restrictedGroup = group(
            permissions = setOf(GroupInstancePermission.RestrictedCreate),
            roles = listOf(InstanceCreationRole("grol_staff", "Staff")),
        )

        assertEquals(
            InstanceCreationValidationError.RoleRequired,
            draft(AccessType.GroupMembers).validationError(listOf(restrictedGroup)),
        )
        assertNull(
            draft(AccessType.GroupMembers, roleIds = listOf("grol_staff"))
                .validationError(listOf(restrictedGroup))
        )
        assertEquals(
            InstanceCreationValidationError.InvalidRole,
            draft(AccessType.GroupMembers, roleIds = listOf("grol_removed"))
                .validationError(listOf(restrictedGroup)),
        )
    }

    @Test
    fun ageGateAndPerformanceOverrideUseIndependentPermissions() {
        val group = group(permissions = setOf(GroupInstancePermission.OpenCreate))

        assertEquals(
            InstanceCreationValidationError.AgeGatePermissionRequired,
            draft(AccessType.GroupMembers, ageGate = true).validationError(listOf(group)),
        )
        assertEquals(
            InstanceCreationValidationError.PerformancePermissionRequired,
            draft(
                AccessType.GroupMembers,
                minimumPerformance = MinimumAvatarPerformance.Good,
            ).validationError(listOf(group)),
        )

        val wildcardGroup = group(permissions = setOf(GroupInstancePermission.All))
        assertNull(
            draft(
                AccessType.GroupPublic,
                ageGate = true,
                minimumPerformance = MinimumAvatarPerformance.Poor,
            ).validationError(listOf(wildcardGroup))
        )
    }

    private fun group(
        permissions: Set<String>,
        roles: List<InstanceCreationRole> = emptyList(),
    ) = InstanceCreationGroup(
        id = "grp_test",
        name = "Test Group",
        permissions = permissions,
        roles = roles,
    )

    private fun draft(
        accessType: AccessType,
        roleIds: List<String> = emptyList(),
        ageGate: Boolean = false,
        minimumPerformance: MinimumAvatarPerformance? = null,
    ) = InstanceCreationDraft(
        accessType = accessType,
        region = RegionType.Us,
        groupId = "grp_test",
        roleIds = roleIds,
        ageGate = ageGate,
        minimumAvatarPerformance = minimumPerformance,
    )
}
