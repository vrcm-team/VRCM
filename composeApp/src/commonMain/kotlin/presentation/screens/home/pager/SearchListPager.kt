package io.github.vrcmteam.vrcm.presentation.screens.home.pager

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.presentation.compoments.AdvancedOptionsPanel
import io.github.vrcmteam.vrcm.presentation.compoments.SearchTextField
import io.github.vrcmteam.vrcm.presentation.compoments.animateScrollToTab
import io.github.vrcmteam.vrcm.presentation.compoments.renderGroupItems
import io.github.vrcmteam.vrcm.presentation.compoments.renderUserItems
import io.github.vrcmteam.vrcm.presentation.compoments.renderWorldItems
import io.github.vrcmteam.vrcm.presentation.compoments.safeImageUrl
import io.github.vrcmteam.vrcm.presentation.compoments.shouldLoadNextPage
import io.github.vrcmteam.vrcm.presentation.adaptive.AppWindowWidthClass
import io.github.vrcmteam.vrcm.presentation.adaptive.LocalAppWindowWidthClass
import io.github.vrcmteam.vrcm.presentation.extensions.animateScrollToFirst
import io.github.vrcmteam.vrcm.presentation.extensions.currentNavigator
import io.github.vrcmteam.vrcm.presentation.extensions.getInsetPadding
import io.github.vrcmteam.vrcm.presentation.screens.group.GroupProfileScreen
import io.github.vrcmteam.vrcm.presentation.screens.group.data.GroupProfileVo
import io.github.vrcmteam.vrcm.presentation.screens.home.compoments.WorldSearchOptionsUI
import io.github.vrcmteam.vrcm.presentation.screens.user.UserProfileScreen
import io.github.vrcmteam.vrcm.presentation.screens.user.data.UserProfileVo
import io.github.vrcmteam.vrcm.presentation.screens.world.WorldProfileScreen
import io.github.vrcmteam.vrcm.presentation.screens.world.data.WorldProfileVo
import io.github.vrcmteam.vrcm.presentation.settings.locale.strings
import io.github.vrcmteam.vrcm.presentation.supports.AppIcons
import io.github.vrcmteam.vrcm.presentation.supports.Pager
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.distinctUntilChanged
import org.koin.compose.viewmodel.koinViewModel

object SearchListPager : Pager {
    override val index: Int = 2

    override val title: String
        @Composable get() = strings.fiendListPagerSearch

    override val icon: Painter
        @Composable get() = rememberVectorPainter(AppIcons.PersonSearch)

    @Composable
    override fun Content() {
        PublicSearchContent()
    }
}

