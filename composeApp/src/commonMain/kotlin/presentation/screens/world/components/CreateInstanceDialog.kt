package io.github.vrcmteam.vrcm.presentation.screens.world.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import io.github.vrcmteam.vrcm.network.api.attributes.AccessType
import io.github.vrcmteam.vrcm.network.api.attributes.RegionType
import io.github.vrcmteam.vrcm.network.api.instances.data.MinimumAvatarPerformance
import io.github.vrcmteam.vrcm.presentation.compoments.RegionIcon
import io.github.vrcmteam.vrcm.presentation.designsystem.AppActivityIndicator
import io.github.vrcmteam.vrcm.presentation.designsystem.AppButton
import io.github.vrcmteam.vrcm.presentation.designsystem.AppButtonStyle
import io.github.vrcmteam.vrcm.presentation.designsystem.AppCheckbox
import io.github.vrcmteam.vrcm.presentation.designsystem.AppDialogSurface
import io.github.vrcmteam.vrcm.presentation.designsystem.AppDivider
import io.github.vrcmteam.vrcm.presentation.designsystem.AppGroup
import io.github.vrcmteam.vrcm.presentation.designsystem.AppIcon
import io.github.vrcmteam.vrcm.presentation.designsystem.AppMenuItem
import io.github.vrcmteam.vrcm.presentation.designsystem.AppPopUpButton
import io.github.vrcmteam.vrcm.presentation.designsystem.AppRow
import io.github.vrcmteam.vrcm.presentation.designsystem.AppSectionHeader
import io.github.vrcmteam.vrcm.presentation.designsystem.AppShapes
import io.github.vrcmteam.vrcm.presentation.designsystem.AppSpacing
import io.github.vrcmteam.vrcm.presentation.designsystem.AppText
import io.github.vrcmteam.vrcm.presentation.designsystem.AppTextField
import io.github.vrcmteam.vrcm.presentation.designsystem.AppTheme
import io.github.vrcmteam.vrcm.presentation.designsystem.AppToggle
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
import io.github.vrcmteam.vrcm.presentation.supports.AppIcons

