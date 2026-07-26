package io.github.vrcmteam.vrcm.presentation.screens.home.pager

import androidx.compose.runtime.*
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import cafe.adriel.voyager.koin.koinScreenModel
import io.github.vrcmteam.vrcm.presentation.compoments.AdvancedOptionsPanel
import io.github.vrcmteam.vrcm.presentation.compoments.SearchTabType
import io.github.vrcmteam.vrcm.presentation.compoments.StandardSearchList
import io.github.vrcmteam.vrcm.presentation.screens.home.compoments.WorldSearchOptionsUI
import io.github.vrcmteam.vrcm.presentation.settings.locale.strings
import io.github.vrcmteam.vrcm.presentation.supports.AppIcons
import io.github.vrcmteam.vrcm.presentation.supports.Pager
import kotlinx.coroutines.launch

object SearchListPager : Pager {
    override val index: Int
        get() = 2

    override val title: String
        @Composable
        get() = strings.fiendListPagerSearch

    override val icon: Painter
        @Composable
        get() = rememberVectorPainter(AppIcons.PersonSearch)

    @Composable
    override fun Content() {
        // 获取ViewModel
        val searchListPagerModel: SearchListPagerModel = koinScreenModel()
        val coroutineScope = rememberCoroutineScope()

        // 获取当前选中的标签索引
        val searchType by searchListPagerModel.searchType.collectAsState()
        // 将搜索类型转换为标签索引：群组搜索类型3对应标签索引2
        val selectedTabIndex = if (searchType == 3) 2 else searchType

        // 搜索文本
        val searchText by searchListPagerModel.searchText.collectAsState()

        // 高级搜索选项状态
        var showAdvancedOptions by remember { mutableStateOf(false) }

        val users by searchListPagerModel.userSearchList.collectAsState()
        val worlds by searchListPagerModel.worldSearchList.collectAsState()
        val groups by searchListPagerModel.groupSearchList.collectAsState()
        val groupHasMore by searchListPagerModel.groupHasMore.collectAsState()
        val isLoadingGroups by searchListPagerModel.isLoadingGroups.collectAsState()
        val groupLoadMoreFailed by searchListPagerModel.groupLoadMoreFailed.collectAsState()

        // 当搜索文本改变时执行搜索
        LaunchedEffect(searchText, searchType) {
            searchListPagerModel.refreshSearchList()
        }
        
        StandardSearchList(
            key = "GenericSearchPager",
            searchText = searchText,
            updateSearchText = { newText ->
                searchListPagerModel.setSearchText(newText)
            },
            selectedTabIndex = selectedTabIndex,
            onTabSelected = { index ->
                coroutineScope.launch {
                    // 当 includeGroups=true 时，标签页索引 2 对应群组搜索类型 3
                    val searchType = if (index == 2) 3 else index
                    searchListPagerModel.setSearchType(searchType)
                }
            },
            userList = users,
            worldList = worlds,
            groupList = groups,
            includeGroups = true,
            onLoadMore = if (
                shouldEnableGroupLoadMore(
                    searchType = searchType,
                    hasMore = groupHasMore,
                    isLoading = isLoadingGroups,
                    itemCount = groups.size,
                )
            ) {
                { searchListPagerModel.loadMoreGroups() }
            } else {
                null
            },
            totalItemsCount = groups.size.takeIf { searchType == 3 } ?: 0,
            isLoadingMore = searchType == 3 && groups.isNotEmpty() && isLoadingGroups,
            loadMoreFailed = shouldShowGroupLoadMoreRetry(
                searchType = searchType,
                loadMoreFailed = groupLoadMoreFailed,
                itemCount = groups.size,
            ),
            onRetryLoadMore = searchListPagerModel::retryLoadMoreGroups,
            advancedOptionsContent = { tabType ->
                // 仅在世界搜索标签下显示高级选项
                if (tabType == SearchTabType.WORLD) {
                    val worldSearchOptions by searchListPagerModel.worldSearchOptions.collectAsState()

                    AdvancedOptionsPanel(
                        title = strings.worldSearchAdvancedOptions,
                        expanded = showAdvancedOptions,
                        onExpandToggle = { showAdvancedOptions = !showAdvancedOptions }
                    ) {
                        // 世界搜索高级选项UI
                        WorldSearchOptionsUI(
                            options = worldSearchOptions,
                            onOptionsChanged = { newOptions ->
                                coroutineScope.launch {
                                    searchListPagerModel.updateWorldSearchOptions(newOptions)
                                }
                            }
                        )
                    }
                }
            }
        )
    }
}

internal fun shouldEnableGroupLoadMore(
    searchType: Int,
    hasMore: Boolean,
    isLoading: Boolean,
    itemCount: Int,
): Boolean = searchType == 3 && hasMore && !isLoading && itemCount > 0

internal fun shouldShowGroupLoadMoreRetry(
    searchType: Int,
    loadMoreFailed: Boolean,
    itemCount: Int,
): Boolean = searchType == 3 && loadMoreFailed && itemCount > 0

