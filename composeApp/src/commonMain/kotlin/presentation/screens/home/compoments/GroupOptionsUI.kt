package io.github.vrcmteam.vrcm.presentation.screens.home.compoments

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.vrcmteam.vrcm.network.api.attributes.FavoriteType
import io.github.vrcmteam.vrcm.network.api.favorite.data.FavoriteData
import io.github.vrcmteam.vrcm.network.api.favorite.data.FavoriteGroupData
import io.github.vrcmteam.vrcm.presentation.compoments.ATooltipBox
import io.github.vrcmteam.vrcm.presentation.designsystem.*
import io.github.vrcmteam.vrcm.presentation.supports.AppIcons
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
    onEditGroup: ((FavoriteGroupData) -> Unit)? = null,
    editGroupContentDescription: String = "",
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
            AppPopUpButton(
                value = selectedGroup?.displayName ?: defaultText,
                expanded = expandGroupMenu,
                onExpandedChange = { expandGroupMenu = it },
                modifier = Modifier.weight(1f),
            ) {
                AppMenuItem(
                    text = { AppText(defaultText) },
                    trailingIcon = { AppText("$total") },
                    onClick = {
                        onOptionsChanged(updateOptions(currentOptions, null))
                        expandGroupMenu = false
                    },
                )

                favoriteGroups.forEach { (group, data) ->
                    AppMenuItem(
                        text = { AppText(group.displayName) },
                        trailingIcon = { AppText("${data.size}/${maxFavoritesPerGroup}") },
                        onClick = {
                            onOptionsChanged(updateOptions(currentOptions, group))
                            expandGroupMenu = false
                        },
                    )
                }
            }

            if (onEditGroup != null) {
                val canEdit = selectedGroup != null &&
                    selectedGroup.ownerId != "local" &&
                    selectedGroup.type == favoriteType.value
                ATooltipBox(tooltip = { AppText(editGroupContentDescription) }) {
                    AppIconButton(
                        enabled = canEdit,
                        onClick = { selectedGroup?.let(onEditGroup) },
                    ) {
                        AppIcon(AppIcons.Edit, contentDescription = editGroupContentDescription)
                    }
                }
            }

            if (onClearGroup != null) {
                ATooltipBox(tooltip = { AppText(clearGroupContentDescription) }) {
                    AppIconButton(
                        enabled = clearGroupEnabled && !clearGroupInProgress,
                        onClick = { selectedGroup?.let(onClearGroup) },
                    ) {
                        if (clearGroupInProgress) {
                            AppActivityIndicator(
                                modifier = Modifier.size(20.dp),
                            )
                        } else {
                            AppIcon(
                                imageVector = AppIcons.Delete,
                                contentDescription = clearGroupContentDescription,
                            )
                        }
                    }
                }
            }
        }
    }
}
