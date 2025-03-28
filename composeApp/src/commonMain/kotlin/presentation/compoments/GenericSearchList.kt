package io.github.vrcmteam.vrcm.presentation.compoments

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.presentation.extensions.animateScrollToFirst
import io.github.vrcmteam.vrcm.presentation.extensions.getInsetPadding

/**
 * 通用搜索列表组件
 * 提供搜索框、选项卡和可切换的内容
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GenericSearchList(
    key: String,
    searchText: String,
    updateSearchText: (String) -> Unit,
    tabs: List<String>,
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    isRefreshing: Boolean? = null,
    doRefresh: (suspend () -> Unit)? = null,
    headerContent: @Composable () -> Unit = {},
    advancedOptionsContent: @Composable (() -> Unit)? = null,
    itemContent:  LazyListScope.(Int) -> Unit
) {
    val lazyListState = rememberLazyListState()
    
    // 监听返回顶部事件
    LaunchedEffect(key) {
        SharedFlowCentre.toPagerTop.collect {
            runCatching {
                lazyListState.animateScrollToFirst()
            }
        }
    }
    
    val topPadding = getInsetPadding(WindowInsets::getTop) + 80.dp
    val bottomPadding = getInsetPadding(12, WindowInsets::getBottom) + 80.dp
    
    val contentLazyColumn = @Composable {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = lazyListState,
            contentPadding = PaddingValues(
                top = topPadding, bottom = bottomPadding
            ),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item {
                Column {
                    // 搜索框
                    SearchTextField(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        value = searchText,
                        onValueChange = updateSearchText
                    )
                    
                    // 自定义顶部内容
                    headerContent()
                    
                    // 标签栏
                    TabRow(
                        selectedTabIndex = selectedTabIndex,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp, bottom = 8.dp),
                        indicator = {
                            TabRowDefaults.SecondaryIndicator(
                                modifier = Modifier
                                    .tabIndicatorOffset(it[selectedTabIndex])
                                    .padding(horizontal = 24.dp)  // 使指示器两侧变短
                                    .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))  // 添加上部圆角
                            )
                        },
                    ) {
                        tabs.forEachIndexed { index, title ->
                            Tab(
                                selected = index == selectedTabIndex,
                                onClick = { onTabSelected(index) },
                                text = { Text(title) }
                            )
                        }
                    }
                    
                    // 高级搜索选项（如果有）
                    advancedOptionsContent?.invoke()
                }
            }

            // 当前选中标签页的内容
            itemContent(selectedTabIndex)

        }
    }
    
    if (isRefreshing != null && doRefresh != null) {
        RefreshBox(
            refreshContainerOffsetY = topPadding, 
            isRefreshing = isRefreshing, 
            doRefresh = doRefresh
        ) {
            contentLazyColumn()
        }
    } else {
        contentLazyColumn()
    }
}

/**
 * 用于显示搜索结果列表项的组件
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> SearchResultItem(
    item: T,
    onClick: (T) -> Unit,
    leadingContent: @Composable () -> Unit,
    headlineContent: @Composable () -> Unit,
    supportingContent: @Composable (() -> Unit)? = null,
    trailingContent: @Composable (() -> Unit)? = null
) {
    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .height(68.dp)
            .padding(horizontal = 6.dp)
            .clip(MaterialTheme.shapes.large)
            .clickable { onClick(item) },
        leadingContent = leadingContent,
        headlineContent = headlineContent,
        supportingContent = supportingContent ?: {},
        trailingContent = trailingContent ?: {}
    )
}

/**
 * 可折叠的高级搜索选项面板
 */
@Composable
fun AdvancedOptionsPanel(
    title: String,
    expanded: Boolean,
    onExpandToggle: () -> Unit,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        // 选项面板标题栏
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onExpandToggle)
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = title
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (expanded) "收起" else "展开"
            )
        }
        
        // 展开时显示内容
        AnimatedVisibility(visible = expanded) {
            content()
        }
    }
} 