/** 可直接嵌入一级页面的公开玩家、世界与群组搜索内容。 */
@Composable
fun PublicSearchContent(
    model: SearchListPagerModel = koinViewModel(),
) {
    val coroutineScope = rememberCoroutineScope()
    val navigator = currentNavigator
    val searchType by model.searchType.collectAsState()
    val searchText by model.searchText.collectAsState()
    val queriesByType by model.queriesByType.collectAsState()
    val loadStates by model.loadStates.collectAsState()
    val users by model.userSearchList.collectAsState()
    val worlds by model.worldSearchList.collectAsState()
    val groups by model.groupSearchList.collectAsState()
    val worldSearchOptions by model.worldSearchOptions.collectAsState()
    val groupHasMore by model.groupHasMore.collectAsState()
    val isLoadingGroups by model.isLoadingGroups.collectAsState()
    val groupLoadMoreFailed by model.groupLoadMoreFailed.collectAsState()
    val selectedTab = PublicSearchTab.fromSearchType(searchType)
    val promptText = strings.publicSearchPrompt
    val noResultsText = strings.publicSearchNoResults
    val failedText = strings.publicSearchFailed
    val refreshFailedText = strings.publicSearchRefreshFailed
    val retryText = strings.retry
    val userListState = rememberLazyListState()
    val worldListState = rememberLazyListState()
    val groupListState = rememberLazyListState()
    val pagerState = rememberPagerState(
        initialPage = selectedTab.tabIndex,
        pageCount = { PublicSearchTab.entries.size },
    )
    var showAdvancedOptions by remember { mutableStateOf(false) }
    val bottomNavigationPadding = if (
        LocalAppWindowWidthClass.current == AppWindowWidthClass.Compact
    ) 80.dp else 0.dp

    LaunchedEffect(pagerState, model) {
        snapshotFlow { pagerState.currentPage }
            .distinctUntilChanged()
            .collect { page ->
                model.setSearchType(PublicSearchTab.fromTabIndex(page).searchType)
            }
    }
    LaunchedEffect(searchType, searchText) {
        model.loadSearchListIfNeeded()
    }
    LaunchedEffect(pagerState, userListState, worldListState, groupListState) {
        SharedFlowCentre.toPagerTop.collect {
            val listState = when (pagerState.currentPage) {
                PublicSearchTab.World.tabIndex -> worldListState
                PublicSearchTab.Group.tabIndex -> groupListState
                else -> userListState
            }
            runCatching { listState.animateScrollToFirst() }
        }
    }
    LaunchedEffect(
        groupListState,
        groups.size,
        groupHasMore,
        isLoadingGroups,
        groupLoadMoreFailed,
        searchType,
    ) {
        snapshotFlow {
            groupListState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
        }.distinctUntilChanged().collect { lastVisibleIndex ->
            if (
                searchType == PublicSearchTab.Group.searchType &&
                !groupLoadMoreFailed &&
                shouldEnableGroupLoadMore(
                    searchType = PublicSearchTab.Group.searchType,
                    hasMore = groupHasMore,
                    isLoading = isLoadingGroups,
                    itemCount = groups.size,
                ) &&
                shouldLoadNextPage(lastVisibleIndex, groups.size)
            ) {
                model.loadMoreGroups()
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        SearchTextField(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 12.dp, end = 16.dp),
            value = searchText,
            onValueChange = model::setSearchText,
        )
        PublicSearchTabRow(
            pagerState = pagerState,
            tabs = listOf(strings.users, strings.worlds, strings.groups),
        )
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f),
            key = { PublicSearchTab.fromTabIndex(it).name },
        ) { page ->
            val tab = PublicSearchTab.fromTabIndex(page)
            val query = queriesByType.getValue(tab.searchType)
            val loadState = loadStates.getValue(tab.searchType)
            val resultCount = when (tab) {
                PublicSearchTab.User -> users.size
                PublicSearchTab.World -> worlds.size
                PublicSearchTab.Group -> groups.size
            }
            val listState = when (tab) {
                PublicSearchTab.User -> userListState
                PublicSearchTab.World -> worldListState
                PublicSearchTab.Group -> groupListState
            }
            PublicSearchPage(
                query = query,
                loadState = loadState,
                resultCount = resultCount,
                lazyListState = listState,
                bottomNavigationPadding = bottomNavigationPadding,
                promptText = promptText,
                noResultsText = noResultsText,
                failedText = failedText,
                refreshFailedText = refreshFailedText,
                retryText = retryText,
                retry = { coroutineScope.launch { model.refreshSearchList(tab.searchType) } },
                advancedOptionsContent = if (tab == PublicSearchTab.World) {
                    {
                        AdvancedOptionsPanel(
                            title = strings.worldSearchAdvancedOptions,
                            expanded = showAdvancedOptions,
                            onExpandToggle = { showAdvancedOptions = !showAdvancedOptions },
                        ) {
                            WorldSearchOptionsUI(
                                options = worldSearchOptions,
                                onOptionsChanged = { options ->
                                    coroutineScope.launch { model.updateWorldSearchOptions(options) }
                                },
                            )
                        }
                    }
                } else {
                    null
                },
            ) {
                when (tab) {
                    PublicSearchTab.User -> renderUserItems(users) { user, suffix ->
                        coroutineScope.launch {
                            navigator push UserProfileScreen(
                                userProfileVO = UserProfileVo(user),
                                sharedSuffixKey = suffix,
                            )
                        }
                    }
                    PublicSearchTab.World -> renderWorldItems(worlds) { world, suffix ->
                        coroutineScope.launch {
                            navigator push WorldProfileScreen(
                                worldProfileVO = WorldProfileVo(world),
                                sharedSuffixKey = suffix,
                                sharedImageCacheKey = world.safeImageUrl(),
                            )
                        }
                    }
                    PublicSearchTab.Group -> {
                        renderGroupItems(groups) { group, suffix ->
                            coroutineScope.launch {
                                navigator push GroupProfileScreen(
                                    groupProfileVo = GroupProfileVo(group),
                                    sharedSuffixKey = suffix,
                                )
                            }
                        }
                        renderGroupPagingStatus(
                            isLoading = groups.isNotEmpty() && isLoadingGroups &&
                                loadState.phase != SearchLoadPhase.Loading,
                            failed = shouldShowGroupLoadMoreRetry(
                                searchType = tab.searchType,
                                loadMoreFailed = groupLoadMoreFailed,
                                itemCount = groups.size,
                            ),
                            retryText = retryText,
                            retry = { model.retryLoadMoreGroups() },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PublicSearchTabRow(
    pagerState: PagerState,
    tabs: List<String>,
) {
    val scope = rememberCoroutineScope()
    PrimaryTabRow(selectedTabIndex = pagerState.currentPage) {
        tabs.forEachIndexed { index, title ->
            Tab(
                selected = index == pagerState.currentPage,
                onClick = { scope.launch { pagerState.animateScrollToTab(index) } },
                text = {
                    Text(
                        text = title,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
            )
        }
    }
}

@Composable
private fun PublicSearchPage(
    query: String,
    loadState: PublicSearchLoadState,
    resultCount: Int,
    lazyListState: LazyListState,
    bottomNavigationPadding: Dp,
    promptText: String,
    noResultsText: String,
    failedText: String,
    refreshFailedText: String,
    retryText: String,
    retry: () -> Unit,
    advancedOptionsContent: (@Composable () -> Unit)?,
    itemContent: LazyListScope.() -> Unit,
) {
    val bottomPadding = getInsetPadding(12, WindowInsets::getBottom) + bottomNavigationPadding
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = lazyListState,
        contentPadding = PaddingValues(
            top = 8.dp,
            bottom = bottomPadding,
        ),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (advancedOptionsContent != null) {
            item(key = "public-search-advanced-options") {
                advancedOptionsContent()
            }
        }
        renderPublicSearchStatus(
            query = query,
            loadState = loadState,
            resultCount = resultCount,
            promptText = promptText,
            noResultsText = noResultsText,
            failedText = failedText,
            refreshFailedText = refreshFailedText,
            retryText = retryText,
            retry = retry,
        )
        if (query.isNotBlank()) itemContent()
    }
}

private fun LazyListScope.renderPublicSearchStatus(
    query: String,
    loadState: PublicSearchLoadState,
    resultCount: Int,
    promptText: String,
    noResultsText: String,
    failedText: String,
    refreshFailedText: String,
    retryText: String,
    retry: () -> Unit,
) {
    when {
        query.isBlank() -> searchMessageItem(promptText, retryText = retryText)
        loadState.phase == SearchLoadPhase.Loading && resultCount == 0 -> item("search-loading") {
            Box(Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        loadState.phase == SearchLoadPhase.Error && resultCount == 0 -> searchMessageItem(
            message = failedText,
            retryText = retryText,
            retry = retry,
        )
        loadState.phase == SearchLoadPhase.Success && resultCount == 0 ->
            searchMessageItem(noResultsText, retryText = retryText)
        loadState.phase == SearchLoadPhase.Loading -> item("search-refreshing") {
            LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp))
        }
        loadState.phase == SearchLoadPhase.Error -> item("search-refresh-failed") {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = refreshFailedText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                TextButton(onClick = retry) { Text(retryText) }
            }
        }
    }
}

private fun LazyListScope.searchMessageItem(
    message: String,
    retryText: String,
    retry: (() -> Unit)? = null,
) {
    item("search-message:$message") {
        Column(
            modifier = Modifier.fillMaxWidth().height(220.dp).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(AppIcons.Search, contentDescription = null, modifier = Modifier.size(48.dp))
            Spacer(Modifier.height(16.dp))
            Text(message, style = MaterialTheme.typography.bodyLarge)
            if (retry != null) {
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = retry) {
                    Icon(AppIcons.Update, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(retryText)
                }
            }
        }
    }
}

private fun LazyListScope.renderGroupPagingStatus(
    isLoading: Boolean,
    failed: Boolean,
    retryText: String,
    retry: () -> Unit,
) {
    if (!isLoading && !failed) return
    item("group-search-load-more") {
        Box(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (isLoading) {
                CircularProgressIndicator(Modifier.size(24.dp))
            } else {
                TextButton(onClick = retry) {
                    Icon(AppIcons.Update, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(retryText)
                }
            }
        }
    }
}

internal fun shouldEnableGroupLoadMore(
    searchType: Int,
    hasMore: Boolean,
    isLoading: Boolean,
    itemCount: Int,
): Boolean = searchType == PublicSearchTab.Group.searchType && hasMore && !isLoading && itemCount > 0

internal fun shouldShowGroupLoadMoreRetry(
    searchType: Int,
    loadMoreFailed: Boolean,
    itemCount: Int,
): Boolean = searchType == PublicSearchTab.Group.searchType && loadMoreFailed && itemCount > 0
