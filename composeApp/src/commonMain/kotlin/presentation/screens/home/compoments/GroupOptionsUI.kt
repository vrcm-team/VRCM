package io.github.vrcmteam.vrcm.presentation.screens.home.compoments

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.vrcmteam.vrcm.network.api.attributes.FavoriteType
import io.github.vrcmteam.vrcm.network.api.favorite.data.FavoriteData
import io.github.vrcmteam.vrcm.network.api.favorite.data.FavoriteGroupData
import io.github.vrcmteam.vrcm.presentation.compoments.ATooltipBox
import io.github.vrcmteam.vrcm.service.FavoriteService
import org.koin.compose.koinInject

/**
 * 通用分组选项UI组件
 *
 * @param T 分组选项类型
 * @param currentOptions 当前选项
 * @param favoriteGroups 分组列表
 * @param defaultText 未选择分组时显示的文本
 * @param onOptionsChanged 选项变更回调
 * @param getSelectedGroup 从选项中获取当前选择的分组
 * @param updateOptions 更新选项的函数
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> GroupOptionsUI(
    currentOptions: T,
    favoriteType: FavoriteType,
    favoriteGroups: Map<FavoriteGroupData, List<FavoriteData>>,
    total: Int = favoriteGroups.values.sumOf { it.size },
    defaultText: String,
    onOptionsChanged: (T) -> Unit,
    getSelectedGroup: (T) -> FavoriteGroupData?,
    updateOptions: (T, FavoriteGroupData?) -> T,
    onClearGroup: ((FavoriteGroupData) -> Unit)? = null,
    clearGroupEnabled: Boolean = false,
    clearGroupInProgress: Boolean = false,
    clearGroupContentDescription: String = "",
) {
    val selectedGroup = getSelectedGroup(currentOptions)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        // 分组下拉菜单
        var expandGroupMenu by remember { mutableStateOf(false) }
        val favoriteService = koinInject<FavoriteService>()
        val maxFavoritesPerGroup = favoriteService.getMaxFavoritesPerGroup(favoriteType)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ExposedDropdownMenuBox(
                expanded = expandGroupMenu,
                onExpandedChange = { expandGroupMenu = it },
                modifier = Modifier.weight(1f),
            ) {
                OutlinedTextField(
                    value = selectedGroup?.displayName ?: defaultText,
                    onValueChange = {},
                    shape = MaterialTheme.shapes.medium,
                    readOnly = true,
                    singleLine = true,
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandGroupMenu)
                    },
                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(
                        unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                    ),
                    textStyle = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp)
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                )

                ExposedDropdownMenu(
                    shape = MaterialTheme.shapes.medium,
                    expanded = expandGroupMenu,
                    onDismissRequest = { expandGroupMenu = false },
                ) {
                    DropdownMenuItem(
                        text = { Text(defaultText) },
                        trailingIcon = { Text("$total") },
                        onClick = {
                            onOptionsChanged(updateOptions(currentOptions, null))
                            expandGroupMenu = false
                        },
                    )

                    favoriteGroups.forEach { (group, data) ->
                        DropdownMenuItem(
                            text = { Text(group.displayName) },
                            trailingIcon = { Text("${data.size}/${maxFavoritesPerGroup}") },
                            onClick = {
                                onOptionsChanged(updateOptions(currentOptions, group))
                                expandGroupMenu = false
                            },
                        )
                    }
                }
            }

            if (onClearGroup != null) {
                ATooltipBox(tooltip = { Text(clearGroupContentDescription) }) {
                    IconButton(
                        enabled = clearGroupEnabled && !clearGroupInProgress,
                        onClick = { selectedGroup?.let(onClearGroup) },
                    ) {
                        if (clearGroupInProgress) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = clearGroupContentDescription,
                            )
                        }
                    }
                }
            }
        }
    }
}
