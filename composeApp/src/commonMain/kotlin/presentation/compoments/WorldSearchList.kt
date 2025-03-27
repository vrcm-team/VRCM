package io.github.vrcmteam.vrcm.presentation.compoments

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.network.api.worlds.data.WorldData
import io.github.vrcmteam.vrcm.presentation.extensions.animateScrollToFirst
import io.github.vrcmteam.vrcm.presentation.extensions.currentNavigator
import io.github.vrcmteam.vrcm.presentation.extensions.getInsetPadding
import io.github.vrcmteam.vrcm.presentation.screens.home.pager.WorldSearchOptions
import io.github.vrcmteam.vrcm.presentation.screens.world.WorldProfileScreen
import io.github.vrcmteam.vrcm.presentation.screens.world.data.WorldProfileVo
import io.github.vrcmteam.vrcm.presentation.settings.locale.strings
import kotlinx.coroutines.launch

/**
 * 排序选项枚举
 */
enum class SortOption(val value: String) {
    Popularity("popularity"),
    Heat("heat"),
    Trust("trust"),
    Shuffle("shuffle"),
    Random("random"),
    Favorites("favorites"),
    Created("created"),
    Updated("updated"),
    Relevance("relevance"),
    Name("name");
    
    companion object {
        fun fromString(value: String): SortOption {
            return values().find { it.value == value } ?: Popularity
        }
    }
    
    /**
     * 获取排序选项的本地化显示名称
     */
    val displayName: String
        @Composable
        get() {
            return when (this) {
                Popularity -> strings.worldSearchSortPopularity
                Heat -> strings.worldSearchSortHeat
                Trust -> strings.worldSearchSortTrust
                Shuffle -> strings.worldSearchSortShuffle
                Random -> strings.worldSearchSortRandom
                Favorites -> strings.worldSearchSortFavorites
                Created -> strings.worldSearchSortCreated
                Updated -> strings.worldSearchSortUpdated
                Relevance -> strings.worldSearchSortRelevance
                Name -> strings.worldSearchSortName
            }
        }
}

