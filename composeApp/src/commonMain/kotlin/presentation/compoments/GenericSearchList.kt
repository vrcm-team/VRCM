package io.github.vrcmteam.vrcm.presentation.compoments

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.presentation.designsystem.*
import io.github.vrcmteam.vrcm.presentation.extensions.animateScrollToFirst
import io.github.vrcmteam.vrcm.presentation.extensions.getInsetPadding
import io.github.vrcmteam.vrcm.presentation.extensions.simpleClickable
import io.github.vrcmteam.vrcm.presentation.settings.locale.strings
import io.github.vrcmteam.vrcm.presentation.supports.AppIcons
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * 通用搜索列表组件
 * 提供搜索框、选项卡和可切换的内容
 */
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
    onLoadMore: (() -> Unit)? = null,
    totalItemsCount: Int = 0,
    lazyListState: LazyListState = rememberLazyListState(),
    topContentPadding: Dp? = null,
    bottomNavigationPadding: Dp = 80.dp,
    itemContent:  LazyListScope.(Int) -> Unit
) {
    // 监听返回顶部事件
    LaunchedEffect(key, lazyListState) {
        SharedFlowCentre.toPagerTop.collect {
            runCatching {
                lazyListState.animateScrollToFirst()
            }
        }
    }

    if (onLoadMore != null) {
        LaunchedEffect(lazyListState, totalItemsCount, onLoadMore) {
            snapshotFlow {
                val layoutInfo = lazyListState.layoutInfo
                val lastVisibleIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
                lastVisibleIndex to totalItemsCount
            }.distinctUntilChanged().collect { (lastVisibleIndex, totalItemsCount) ->
                if (shouldLoadNextSearchPage(lastVisibleIndex, totalItemsCount)) {
                    onLoadMore()
                }
            }
        }
    }

    val topPadding = topContentPadding ?: (getInsetPadding(WindowInsets::getTop) + 80.dp)
    val bottomPadding = getInsetPadding(12, WindowInsets::getBottom) + bottomNavigationPadding

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
                Column(
                    modifier = Modifier
                        .fillMaxWidth(),
                ){
                    // 搜索框
                    SearchTextField(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        value = searchText,
                        onValueChange = updateSearchText
                    )

                    // 自定义顶部内容
                    headerContent()

                    // 标签栏
                    AppTabRow(
                        selectedTabIndex = selectedTabIndex,
                        modifier = Modifier
                            .fillMaxWidth(),
                    ) {
                        tabs.forEachIndexed { index, title ->
                            AppTab(
                                selected = index == selectedTabIndex,
                                onClick = { onTabSelected(index) },
                                text = {
                                    AppText(
                                        text = title,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        style = AppTheme.type.subheadline.copy(
                                            fontWeight = if (index == selectedTabIndex) FontWeight.Bold
                                                        else FontWeight.Normal
                                        )
                                    )
                                }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
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

/** 列表行前导内容（头像 / 缩略图 / 图标）的边长；分隔线按它内缩，所以各处的前导内容都用这个尺寸。 */
val SearchResultLeadingSize = 48.dp

/**
 * 列表里的一行，照 iOS「信息」的列表：通栏、没有卡片底，按压时整行压暗；
 * 行与行之间是一条从标题起、一直到屏幕边的发丝线。放进分组卡片等自带分隔线的容器时关掉 [showDivider]。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun <T> SearchResultItem(
    item: T,
    onClick: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onLongClick: ((T) -> Unit)? = null,
    showDivider: Boolean = true,
    leadingContent: @Composable () -> Unit,
    headlineContent: @Composable () -> Unit,
    supportingContent: @Composable (() -> Unit)? = null,
    trailingContent: @Composable (() -> Unit)? = null
) {
    val interactionModifier = if (onLongClick == null) {
        Modifier.clickable(enabled = enabled) { onClick(item) }
    } else {
        Modifier.combinedClickable(
            enabled = enabled,
            onClick = { onClick(item) },
            onLongClick = { onLongClick(item) },
        )
    }
    Column(modifier.fillMaxWidth()) {
        AppListItem(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 68.dp)
                .then(interactionModifier),
            leadingContent = leadingContent,
            headlineContent = headlineContent,
            supportingContent = supportingContent ?: {},
            trailingContent = trailingContent ?: {}
        )
        if (showDivider) {
            AppDivider(Modifier.padding(start = appRowDividerInset(SearchResultLeadingSize)))
        }
    }
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
                .simpleClickable(onClick = onExpandToggle)
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AppIcon(
                    imageVector = AppIcons.Settings,
                    contentDescription = title
                )
                AppText(
                    text = title,
                    style = AppTheme.type.subheadline
                )
            }
            AppIcon(
                imageVector = if (expanded) AppIcons.ExpandLess else AppIcons.ExpandMore,
                contentDescription = if (expanded) {
                    strings.notificationCollapse
                } else {
                    strings.notificationExpand
                }
            )
        }

        // 展开时显示内容
        AnimatedVisibility(visible = expanded) {
            content()
        }
    }
}
