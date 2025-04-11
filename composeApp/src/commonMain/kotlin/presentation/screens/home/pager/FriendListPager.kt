package io.github.vrcmteam.vrcm.presentation.screens.home.pager

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.koin.koinScreenModel
import io.github.vrcmteam.vrcm.network.api.favorite.data.FavoriteGroupData
import io.github.vrcmteam.vrcm.presentation.compoments.AdvancedOptionsPanel
import io.github.vrcmteam.vrcm.presentation.compoments.LocalSharedSuffixKey
import io.github.vrcmteam.vrcm.presentation.compoments.SearchTabType
import io.github.vrcmteam.vrcm.presentation.compoments.StandardSearchList
import io.github.vrcmteam.vrcm.presentation.extensions.currentNavigator
import io.github.vrcmteam.vrcm.presentation.supports.AppIcons
import io.github.vrcmteam.vrcm.presentation.supports.Pager

object FriendListPager : Pager {

    override val index: Int
        get() = 1
    override val title: String
        @Composable
        get() = "Friend"

    override val icon: Painter
        @Composable get() = rememberVectorPainter(image = AppIcons.Group)

    @Composable
    override fun Content() {
        val friendListPagerModel: FriendListPagerModel = koinScreenModel()

        // 搜索文本
        val searchText by friendListPagerModel.searchText.collectAsState()
        
        // 选中的标签页索引
        val selectedTabIndex by friendListPagerModel.selectedTabIndex.collectAsState()
        
        // 获取好友组列表和世界组列表
        val friendFavoriteGroups by friendListPagerModel.friendFavoriteGroupsFlow.collectAsState()
        val worldFavoriteGroups by friendListPagerModel.worldFavoriteGroupsFlow.collectAsState()
        
        // 获取分组选项状态
        val friendGroupOptions by friendListPagerModel.friendGroupOptions.collectAsState()
        val worldGroupOptions by friendListPagerModel.worldGroupOptions.collectAsState()
        
        // 获取列表数据
        val filteredFriends by friendListPagerModel.friendList.collectAsState()
        val filteredWorlds by friendListPagerModel.worldList.collectAsState()
        val isRefreshing by friendListPagerModel.isRefreshing.collectAsState()
        // 初始加载缓存数据: 只有第一次默认为ture才会刷新一次
        LaunchedEffect(Unit) {
            if (isRefreshing){
                friendListPagerModel.refreshCurrentTabCacheData(tabIndex = 0)
                friendListPagerModel.refreshCurrentTabCacheData(tabIndex = 1)
            }
        }

        StandardSearchList(
            key = title,
            searchText = searchText,
            updateSearchText = friendListPagerModel::setSearchText,
            selectedTabIndex = selectedTabIndex,
            onTabSelected = friendListPagerModel::setSelectedTabIndex,
            isRefreshing = isRefreshing,
            doRefresh = friendListPagerModel::refreshCurrentTabCacheData,
            userList = filteredFriends,
            worldList = filteredWorlds,
            advancedOptionsContent = { tabType ->
                // 根据当前选中的标签页显示不同的高级选项
                when (tabType) {
                    SearchTabType.USER -> { // 好友标签页
                        FriendGroupOptionsUI(
                            options = friendGroupOptions,
                            favoriteGroups = friendFavoriteGroups.keys.toList(),
                            onOptionsChanged = { newOptions ->
                                friendListPagerModel.updateFriendGroupOptions(newOptions)
                            }
                        )
                    }
                    SearchTabType.WORLD -> { // 世界标签页
                        WorldGroupOptionsUI(
                            options = worldGroupOptions,
                            favoriteGroups = worldFavoriteGroups.keys.toList(),
                            onOptionsChanged = { newOptions ->
                                friendListPagerModel.updateWorldGroupOptions(newOptions)
                            }
                        )
                    }
                    else -> {}
                }
            }
        )
    }
}

/**
 * 好友分组选项UI组件
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendGroupOptionsUI(
    options: FriendGroupOptions,
    favoriteGroups: List<FavoriteGroupData>,
    onOptionsChanged: (FriendGroupOptions) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        // 好友分组选择
        // 好友分组下拉菜单
        var expandGroupMenu by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(
            expanded = expandGroupMenu,
            onExpandedChange = { expandGroupMenu = it }
        ) {
            OutlinedTextField(
                value = options.selectedGroup?.displayName ?: "全部好友",
                onValueChange = {},
                shape = MaterialTheme.shapes.medium,
                readOnly = true,
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandGroupMenu)
                },
                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
            )

            ExposedDropdownMenu(
                shape = MaterialTheme.shapes.medium,
                expanded = expandGroupMenu,
                onDismissRequest = { expandGroupMenu = false }
            ) {
                // 添加"全部好友"选项
                DropdownMenuItem(
                    text = { Text("全部好友") },
                    onClick = {
                        onOptionsChanged(options.copy(selectedGroup = null))
                        expandGroupMenu = false
                    }
                )

                // 添加所有好友分组选项
                favoriteGroups.forEach { group ->
                    DropdownMenuItem(
                        text = { Text(group.displayName) },
                        onClick = {
                            onOptionsChanged(options.copy(selectedGroup = group))
                            expandGroupMenu = false
                        }
                    )
                }
            }
        }
    }
}

/**
 * 世界分组选项UI组件
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorldGroupOptionsUI(
    options: WorldGroupOptions,
    favoriteGroups: List<FavoriteGroupData>,
    onOptionsChanged: (WorldGroupOptions) -> Unit
) {
    // 世界分组选择
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        // 世界分组下拉菜单
        var expandGroupMenu by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(
            expanded = expandGroupMenu,
            onExpandedChange = { expandGroupMenu = it }
        ) {
            OutlinedTextField(
                value = options.selectedGroup?.displayName ?: "全部世界",
                onValueChange = {},
                readOnly = true,
                shape = MaterialTheme.shapes.medium,
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandGroupMenu)
                },
                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
            )

            ExposedDropdownMenu(
                shape = MaterialTheme.shapes.medium,
                expanded = expandGroupMenu,
                onDismissRequest = { expandGroupMenu = false }
            ) {
                // 添加"全部世界"选项
                DropdownMenuItem(
                    text = { Text("全部世界") },
                    onClick = {
                        onOptionsChanged(options.copy(selectedGroup = null))
                        expandGroupMenu = false
                    }
                )

                // 添加所有世界分组选项
                favoriteGroups.forEach { group ->
                    DropdownMenuItem(
                        text = { Text(group.displayName) },
                        onClick = {
                            onOptionsChanged(options.copy(selectedGroup = group))
                            expandGroupMenu = false
                        }
                    )
                }
            }
        }
    }
}

