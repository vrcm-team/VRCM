package io.github.vrcmteam.vrcm.presentation.screens.world.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.vrcmteam.vrcm.network.api.attributes.AccessType
import io.github.vrcmteam.vrcm.network.api.attributes.RegionType
import io.github.vrcmteam.vrcm.network.api.instances.data.MinimumAvatarPerformance
import io.github.vrcmteam.vrcm.presentation.compoments.RegionIcon
import io.github.vrcmteam.vrcm.presentation.screens.world.data.GROUP_ACCESS_TYPES
import io.github.vrcmteam.vrcm.presentation.screens.world.data.GroupInstancePermission
import io.github.vrcmteam.vrcm.presentation.screens.world.data.InstanceCreationDraft
import io.github.vrcmteam.vrcm.presentation.screens.world.data.InstanceCreationGroup
import io.github.vrcmteam.vrcm.presentation.screens.world.data.InstanceCreationGroupsState
import io.github.vrcmteam.vrcm.presentation.screens.world.data.InstanceCreationSubmissionState
import io.github.vrcmteam.vrcm.presentation.screens.world.data.InstanceCreationValidationError
import io.github.vrcmteam.vrcm.presentation.screens.world.data.validationError
import io.github.vrcmteam.vrcm.presentation.settings.locale.LocaleStrings
import io.github.vrcmteam.vrcm.presentation.settings.locale.strings

