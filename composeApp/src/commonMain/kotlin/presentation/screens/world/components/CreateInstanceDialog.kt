package io.github.vrcmteam.vrcm.presentation.screens.world.components

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.vrcmteam.vrcm.network.api.attributes.AccessType
import io.github.vrcmteam.vrcm.network.api.attributes.RegionType
import io.github.vrcmteam.vrcm.presentation.compoments.RegionIcon
import io.github.vrcmteam.vrcm.presentation.settings.locale.strings

/**
 * 创建实例对话框
 *
 * @param onDismiss 取消回调
 * @param onConfirm 确认创建回调，传递选择的访问权限和区域
 */
class CreateInstanceDialog(
    private val onDismiss: () -> Unit = {},
    private val onConfirm: (
        AccessType, 
        RegionType, 
        queueEnabled: Boolean,
        groupId: String?,
        groupAccessType: String?,
        roleIds: List<String>?
    ) -> Unit
) {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun Content() {
        var selectedAccessType by remember { mutableStateOf<AccessType>(AccessType.FriendPlus) }
        var selectedRegion by remember { mutableStateOf<RegionType>(RegionType.Us) }
        var queueEnabled by remember { mutableStateOf(false) }
        var groupId by remember { mutableStateOf("") }
        var groupAccessType by remember { mutableStateOf("members") }
//        var roleId by remember { mutableStateOf("") }
        var roleIds by remember { mutableStateOf<List<String>>(emptyList()) }
        var showAdvancedOptions by remember { mutableStateOf(false) }
        
        // 确定当前选择的访问类型的分类
        val isGroupType = selectedAccessType == AccessType.Group ||
                          selectedAccessType == AccessType.GroupMembers ||
                          selectedAccessType == AccessType.GroupPlus ||
                          selectedAccessType == AccessType.GroupPublic
        
//        val isPrivateType = selectedAccessType == AccessType.Invite ||
//                            selectedAccessType == AccessType.InvitePlus
        
        BasicAlertDialog(
            onDismissRequest = onDismiss,
            modifier = Modifier.fillMaxWidth(0.9f).heightIn(max = 600.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = strings.createInstance,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // 访问权限选择
                    Text(
                        text = strings.accessType,
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Start,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // 访问类型分组
                    Text(
                        text = "标准访问类型",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primary
                    )

                    val standardAccessTypes = listOf(
                        AccessType.Public,
                        AccessType.FriendPlus,
                        AccessType.Friend,
                        AccessType.InvitePlus,
                        AccessType.Invite,
                        // TODO 由于群组的房间创建过于复杂，所以暂时不支持
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 150.dp)
                            .verticalScroll(rememberScrollState())
                    ){
                        standardAccessTypes.forEach { accessType ->
                            AccessTypeItem(
                                accessType = accessType,
                                isSelected = accessType == selectedAccessType,
                                onClick = { selectedAccessType = accessType }
                            )
                        }
                    }


//                        Spacer(modifier = Modifier.height(8.dp))
//
//                        // 群组访问类型
//                        Text(
//                            text = "群组访问类型",
//                            style = MaterialTheme.typography.labelMedium,
//                            modifier = Modifier.fillMaxWidth(),
//                            color = MaterialTheme.colorScheme.primary
//                        )
//
//                        val groupAccessTypes = listOf(
//                            AccessType.Group,
//                            AccessType.GroupMembers,
//                            AccessType.GroupPlus,
//                            AccessType.GroupPublic
//                        )
//
//                        groupAccessTypes.forEach { accessType ->
//                            AccessTypeItem(
//                                accessType = accessType,
//                                isSelected = accessType == selectedAccessType,
//                                onClick = { selectedAccessType = accessType }
//                            )
//                        }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 区域选择
                    Text(
                        text = strings.regionType,
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Start,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        RegionType.entries.filter { it != RegionType.Unknown }.forEach { region ->
                            RegionItem(
                                region = region,
                                isSelected = region == selectedRegion,
                                onClick = { selectedRegion = region }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 排队功能
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "启用排队功能",
                            style = MaterialTheme.typography.bodyMedium
                        )

                        Switch(
                            checked = queueEnabled,
                            onCheckedChange = { queueEnabled = it }
                        )
                    }

                    // 高级选项
                    if (showAdvancedOptions) {
                        Spacer(modifier = Modifier.height(8.dp))



                        // 如果是群组类型，显示群组相关选项
//                            if (isGroupType) {
//                                Spacer(modifier = Modifier.height(8.dp))
//
//                                OutlinedTextField(
//                                    value = groupId,
//                                    onValueChange = { groupId = it },
//                                    label = { Text("群组ID (必填)") },
//                                    modifier = Modifier.fillMaxWidth(),
//                                    singleLine = true
//                                )
//
//                                Spacer(modifier = Modifier.height(8.dp))
//
//                                // 添加角色ID
//                                OutlinedTextField(
//                                    value = roleId,
//                                    onValueChange = { roleId = it },
//                                    label = { Text("角色ID") },
//                                    modifier = Modifier.fillMaxWidth(),
//                                    singleLine = true,
//                                    keyboardOptions = KeyboardOptions(
//                                        imeAction = ImeAction.Done,
//                                        keyboardType = KeyboardType.Text
//                                    ),
//                                    keyboardActions = KeyboardActions(
//                                        onDone = {
//                                            if (roleId.isNotBlank() && !roleIds.contains(roleId)) {
//                                                roleIds = roleIds + roleId
//                                                roleId = ""
//                                            }
//                                        }
//                                    ),
//                                    trailingIcon = {
//                                        IconButton(onClick = {
//                                            if (roleId.isNotBlank() && !roleIds.contains(roleId)) {
//                                                roleIds = roleIds + roleId
//                                                roleId = ""
//                                            }
//                                        }) {
//                                            Text("+", style = MaterialTheme.typography.bodyLarge)
//                                        }
//                                    }
//                                )
//
//                                // 已添加的角色ID列表
//                                if (roleIds.isNotEmpty()) {
//                                    Spacer(modifier = Modifier.height(8.dp))
//
//                                    Text(
//                                        text = "已添加的角色ID:",
//                                        style = MaterialTheme.typography.bodySmall,
//                                        modifier = Modifier.fillMaxWidth()
//                                    )
//
//                                    roleIds.forEach { id ->
//                                        Row(
//                                            modifier = Modifier
//                                                .fillMaxWidth()
//                                                .padding(vertical = 4.dp),
//                                            horizontalArrangement = Arrangement.SpaceBetween,
//                                            verticalAlignment = Alignment.CenterVertically
//                                        ) {
//                                            Text(
//                                                text = id,
//                                                style = MaterialTheme.typography.bodySmall,
//                                                modifier = Modifier.weight(1f)
//                                            )
//
//                                            IconButton(
//                                                onClick = { roleIds = roleIds.filter { it != id } },
//                                                modifier = Modifier.size(24.dp)
//                                            ) {
//                                                Text("×", style = MaterialTheme.typography.bodyLarge)
//                                            }
//                                        }
//                                    }
//                                }
//                            }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // 验证是否可以提交
                    val canSubmit = if (isGroupType) {
                        groupId.isNotBlank()
                    } else {
                        true
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(text = strings.cancel)
                        }

                        Button(
                            onClick = {
                                onConfirm(
                                    selectedAccessType,
                                    selectedRegion,
                                    queueEnabled,
                                    if (isGroupType) groupId else null,
                                    if (isGroupType) groupAccessType else null,
                                    if (isGroupType) roleIds else null
                                )
                            },
                            modifier = Modifier.weight(1f),
                            enabled = canSubmit
                        ) {
                            Text(text = strings.confirm)
                        }
                    }
                }
            }
        }
    }
    
    @Composable
    private fun AccessTypeItem(
        accessType: AccessType,
        isSelected: Boolean,
        onClick: () -> Unit
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
                .padding(vertical = 4.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(backgroundColor)
                .clickable(onClick = onClick)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = accessType.displayName,
                style = MaterialTheme.typography.bodyLarge,
                color = textColor
            )
        }
    }
    
    @Composable
    private fun RegionItem(
        region: RegionType,
        isSelected: Boolean,
        onClick: () -> Unit
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .padding(4.dp)
                .clip(RoundedCornerShape(8.dp))
                .border(
                    width = if (isSelected) 2.dp else 1.dp,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                    shape = RoundedCornerShape(8.dp)
                )
                .clickable(onClick = onClick)
                .padding(8.dp)
        ) {
            RegionIcon(
                region = region,
                modifier = Modifier.size(36.dp)
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                text = region.name,
                style = MaterialTheme.typography.labelMedium,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
        }
    }
} 