/**
 * 世界搜索列表组件
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorldSearchList(
    key: String,
    isRefreshing: Boolean? = null,
    searchText: String,
    updateSearchText: (String) -> Unit,
    worldListInit: (String) -> List<WorldData> = { emptyList() },
    doRefresh: (suspend () -> Unit)? = null,
    onUpdateSearch: suspend (searchText: String, worldList: MutableList<WorldData>) -> Unit,
    headerContent: @Composable () -> Unit,
    initialSearchOptions: WorldSearchOptions = WorldSearchOptions(),
    onSearchOptionsChanged: ((WorldSearchOptions) -> Unit)? = null
) {
    val lazyListState = rememberLazyListState()
//    var searchText by rememberSaveable(key) { mutableStateOf("") }
    val worldList: MutableList<WorldData> by remember { derivedStateOf { worldListInit(searchText).toMutableStateList() } }
    var showAdvancedOptions by rememberSaveable { mutableStateOf(false) }
    var searchOptions by rememberSaveable { mutableStateOf(initialSearchOptions) }

    LaunchedEffect(searchText) {
        onUpdateSearch(searchText, worldList)
    }

    LaunchedEffect(key) {
        SharedFlowCentre.toPagerTop.collect {
            // 防止滑动时手动阻止滑动动画导致任务取消,监听失效的bug
            runCatching {
                lazyListState.animateScrollToFirst()
            }
        }
    }

    WorldList(
        lazyListState,
        worldList,
        isRefreshing,
        doRefresh
    ) {
        Column {
            // 搜索框
            SearchTextField(
                modifier = Modifier.padding(horizontal = 16.dp),
                value = searchText,
                onValueChange = updateSearchText
            )
            
            // 自定义头部内容（tabs）
            headerContent()
            
            // 高级搜索选项切换按钮
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .clickable { showAdvancedOptions = !showAdvancedOptions },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = strings.worldSearchAdvancedOptions
                    )
                    Text(
                        text = strings.worldSearchAdvancedOptions,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Icon(
                    imageVector = if (showAdvancedOptions) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (showAdvancedOptions) "收起" else "展开"
                )
            }
            
            // 高级搜索选项
            AnimatedVisibility(visible = showAdvancedOptions) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    // 精选世界选项
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = strings.worldSearchFeaturedOnly,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Switch(
                            checked = searchOptions.featured ?: false,
                            onCheckedChange = { 
                                val newOptions = searchOptions.copy(featured = if (it) true else null)
                                searchOptions = newOptions
                                onSearchOptionsChanged?.invoke(newOptions)
                            }
                        )
                    }
                    
                    // 排序方式选项
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
                        
                        // 排序下拉菜单
                        var expandSortMenu by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(
                            expanded = expandSortMenu,
                            onExpandedChange = { expandSortMenu = it }
                        ) {
                            OutlinedTextField(
                                value = searchOptions.sortOption.displayName,
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
                                SortOption.entries.forEach { sortOption ->
                                    DropdownMenuItem(
                                        text = { Text(sortOption.displayName) },
                                        onClick = {
                                            val newOptions = searchOptions.copy(sortOption = sortOption)
                                            searchOptions = newOptions
                                            onSearchOptionsChanged?.invoke(newOptions)
                                            expandSortMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                    
                    // 排序顺序选项
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = strings.worldSearchOrder,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        
                        // 单选按钮组
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = searchOptions.order == "descending",
                                    onClick = {
                                        val newOptions = searchOptions.copy(order = "descending")
                                        searchOptions = newOptions
                                        onSearchOptionsChanged?.invoke(newOptions)
                                    }
                                )
                                Text(strings.worldSearchDescending)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = searchOptions.order == "ascending",
                                    onClick = {
                                        val newOptions = searchOptions.copy(order = "ascending")
                                        searchOptions = newOptions
                                        onSearchOptionsChanged?.invoke(newOptions)
                                    }
                                )
                                Text(strings.worldSearchAscending)
                            }
                        }
                    }
                    
                    // 显示数量选项
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
                            value = searchOptions.resultsCount.toFloat(),
                            onValueChange = { 
                                val newValue = it.toInt()
                                if (newValue != searchOptions.resultsCount) {
                                    val newOptions = searchOptions.copy(resultsCount = newValue)
                                    searchOptions = newOptions
                                    onSearchOptionsChanged?.invoke(newOptions)
                                }
                            },
                            valueRange = 10f..100f,
                            steps = 9,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            text = strings.worldSearchResultsFormat.replaceFirst("%d", searchOptions.resultsCount.toString()),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}



@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorldList(
    state: LazyListState,
    worldList: List<WorldData>,
    isRefreshing: Boolean? = null,
    doRefresh: (suspend () -> Unit)? = null,
    headerContent: @Composable () -> Unit,
) {
    val topPadding = getInsetPadding(WindowInsets::getTop) + 80.dp
    val navigator = currentNavigator
    val coroutineScope = rememberCoroutineScope()
    
    val toWorldProfile = { world: WorldData ->
        if (navigator.size <= 1) {
            coroutineScope.launch {
                navigator push WorldProfileScreen(WorldProfileVo(world))
            }
        }
    }
    
    // 如果没有底部系统手势条，默认12dp
    val bottomPadding = getInsetPadding(12, WindowInsets::getBottom) + 80.dp

    val worldListLazyColumn = @Composable {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = state,
            contentPadding = PaddingValues(
                top = topPadding, bottom = bottomPadding
            ),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item {
                headerContent()
            }
            items(worldList, key = { it.id }) {
                WorldListItem(it, toWorldProfile)
            }
        }
    }
    
    if (isRefreshing != null && doRefresh != null) {
        RefreshBox(
            refreshContainerOffsetY = topPadding, isRefreshing = isRefreshing, doRefresh = doRefresh
        ) {
            worldListLazyColumn()
        }
    } else {
        worldListLazyColumn()
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun LazyItemScope.WorldListItem(world: WorldData, toWorldProfile: (WorldData) -> Unit) {
    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .height(68.dp)
            .padding(horizontal = 6.dp)
            .clip(MaterialTheme.shapes.large)
            .clickable { toWorldProfile(world) }
            .animateItem(),
        leadingContent = {
            WorldThumbnail(
                modifier = Modifier.sharedBoundsBy("${world.id}WorldThumbnail"),
                thumbnailUrl = world.thumbnailImageUrl,
            )
        },
        headlineContent = {
            Text(
                text = world.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1
            )
        },
        supportingContent = {
            Text(
                text = world.authorName,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1
            )
        },
        trailingContent = {
            // 显示世界访问等级
            Text(
                text = world.releaseStatus,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1
            )
        },
    )
}

@Composable
fun WorldThumbnail(
    modifier: Modifier = Modifier,
    thumbnailUrl: String?
) {
    // 使用AvatarImage组件显示世界缩略图
    AImage(
        modifier = modifier.size(48.dp),
        imageData = thumbnailUrl,
    )
} 