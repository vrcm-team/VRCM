package io.github.vrcmteam.vrcm.presentation.screens.home.compoments

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.vrcmteam.vrcm.network.api.favorite.data.FavoriteGroupData

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
    favoriteGroups: List<FavoriteGroupData>,
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
                colors =  ExposedDropdownMenuDefaults.outlinedTextFieldColors(
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
                    onClick = {
                        onOptionsChanged(updateOptions(currentOptions, null))
                        expandGroupMenu = false
                    }
                )

                // 添加所有分组选项
                favoriteGroups.forEach { group ->
                    DropdownMenuItem(
                        text = { Text(group.displayName) },
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