internal class CreateInstanceDialog(
    private val groupsState: InstanceCreationGroupsState,
    private val submissionState: InstanceCreationSubmissionState,
    private val onDismiss: () -> Unit = {},
    private val onRetryGroups: () -> Unit,
    private val onConfirm: (InstanceCreationDraft) -> Unit,
) {
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

        Dialog(onDismissRequest = { if (!isSubmitting) onDismiss() }) {
            AppDialogSurface(
                modifier = Modifier.fillMaxWidth(0.92f).widthIn(max = 560.dp).heightIn(max = 720.dp),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    AppText(
                        text = strings.createInstance,
                        style = AppTheme.type.title2,
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
                        AccessTypeGroup(
                            title = strings.createInstanceStandardAccessType,
                            accessTypes = STANDARD_ACCESS_TYPES,
                            selected = selectedAccessType,
                            enabled = !isSubmitting,
                            onSelect = { selectedAccessType = it },
                        )
                        AccessTypeGroup(
                            title = strings.createInstanceGroupAccessType,
                            accessTypes = GROUP_ACCESS_TYPES,
                            selected = selectedAccessType,
                            enabled = groups.isNotEmpty() && !isSubmitting,
                            onSelect = { selectedAccessType = it },
                        )

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

                        AppTextField(
                            value = displayName,
                            onValueChange = { displayName = it },
                            label = { AppText(strings.createInstanceDisplayName) },
                            supportingText = { AppText(strings.createInstanceOptional) },
                            enabled = !isSubmitting,
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )

                        validationError?.let {
                            AppText(
                                text = it.localizedMessage(strings),
                                color = AppTheme.colors.destructive,
                                style = AppTheme.type.caption1,
                            )
                        }
                        if (submissionState == InstanceCreationSubmissionState.Failed) {
                            AppText(
                                text = strings.instanceCreateFailed,
                                color = AppTheme.colors.destructive,
                                style = AppTheme.type.caption1,
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        AppButton(
                            onClick = onDismiss,
                            enabled = !isSubmitting,
                            modifier = Modifier.weight(1f),
                            style = AppButtonStyle.Gray,
                        ) {
                            AppText(strings.cancel)
                        }
                        AppButton(
                            onClick = { onConfirm(draft) },
                            enabled = validationError == null && !isSubmitting,
                            modifier = Modifier.weight(1f),
                            style = AppButtonStyle.Prominent,
                        ) {
                            if (isSubmitting) {
                                AppActivityIndicator(
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.size(8.dp))
                                AppText(strings.createInstanceSubmitting)
                            } else {
                                AppText(strings.confirm)
                            }
                        }
                    }
                }
            }
        }
    }

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
                AppActivityIndicator(Modifier.size(20.dp))
                AppText(strings.createInstanceGroupsLoading)
            }

            InstanceCreationGroupsState.Failed -> Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AppText(strings.createInstanceGroupsFailed, modifier = Modifier.weight(1f))
                AppButton(onClick = onRetryGroups, style = AppButtonStyle.Gray) { AppText(strings.retry) }
            }

            is InstanceCreationGroupsState.Ready -> {
                if (groupsState.groups.isEmpty()) {
                    AppText(strings.createInstanceNoEligibleGroups)
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
                    AppCheckbox(
                        checked = role.id in roleIds,
                        onCheckedChange = null,
                        enabled = enabled,
                    )
                    AppText(role.name)
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

        @Composable
    private fun GroupDropdown(
        groups: List<InstanceCreationGroup>,
        selectedGroupId: String,
        enabled: Boolean,
        onGroupSelected: (String) -> Unit,
    ) {
        var expanded by remember { mutableStateOf(false) }
        AppPopUpButton(
            value = groups.firstOrNull { it.id == selectedGroupId }?.name.orEmpty(),
            expanded = expanded,
            onExpandedChange = { if (enabled) expanded = it },
            modifier = Modifier.fillMaxWidth(),
            label = strings.createInstanceGroup,
            enabled = enabled,
        ) {
            groups.forEach { group ->
                AppMenuItem(
                    text = { AppText(group.name) },
                    onClick = {
                        onGroupSelected(group.id)
                        expanded = false
                    },
                )
            }
        }
    }

        @Composable
    private fun PerformanceDropdown(
        selected: MinimumAvatarPerformance?,
        enabled: Boolean,
        onSelected: (MinimumAvatarPerformance?) -> Unit,
    ) {
        var expanded by remember { mutableStateOf(false) }
        AppPopUpButton(
            value = selected.localizedName(strings),
            expanded = expanded,
            onExpandedChange = { if (enabled) expanded = it },
            modifier = Modifier.fillMaxWidth(),
            label = strings.createInstanceMinimumPerformance,
            enabled = enabled,
        ) {
            (listOf<MinimumAvatarPerformance?>(null) + MinimumAvatarPerformance.entries).forEach {
                performance ->
                AppMenuItem(
                    text = { AppText(performance.localizedName(strings)) },
                    onClick = {
                        onSelected(performance)
                        expanded = false
                    },
                )
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
            AppText(
                text = label,
                style = AppTheme.type.subheadline,
                modifier = Modifier.weight(1f).padding(end = 8.dp),
            )
            AppToggle(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
        }
    }

    @Composable
    private fun SectionLabel(text: String) {
        AppSectionHeader(text, modifier = Modifier.fillMaxWidth())
    }

    /** 访问类型是多选一：一张分组卡片，选中的那行尾部打勾。 */
    @Composable
    private fun AccessTypeGroup(
        title: String,
        accessTypes: List<AccessType>,
        selected: AccessType,
        enabled: Boolean,
        onSelect: (AccessType) -> Unit,
    ) {
        Column {
            SectionLabel(title)
            AppGroup {
                accessTypes.forEachIndexed { index, accessType ->
                    if (index > 0) AppDivider(Modifier.padding(start = AppSpacing.row))
                    AppRow(
                        title = accessType.localizedName(strings),
                        modifier = Modifier.semantics { this.selected = accessType == selected },
                        enabled = enabled,
                        onClick = { onSelect(accessType) },
                        trailing = {
                            if (accessType == selected) {
                                AppIcon(
                                    imageVector = AppIcons.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = AppTheme.colors.tint,
                                )
                            }
                        },
                    )
                }
            }
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
                .clip(AppShapes.s)
                .border(
                    width = if (isSelected) 2.dp else 1.dp,
                    color = if (isSelected) AppTheme.colors.tint else Color.Transparent,
                    shape = AppShapes.m,
                )
                .clickable(enabled = enabled, onClick = onClick)
                .padding(8.dp),
        ) {
            RegionIcon(region = region, modifier = Modifier.size(36.dp))
            Spacer(Modifier.height(4.dp))
            AppText(
                text = region.name,
                style = AppTheme.type.caption1Emphasized,
                color = if (isSelected) {
                    AppTheme.colors.tint
                } else {
                    AppTheme.colors.label
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
