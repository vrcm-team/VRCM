package io.github.vrcmteam.vrcm.presentation.screens.home.compoments

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.vrcmteam.vrcm.network.api.attributes.FavoriteType
import io.github.vrcmteam.vrcm.network.api.favorite.data.FavoriteData
import io.github.vrcmteam.vrcm.network.api.favorite.data.FavoriteGroupData
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
    updateOptions: (T, FavoriteGroupData?) -> T
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        // 分组下拉菜单
        var expandGroupMenu by remember { mutableStateOf(false) }
        val favoriteService = koinInject<FavoriteService>()
        val maxFavoritesPerGroup = favoriteService.getMaxFavoritesPerGroup(favoriteType)
        ExposedDropdownMenuBox(
            expanded = expandGroupMenu,
            onExpandedChange = { expandGroupMenu = it }
        ) {
            OutlinedTextField(
                value = getSelectedGroup(currentOptions)?.displayName ?: defaultText,
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
                    .defaultMinSize(minHeight = 48.dp) // 使用默认最小高度
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
            )

            ExposedDropdownMenu(
                shape = MaterialTheme.shapes.medium,
                expanded = expandGroupMenu,
                onDismissRequest = { expandGroupMenu = false }
            ) {
                // 添加"全部"选项
                DropdownMenuItem(
                    text = { Text(defaultText) },
                    trailingIcon = { Text("$total") },
                    onClick = {
                        onOptionsChanged(updateOptions(currentOptions, null))
                        expandGroupMenu = false
                    }
                )

                // 添加所有分组选项
                favoriteGroups.forEach { (group, data) ->
                    DropdownMenuItem(
                        text = { Text(group.displayName) },
                        trailingIcon = { Text("${data.size}/${maxFavoritesPerGroup}") },
                        onClick = {
                            onOptionsChanged(updateOptions(currentOptions, group))
                            expandGroupMenu = false
                        }
                    )
                }
            }
        }
    }
} 