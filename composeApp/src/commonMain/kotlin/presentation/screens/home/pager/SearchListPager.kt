package io.github.vrcmteam.vrcm.presentation.screens.home.pager

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.dp
import io.github.vrcmteam.vrcm.presentation.compoments.AdvancedOptionsPanel
import io.github.vrcmteam.vrcm.presentation.compoments.GenericSearchList
import io.github.vrcmteam.vrcm.presentation.compoments.renderGroupItems
import io.github.vrcmteam.vrcm.presentation.compoments.renderUserItems
import io.github.vrcmteam.vrcm.presentation.compoments.renderWorldItems
import io.github.vrcmteam.vrcm.presentation.compoments.safeImageUrl
import io.github.vrcmteam.vrcm.presentation.extensions.currentNavigator
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
    val loadStates by model.loadStates.collectAsState()
    val users by model.userSearchList.collectAsState()
    val worlds by model.worldSearchList.collectAsState()
    val groups by model.groupSearchList.collectAsState()
    val worldSearchOptions by model.worldSearchOptions.collectAsState()
    val groupHasMore by model.groupHasMore.collectAsState()
    val isLoadingGroups by model.isLoadingGroups.collectAsState()
    val groupLoadMoreFailed by model.groupLoadMoreFailed.collectAsState()
    val selectedTab = PublicSearchTab.fromSearchType(searchType)
    val selectedResultsCount = when (selectedTab) {
        PublicSearchTab.User -> users.size
        PublicSearchTab.World -> worlds.size
        PublicSearchTab.Group -> groups.size
    }
    val loadState = loadStates.getValue(searchType)
    val promptText = strings.publicSearchPrompt
    val noResultsText = strings.publicSearchNoResults
    val failedText = strings.publicSearchFailed
    val refreshFailedText = strings.publicSearchRefreshFailed
    val retryText = strings.retry
    val userListState = rememberLazyListState()
    val worldListState = rememberLazyListState()
    val groupListState = rememberLazyListState()
    val selectedListState = when (selectedTab) {
        PublicSearchTab.User -> userListState
        PublicSearchTab.World -> worldListState
        PublicSearchTab.Group -> groupListState
    }
    var showAdvancedOptions by remember { mutableStateOf(false) }

    LaunchedEffect(searchType, searchText) {
        model.loadSearchListIfNeeded()
    }

    GenericSearchList(
        key = "PublicSearchContent",
        searchText = searchText,
        updateSearchText = model::setSearchText,
        tabs = listOf(strings.users, strings.worlds, strings.groups),
        selectedTabIndex = selectedTab.tabIndex,
        onTabSelected = { model.setSearchType(PublicSearchTab.fromTabIndex(it).searchType) },
        advancedOptionsContent = {
            if (selectedTab == PublicSearchTab.World) {
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
        },
        onLoadMore = if (
            shouldEnableGroupLoadMore(searchType, groupHasMore, isLoadingGroups, groups.size)
        ) {
            { model.loadMoreGroups() }
        } else {
            null
        },
        totalItemsCount = groups.size.takeIf { selectedTab == PublicSearchTab.Group } ?: 0,
        lazyListState = selectedListState,
    ) {
        renderPublicSearchStatus(
            query = searchText,
            loadState = loadState,
            resultCount = selectedResultsCount,
            promptText = promptText,
            noResultsText = noResultsText,
            failedText = failedText,
            refreshFailedText = refreshFailedText,
            retryText = retryText,
            retry = { coroutineScope.launch { model.refreshSearchList() } },
        )
        if (searchText.isNotBlank()) {
            when (selectedTab) {
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
                        failed = shouldShowGroupLoadMoreRetry(searchType, groupLoadMoreFailed, groups.size),
                        retryText = retryText,
                        retry = { model.retryLoadMoreGroups() },
                    )
                }
            }
        }
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
                    Icon(Icons.Default.Refresh, contentDescription = null)
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
                    Icon(Icons.Default.Refresh, contentDescription = null)
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