internal class CreateInstanceDialog(
    private val groupsState: InstanceCreationGroupsState,
    private val submissionState: InstanceCreationSubmissionState,
    private val onDismiss: () -> Unit = {},
    private val onRetryGroups: () -> Unit,
    private val onConfirm: (InstanceCreationDraft) -> Unit,
) {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun Content() {
        var selectedAccessType by remember { mutableStateOf(AccessType.FriendPlus) }
        var selectedRegion by remember { mutableStateOf(RegionType.Us) }
        var groupId by remember { mutableStateOf("") }
        var roleIds by remember { mutableStateOf(emptySet<String>()) }
        var queueEnabled by remember { mutableStateOf(false) }
        var ageGate by remember { mutableStateOf(false) }
        var displayName by remember { mutableStateOf("") }
        var minimumPerformance by remember {
            mutableStateOf<MinimumAvatarPerformance?>(null)
        }
        val groups = (groupsState as? InstanceCreationGroupsState.Ready)?.groups.orEmpty()
        val selectedGroup = groups.firstOrNull { it.id == groupId }
        val isGroupType = selectedAccessType in GROUP_ACCESS_TYPES
        val isSubmitting = submissionState == InstanceCreationSubmissionState.Submitting

        LaunchedEffect(groups) {
            if (groupId !in groups.map { it.id }) {
                groupId = groups.firstOrNull()?.id.orEmpty()
                roleIds = emptySet()
            }
        }
        LaunchedEffect(selectedAccessType) {
            if (selectedAccessType != AccessType.GroupMembers) roleIds = emptySet()
            if (!isGroupType) {
                queueEnabled = false
                ageGate = false
                minimumPerformance = null
            }
        }
        LaunchedEffect(selectedGroup) {
            roleIds = roleIds.filterTo(mutableSetOf()) { roleId ->
                selectedGroup?.roles?.any { it.id == roleId } == true
            }
            if (selectedGroup?.hasPermission(GroupInstancePermission.AgeGatedCreate) != true) {
                ageGate = false
            }
            if (selectedGroup?.hasPermission(
                    GroupInstancePermission.BypassAvatarPerformance
                ) != true
            ) {
                minimumPerformance = null
            }
        }

        val draft = InstanceCreationDraft(
            accessType = selectedAccessType,
            region = selectedRegion,
            queueEnabled = queueEnabled,
            groupId = groupId.takeIf { isGroupType },
            groupName = selectedGroup?.name,
            roleIds = roleIds.toList(),
            ageGate = ageGate,
            displayName = displayName,
            minimumAvatarPerformance = minimumPerformance,
        )
        val validationError = draft.validationError(groups)

        BasicAlertDialog(
            onDismissRequest = { if (!isSubmitting) onDismiss() },
            modifier = Modifier.fillMaxWidth(0.92f).widthIn(max = 560.dp).heightIn(max = 720.dp),
        ) {
            Surface(shape = RoundedCornerShape(8.dp)) {
                Column(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = strings.createInstance,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(12.dp))

                    Column(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        SectionLabel(strings.createInstanceStandardAccessType)
                        STANDARD_ACCESS_TYPES.forEach { accessType ->
                            AccessTypeItem(
                                label = accessType.localizedName(strings),
                                isSelected = accessType == selectedAccessType,
                                enabled = !isSubmitting,
                                onClick = { selectedAccessType = accessType },
                            )
                        }

                        SectionLabel(strings.createInstanceGroupAccessType)
                        GROUP_ACCESS_TYPES.forEach { accessType ->
                            AccessTypeItem(
                                label = accessType.localizedName(strings),
                                isSelected = accessType == selectedAccessType,
                                enabled = groups.isNotEmpty() && !isSubmitting,
                                onClick = { selectedAccessType = accessType },
                            )
                        }

                        if (isGroupType) {
                            GroupFields(
                                groupsState = groupsState,
                                selectedGroup = selectedGroup,
                                groupId = groupId,
                                roleIds = roleIds,
                                queueEnabled = queueEnabled,
                                ageGate = ageGate,
                                minimumPerformance = minimumPerformance,
                                selectedAccessType = selectedAccessType,
                                enabled = !isSubmitting,
                                onGroupSelected = {
                                    groupId = it
                                    roleIds = emptySet()
                                },
                                onRoleIdsChanged = { roleIds = it },
                                onQueueEnabledChanged = { queueEnabled = it },
                                onAgeGateChanged = { ageGate = it },
                                onMinimumPerformanceChanged = { minimumPerformance = it },
                                onRetryGroups = onRetryGroups,
                            )
                        }

                        SectionLabel(strings.regionType)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                        ) {
                            RegionType.entries.filter { it != RegionType.Unknown }.forEach { region ->
                                RegionItem(
                                    region = region,
                                    isSelected = region == selectedRegion,
                                    enabled = !isSubmitting,
                                    onClick = { selectedRegion = region },
                                )
                            }
                        }

                        OutlinedTextField(
                            value = displayName,
                            onValueChange = { displayName = it },
                            label = { Text(strings.createInstanceDisplayName) },
                            supportingText = { Text(strings.createInstanceOptional) },
                            enabled = !isSubmitting,
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )

                        validationError?.let {
                            Text(
                                text = it.localizedMessage(strings),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        if (submissionState == InstanceCreationSubmissionState.Failed) {
                            Text(
                                text = strings.instanceCreateFailed,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            enabled = !isSubmitting,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(strings.cancel)
                        }
                        Button(
                            onClick = { onConfirm(draft) },
                            enabled = validationError == null && !isSubmitting,
                            modifier = Modifier.weight(1f),
                        ) {
                            if (isSubmitting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                )
                                Spacer(Modifier.size(8.dp))
                                Text(strings.createInstanceSubmitting)
                            } else {
                                Text(strings.confirm)
                            }
                        }
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun GroupFields(
        groupsState: InstanceCreationGroupsState,
        selectedGroup: InstanceCreationGroup?,
        groupId: String,
        roleIds: Set<String>,
        queueEnabled: Boolean,
        ageGate: Boolean,
        minimumPerformance: MinimumAvatarPerformance?,
        selectedAccessType: AccessType,
        enabled: Boolean,
        onGroupSelected: (String) -> Unit,
        onRoleIdsChanged: (Set<String>) -> Unit,
        onQueueEnabledChanged: (Boolean) -> Unit,
        onAgeGateChanged: (Boolean) -> Unit,
        onMinimumPerformanceChanged: (MinimumAvatarPerformance?) -> Unit,
        onRetryGroups: () -> Unit,
    ) {
        when (groupsState) {
            InstanceCreationGroupsState.Idle,
            InstanceCreationGroupsState.Loading -> Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Text(strings.createInstanceGroupsLoading)
            }

            InstanceCreationGroupsState.Failed -> Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(strings.createInstanceGroupsFailed, modifier = Modifier.weight(1f))
                OutlinedButton(onClick = onRetryGroups) { Text(strings.retry) }
            }

            is InstanceCreationGroupsState.Ready -> {
                if (groupsState.groups.isEmpty()) {
                    Text(strings.createInstanceNoEligibleGroups)
                } else {
                    GroupDropdown(
                        groups = groupsState.groups,
                        selectedGroupId = groupId,
                        enabled = enabled,
                        onGroupSelected = onGroupSelected,
                    )
                }
            }
        }

        if (selectedGroup == null) return
        if (selectedAccessType == AccessType.GroupMembers &&
            selectedGroup.hasPermission(GroupInstancePermission.RestrictedCreate)
        ) {
            SectionLabel(strings.createInstanceRoleRestriction)
            selectedGroup.roles.forEach { role ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = enabled) {
                            onRoleIdsChanged(
                                if (role.id in roleIds) roleIds - role.id else roleIds + role.id
                            )
                        }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = role.id in roleIds,
                        onCheckedChange = null,
                        enabled = enabled,
                    )
                    Text(role.name)
                }
            }
        }

        ToggleRow(
            label = strings.createInstanceEnableQueue,
            checked = queueEnabled,
            enabled = enabled,
            onCheckedChange = onQueueEnabledChanged,
        )
        ToggleRow(
            label = strings.createInstanceAgeGate,
            checked = ageGate,
            enabled = enabled && selectedGroup.hasPermission(
                GroupInstancePermission.AgeGatedCreate
            ),
            onCheckedChange = onAgeGateChanged,
        )
        PerformanceDropdown(
            selected = minimumPerformance,
            enabled = enabled && selectedGroup.hasPermission(
                GroupInstancePermission.BypassAvatarPerformance
            ),
            onSelected = onMinimumPerformanceChanged,
        )
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun GroupDropdown(
        groups: List<InstanceCreationGroup>,
        selectedGroupId: String,
        enabled: Boolean,
        onGroupSelected: (String) -> Unit,
    ) {
        var expanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = {
            if (enabled) expanded = it
        }) {
            OutlinedTextField(
                value = groups.firstOrNull { it.id == selectedGroupId }?.name.orEmpty(),
                onValueChange = {},
                readOnly = true,
                enabled = enabled,
                label = { Text(strings.createInstanceGroup) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                groups.forEach { group ->
                    DropdownMenuItem(
                        text = { Text(group.name) },
                        onClick = {
                            onGroupSelected(group.id)
                            expanded = false
                        },
                    )
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun PerformanceDropdown(
        selected: MinimumAvatarPerformance?,
        enabled: Boolean,
        onSelected: (MinimumAvatarPerformance?) -> Unit,
    ) {
        var expanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = {
            if (enabled) expanded = it
        }) {
            OutlinedTextField(
                value = selected.localizedName(strings),
                onValueChange = {},
                readOnly = true,
                enabled = enabled,
                label = { Text(strings.createInstanceMinimumPerformance) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                (listOf<MinimumAvatarPerformance?>(null) + MinimumAvatarPerformance.entries).forEach {
                    performance ->
                    DropdownMenuItem(
                        text = { Text(performance.localizedName(strings)) },
                        onClick = {
                            onSelected(performance)
                            expanded = false
                        },
                    )
                }
            }
        }
    }

    @Composable
    private fun ToggleRow(
        label: String,
        checked: Boolean,
        enabled: Boolean,
        onCheckedChange: (Boolean) -> Unit,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f).padding(end = 8.dp),
            )
            Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
        }
    }

    @Composable
    private fun SectionLabel(text: String) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxWidth(),
        )
    }

    @Composable
    private fun AccessTypeItem(
        label: String,
        isSelected: Boolean,
        enabled: Boolean,
        onClick: () -> Unit,
    ) {
        val backgroundColor = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        }
        val textColor = if (isSelected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(backgroundColor)
                .clickable(enabled = enabled, onClick = onClick)
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = textColor.copy(alpha = if (enabled) 1f else 0.38f),
            )
        }
    }

    @Composable
    private fun RegionItem(
        region: RegionType,
        isSelected: Boolean,
        enabled: Boolean,
        onClick: () -> Unit,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .padding(4.dp)
                .clip(RoundedCornerShape(8.dp))
                .border(
                    width = if (isSelected) 2.dp else 1.dp,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                    shape = RoundedCornerShape(8.dp),
                )
                .clickable(enabled = enabled, onClick = onClick)
                .padding(8.dp),
        ) {
            RegionIcon(region = region, modifier = Modifier.size(36.dp))
            Spacer(Modifier.height(4.dp))
            Text(
                text = region.name,
                style = MaterialTheme.typography.labelMedium,
                color = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
        }
    }
}

