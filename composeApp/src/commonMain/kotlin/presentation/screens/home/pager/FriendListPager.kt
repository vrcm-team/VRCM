package io.github.vrcmteam.vrcm.presentation.screens.home.pager

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.koin.koinScreenModel
import io.github.vrcmteam.vrcm.network.api.favorite.data.FavoriteGroupData
import io.github.vrcmteam.vrcm.presentation.compoments.*
import io.github.vrcmteam.vrcm.presentation.extensions.currentNavigator
import io.github.vrcmteam.vrcm.presentation.screens.user.UserProfileScreen
import io.github.vrcmteam.vrcm.presentation.screens.user.data.UserProfileVo
import io.github.vrcmteam.vrcm.presentation.screens.world.WorldProfileScreen
import io.github.vrcmteam.vrcm.presentation.screens.world.data.WorldProfileVo
import io.github.vrcmteam.vrcm.presentation.settings.locale.strings
import io.github.vrcmteam.vrcm.presentation.supports.AppIcons
import io.github.vrcmteam.vrcm.presentation.supports.Pager
import kotlinx.coroutines.launch

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
        val coroutineScope = rememberCoroutineScope()
        val currentNavigator = currentNavigator
        val sharedSuffixKey = LocalSharedSuffixKey.current
        
        // 搜索文本
        val searchText by friendListPagerModel.searchText.collectAsState()
        
        // 选中的标签页索引
        val selectedTabIndex by friendListPagerModel.selectedTabIndex.collectAsState()

        // 好友组选项状态
        var showFriendGroupOptions by remember { mutableStateOf(false) }
        
        // 世界组选项状态
        var showWorldGroupOptions by remember { mutableStateOf(false) }
        
        // 获取好友组列表和世界组列表
        val friendFavoriteGroups by friendListPagerModel.friendFavoriteGroupsFlow.collectAsState()
        val worldFavoriteGroups by friendListPagerModel.worldFavoriteGroupsFlow.collectAsState()
        
        // 定义标签列表 - 好友和世界两个标签
        val tabs = listOf(strings.searchUsers, strings.searchWorlds)
        
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


        GenericSearchList(
            key = title,
            searchText = searchText,
            updateSearchText = friendListPagerModel::setSearchText,
            tabs = tabs,
            selectedTabIndex = selectedTabIndex,
            onTabSelected = friendListPagerModel::setSelectedTabIndex,
            isRefreshing = isRefreshing,
            doRefresh = friendListPagerModel::refreshCurrentTabCacheData,
            advancedOptionsContent = {
                // 根据当前选中的标签页显示不同的高级选项
                when (selectedTabIndex) {
                    0 -> { // 好友标签页
                        AdvancedOptionsPanel(
                            title = "好友分组选项",
                            expanded = showFriendGroupOptions,
                            onExpandToggle = { showFriendGroupOptions = !showFriendGroupOptions }
                        ) {
                            FriendGroupOptionsUI(
                                options = friendGroupOptions,
                                favoriteGroups = friendFavoriteGroups.keys.toList(),
                                onOptionsChanged = { newOptions ->
                                    friendListPagerModel.updateFriendGroupOptions(newOptions)
                                }
                            )
                        }
                    }
                    1 -> { // 世界标签页
                        AdvancedOptionsPanel(
                            title = "世界分组选项",
                            expanded = showWorldGroupOptions,
                            onExpandToggle = { showWorldGroupOptions = !showWorldGroupOptions }
                        ) {
                            WorldGroupOptionsUI(
                                options = worldGroupOptions,
                                favoriteGroups = worldFavoriteGroups.keys.toList(),
                                onOptionsChanged = { newOptions ->
                                    friendListPagerModel.updateWorldGroupOptions(newOptions)
                                }
                            )
                        }
                    }
                }
            }
        ) { tabIndex ->
            when (tabIndex) {
                0 -> { // 好友标签页
                    renderUserItems(
                        users = filteredFriends,
                        onUserClick = { user ->
                            // 处理用户点击，导航到用户资料页面
                            if (currentNavigator.size <= 1) {
                                coroutineScope.launch {
                                    currentNavigator push UserProfileScreen(
                                        UserProfileVo(user),
                                        sharedSuffixKey
                                    )
                                }
                            }
                        }
                    )
                }
                1 -> { // 世界标签页
                    renderWorldItems(
                        worlds = filteredWorlds,
                        onWorldClick = { world ->
                            // 处理世界点击，导航到世界详情页面
                            if (currentNavigator.size <= 1) {
                                coroutineScope.launch {
                                    currentNavigator push WorldProfileScreen(
                                        WorldProfileVo(world)
                                    )
                                }
                            }
                        }
                    )
                }
            }
        }
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
            .padding(vertical = 8.dp)
    ) {
        // 好友分组选择
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
        ) {
            Text(
                text = "选择好友分组",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(4.dp))
            
            // 好友分组下拉菜单
            var expandGroupMenu by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = expandGroupMenu,
                onExpandedChange = { expandGroupMenu = it }
            ) {
                OutlinedTextField(
                    value = options.selectedGroup?.displayName ?: "全部好友",
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandGroupMenu)
                    },
                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor()
                )
                
                ExposedDropdownMenu(
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
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        // 世界分组选择
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
        ) {
            Text(
                text = "选择世界分组",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(4.dp))
            
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
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandGroupMenu)
                    },
                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor()
                )
                
                ExposedDropdownMenu(
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
}

