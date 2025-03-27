package io.github.vrcmteam.vrcm.presentation.compoments

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.vrcmteam.vrcm.network.api.attributes.IUser
import io.github.vrcmteam.vrcm.network.api.worlds.data.WorldData
import io.github.vrcmteam.vrcm.presentation.extensions.currentNavigator
import io.github.vrcmteam.vrcm.presentation.screens.user.UserProfileScreen
import io.github.vrcmteam.vrcm.presentation.screens.user.data.UserProfileVo
import io.github.vrcmteam.vrcm.presentation.screens.world.WorldProfileScreen
import io.github.vrcmteam.vrcm.presentation.screens.world.data.WorldProfileVo
import io.github.vrcmteam.vrcm.presentation.settings.locale.strings
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * 通用搜索页面演示
 * 
 * 这个示例展示了如何使用GenericSearchList组件创建一个包含用户和世界搜索的界面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GenericSearchListDemo(
    userSearchModel: SearchDataProvider<IUser>,
    worldSearchModel: SearchDataProvider<WorldData>
) {
    val coroutineScope = rememberCoroutineScope()
    
    // 用于管理当前选中的标签页
    var selectedTabIndex by remember { mutableStateOf(0) }
    
    // 搜索文本
    var searchText by remember { mutableStateOf("") }
    
    // 高级搜索选项展开状态
    var showAdvancedOptions by remember { mutableStateOf(false) }
    
    // 高级搜索选项示例（仅适用于世界搜索）
    var featuredOnly by remember { mutableStateOf(false) }
    var sortOption by remember { mutableStateOf("popularity") }
    var resultCount by remember { mutableStateOf(20) }
    
    // 标签页内容
    val tabs = listOf(strings.searchUsers, strings.searchWorlds)
    
    // 导航器
    val currentNavigator = currentNavigator
    val sharedSuffixKey = LocalSharedSuffixKey.current
    
    // 处理用户点击
    val onUserClick = { user: IUser ->
        if (currentNavigator.size <= 1) {
            currentNavigator push UserProfileScreen(UserProfileVo(user), sharedSuffixKey)
        }
    }
    
    // 处理世界点击
    val onWorldClick = { world: WorldData ->
        if (currentNavigator.size <= 1) {
            coroutineScope.launch {
                currentNavigator push WorldProfileScreen(WorldProfileVo(world))
            }
        }
    }
    
    // 更新搜索
    val updateSearch = {
        coroutineScope.launch {
            when (selectedTabIndex) {
                0 -> userSearchModel.search(searchText)
                1 -> {
                    // 如果是世界搜索，可以添加额外的搜索选项
                    val options = mapOf(
                        "featured" to if (featuredOnly) "true" else null,
                        "sort" to sortOption,
                        "n" to resultCount.toString()
                    )
                    worldSearchModel.search(searchText, options)
                }
            }
        }
    }
    
    // 当搜索文本或标签页改变时触发搜索
    LaunchedEffect(searchText, selectedTabIndex) {
        updateSearch()
    }
    
    // 主要UI
    GenericSearchList(
        key = "GenericSearch",
        searchText = searchText,
        updateSearchText = { 
            searchText = it 
        },
        tabs = tabs,
        selectedTabIndex = selectedTabIndex,
        onTabSelected = { 
            selectedTabIndex = it 
        },
        advancedOptionsContent = {
            // 仅在世界搜索时显示高级选项
            if (selectedTabIndex == 1) {
                AdvancedOptionsPanel(
                    title = strings.worldSearchAdvancedOptions,
                    expanded = showAdvancedOptions,
                    onExpandToggle = { showAdvancedOptions = !showAdvancedOptions }
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 8.dp)
                    ) {
                        // 精选世界选项
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = strings.worldSearchFeaturedOnly,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Switch(
                                checked = featuredOnly,
                                onCheckedChange = { 
                                    featuredOnly = it
                                    updateSearch()
                                }
                            )
                        }
                        
                        // 排序选项
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Text(
                                text = strings.worldSearchSortBy,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            
                            // 排序选项选择器
                            var expandSortMenu by remember { mutableStateOf(false) }
                            ExposedDropdownMenuBox(
                                expanded = expandSortMenu,
                                onExpandedChange = { expandSortMenu = it }
                            ) {
                                OutlinedTextField(
                                    value = sortOption,
                                    onValueChange = {},
                                    readOnly = true,
                                    trailingIcon = {
                                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandSortMenu)
                                    },
                                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .menuAnchor()
                                )
                                
                                ExposedDropdownMenu(
                                    expanded = expandSortMenu,
                                    onDismissRequest = { expandSortMenu = false }
                                ) {
                                    listOf("popularity", "heat", "created", "updated").forEach { option ->
                                        DropdownMenuItem(
                                            text = { Text(option) },
                                            onClick = {
                                                sortOption = option
                                                expandSortMenu = false
                                                updateSearch()
                                            }
                                        )
                                    }
                                }
                            }
                        }
                        
                        // 结果数量选择器
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Text(
                                text = strings.worldSearchResultCount,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Slider(
                                value = resultCount.toFloat(),
                                onValueChange = { 
                                    resultCount = it.toInt()
                                    updateSearch()
                                },
                                valueRange = 10f..100f,
                                steps = 9,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(
                                text = strings.worldSearchResultsFormat.replaceFirst("%d", resultCount.toString()),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }
    ) { tabIndex ->
        when (tabIndex) {
            0 -> {
                // 用户搜索结果
                renderUserItems(
                    users = userSearchModel.data.value,
                    onUserClick = onUserClick
                )
            }
            1 -> {
                // 世界搜索结果
                renderWorldItems(
                    worlds = worldSearchModel.data.value,
                    onWorldClick = onWorldClick
                )
            }
        }
    }
}

/**
 * 搜索数据提供者接口
 * 封装搜索结果数据并提供搜索方法
 */
interface SearchDataProvider<T> {
    val data: StateFlow<List<T>>
    
    suspend fun search(query: String, options: Map<String, String?> = emptyMap())
} 