private val STANDARD_ACCESS_TYPES = listOf(
    AccessType.Public,
    AccessType.FriendPlus,
    AccessType.Friend,
    AccessType.InvitePlus,
    AccessType.Invite,
)

private fun AccessType.localizedName(strings: LocaleStrings): String = when (this) {
    AccessType.Public -> strings.friendActivityAccessPublic
    AccessType.FriendPlus -> strings.friendActivityAccessFriendsPlus
    AccessType.Friend -> strings.friendActivityAccessFriends
    AccessType.InvitePlus -> strings.friendActivityAccessInvitePlus
    AccessType.Invite -> strings.friendActivityAccessInvite
    AccessType.GroupMembers -> strings.createInstanceGroupMembers
    AccessType.GroupPlus -> strings.createInstanceGroupPlus
    AccessType.GroupPublic -> strings.createInstanceGroupPublic
    AccessType.Group, AccessType.Private -> displayName
}

private fun MinimumAvatarPerformance?.localizedName(strings: LocaleStrings): String = when (this) {
    null -> strings.createInstancePerformanceDefault
    MinimumAvatarPerformance.Poor -> strings.createInstancePerformancePoor
    MinimumAvatarPerformance.Medium -> strings.createInstancePerformanceMedium
    MinimumAvatarPerformance.Good -> strings.createInstancePerformanceGood
}

private fun InstanceCreationValidationError.localizedMessage(strings: LocaleStrings): String = when (this) {
    InstanceCreationValidationError.GroupRequired -> strings.createInstanceGroupRequired
    InstanceCreationValidationError.AccessPermissionRequired ->
        strings.createInstanceAccessPermissionRequired
    InstanceCreationValidationError.RoleRequired -> strings.createInstanceRoleRequired
    InstanceCreationValidationError.InvalidRole -> strings.createInstanceInvalidRole
    InstanceCreationValidationError.AgeGatePermissionRequired ->
        strings.createInstanceAgeGatePermissionRequired
    InstanceCreationValidationError.PerformancePermissionRequired ->
        strings.createInstancePerformancePermissionRequired
}
